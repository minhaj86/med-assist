import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AssessmentVersion } from '../../api/types'
import {
  chartFixture, completedRun, DIFFERENTIAL_ID, ENCOUNTER_ID, problem, RUN_ID, runningRun, safetyCheckFixture, VERSION_ID, versionFixture,
  versionSummary,
} from '../../test/fixtures'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'
import { EncounterWorkstation } from './EncounterWorkstation'

const base = `/api/encounters/${ENCOUNTER_ID}`

function useEncounterApi(options: { version?: AssessmentVersion } = {}) {
  const state = { assessed: false, runPolls: 0, feedback: [] as unknown[], assessmentRequests: [] as unknown[] }
  server.use(
    http.get(`${base}/chart`, () => HttpResponse.json(chartFixture)),
    http.get(`${base}/safety-alerts`, () => HttpResponse.json(safetyCheckFixture)),
    http.get(`${base}/assessments`, () => HttpResponse.json(state.assessed ? [versionSummary] : [])),
    http.get(`${base}/assessment-runs/latest`, () => HttpResponse.json(problem(404, 'Not found', 'No assessment run'), { status: 404 })),
    http.get(`${base}/final-assessment`, () => HttpResponse.json(problem(404, 'Not found', 'No final assessment'), { status: 404 })),
    http.get(`${base}/investigations`, () => HttpResponse.json([])),
    http.get(`${base}/assistant/conversation`, () => HttpResponse.json([])),
    http.get('/api/terminology/concepts', () => HttpResponse.json([])),
    http.post(`${base}/assessment`, async ({ request }) => {
      state.assessmentRequests.push(await request.json())
      return HttpResponse.json({ ...runningRun, status: 'QUEUED', stages: [runningRun.stages[0]] }, { status: 202 })
    }),
    http.get(`/api/assessment-runs/${RUN_ID}`, () => {
      state.runPolls += 1
      if (state.runPolls < 2) return HttpResponse.json(runningRun)
      state.assessed = true
      return HttpResponse.json(completedRun)
    }),
    http.get(`/api/assessments/${VERSION_ID}`, () => HttpResponse.json(options.version ?? versionFixture)),
    http.post(`/api/assessments/${VERSION_ID}/feedback`, async ({ request }) => {
      state.feedback.push(await request.json())
      return HttpResponse.json({}, { status: 201 })
    }),
    http.post(`${base}/assistant/questions`, () =>
      HttpResponse.json(problem(503, 'Service unavailable', 'The AI assistant is temporarily unavailable. All other MedAssist functions continue to work.'), { status: 503 })),
  )
  return state
}

const renderWorkstation = () => renderRoute(<EncounterWorkstation />, { path: '/encounters/:encounterId', route: `/encounters/${ENCOUNTER_ID}` })

describe('EncounterWorkstation', () => {
  beforeEach(() => { vi.useRealTimers() })

  it('shows deterministic safety alerts before any AI assessment exists', async () => {
    useEncounterApi()
    renderWorkstation()
    expect(await screen.findByRole('heading', { name: 'Synthetic, Testa' })).toBeInTheDocument()
    expect(screen.getByText('SYNTHETIC TEST DATA')).toBeInTheDocument()
    const alert = await screen.findByRole('alert', { name: /Deterministic urgent alert: Very high heart rate/ })
    expect(within(alert).getByText('RULE')).toBeInTheDocument()
    expect(within(alert).getByText('DEMONSTRATION RULE')).toBeInTheDocument()
    expect(await screen.findByText(/No assessment yet/)).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Clinical information' })).toHaveTextContent('Chest pain')
  })

  it('runs an assessment, shows real progress stages, then the validated, explainable result', async () => {
    const api = useEncounterApi()
    const { user } = renderWorkstation()
    await user.click(await screen.findByRole('button', { name: /Run assessment/ }))
    expect(api.assessmentRequests).toEqual([{ includeAi: true }])
    expect(await screen.findByText('Running safety rules')).toBeInTheDocument()
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()

    expect(await screen.findByText('Assessment V1', {}, { timeout: 5000 })).toBeInTheDocument()
    const redFlag = await screen.findByRole('alert', { name: /AI-generated urgent alert: Possible time-critical cause/ })
    expect(within(redFlag).getByText('AI-generated consideration — verify against clinical findings')).toBeInTheDocument()
    const card = screen.getByRole('article', { name: /Possible differential 1: Condition Alpha/ })
    expect(within(card).getByText('Confidence: MODERATE')).toBeInTheDocument()
    expect(screen.getByText('AI-identified uncertainty')).toBeInTheDocument()
    expect(screen.getByText(/Insufficient information to reliably assess Condition Beta/)).toBeInTheDocument()

    await user.click(within(screen.getByRole('group', { name: 'Feedback on Condition Alpha (synthetic)' })).getByRole('button', { name: 'Accept' }))
    await vi.waitFor(() => expect(api.feedback).toEqual([{ suggestionType: 'DIFFERENTIAL', suggestionId: DIFFERENTIAL_ID, action: 'ACCEPTED' }]))

    await user.click(within(card).getByRole('button', { name: 'Explain' }))
    const evidence = screen.getByRole('region', { name: 'Evidence and assistant' })
    for (const q of ['Why considered?', 'What supports it?', 'What argues against it?', 'What information is missing?',
      'What would help distinguish it?', 'What knowledge sources support the reasoning?']) {
      expect(within(evidence).getByText(q)).toBeInTheDocument()
    }
    expect(document.body.textContent).not.toMatch(/definitely|confirmed diagnosis/i)
  })

  it('keeps the workstation usable and non-alarming when the AI assistant is unavailable', async () => {
    useEncounterApi()
    const { user } = renderWorkstation()
    await user.click(await screen.findByRole('tab', { name: 'AI assistant' }))
    await user.type(screen.getByRole('textbox', { name: 'Question for the AI assistant' }), 'What information would most affect the differential?')
    await user.click(screen.getByRole('button', { name: 'Send question' }))
    expect(await screen.findByText(/The AI assistant is temporarily unavailable\. Safety rules, data entry/)).toBeInTheDocument()
    expect(screen.getByRole('alert', { name: /Deterministic urgent alert/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Run assessment/ })).toBeEnabled()
  })

  it('makes a completed encounter read-only', async () => {
    useEncounterApi()
    server.use(http.get(`${base}/chart`, () => HttpResponse.json({ ...chartFixture, encounter: { ...chartFixture.encounter, status: 'COMPLETED' } })))
    renderWorkstation()
    expect(await screen.findByText(/the record is read-only/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Run assessment/ })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Complete encounter' })).not.toBeInTheDocument()
  })
})
