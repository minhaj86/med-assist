import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { DIFFERENTIAL_ID, VERSION_ID, versionFixture } from '../../../test/fixtures'
import { renderInWorkstation, workstationState } from '../../../test/render'
import { server } from '../../../test/server'
import { DifferentialCard } from './DifferentialCard'

const [aiDifferential, knowledgeLinked] = versionFixture.differentials

describe('DifferentialCard', () => {
  it('shows every explainability section with hedged, non-definitive wording', () => {
    renderInWorkstation(<DifferentialCard differential={aiDifferential} investigations={[]} />, workstationState({ version: versionFixture }))
    const card = screen.getByRole('article', { name: /Possible differential 1: Condition Alpha/ })
    expect(within(card).getByText('Possible differential')).toBeInTheDocument()
    expect(within(card).getByText('AI-reasoned')).toBeInTheDocument()
    expect(within(card).getByText('Confidence: MODERATE')).toBeInTheDocument()
    for (const section of ['Supporting', 'Contradicting', 'Missing information', 'Knowledge sources']) {
      expect(within(card).getByText(section)).toBeInTheDocument()
    }
    expect(within(card).getByText('None identified')).toBeInTheDocument()
    expect(within(card).getByText(/ECG not yet recorded/)).toBeInTheDocument()
    expect(within(card).getByText('unsupported')).toBeInTheDocument()
    expect(within(card).getByText(/Synthetic chest pain note/)).toBeInTheDocument()
    expect(card.textContent).not.toMatch(/definite|diagnosed with|%/i)
  })

  it('records clinician feedback through the API', async () => {
    let body: unknown
    server.use(http.post(`/api/assessments/${VERSION_ID}/feedback`, async ({ request }) => {
      body = await request.json()
      return HttpResponse.json({}, { status: 201 })
    }))
    const { user } = renderInWorkstation(<DifferentialCard differential={aiDifferential} investigations={[]} />, workstationState({ version: versionFixture }))
    const feedback = screen.getByRole('group', { name: 'Feedback on Condition Alpha (synthetic)' })
    await user.click(within(feedback).getByRole('button', { name: 'Accept' }))
    await vi.waitFor(() => expect(body).toEqual({ suggestionType: 'DIFFERENTIAL', suggestionId: DIFFERENTIAL_ID, action: 'ACCEPTED' }))
  })

  it('marks knowledge-linked candidates as not AI-assessed and offers no feedback controls', () => {
    renderInWorkstation(<DifferentialCard differential={knowledgeLinked} investigations={[]} />, workstationState({ version: versionFixture }))
    expect(screen.getByText('Knowledge-linked, not AI-assessed')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: /Feedback on/ })).not.toBeInTheDocument()
  })

  it('disables feedback when the record is read-only', () => {
    renderInWorkstation(<DifferentialCard differential={aiDifferential} investigations={[]} />, workstationState({ version: versionFixture, readOnly: true }))
    expect(within(screen.getByRole('group', { name: /Feedback on Condition Alpha/ })).getByRole('button', { name: 'Accept' })).toBeDisabled()
  })
})
