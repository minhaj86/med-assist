import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { ENCOUNTER_ID } from '../../../test/fixtures'
import { renderRoute } from '../../../test/render'
import { server } from '../../../test/server'
import { VitalsForm } from './forms'

describe('VitalsForm', () => {
  it('requires at least one measurement', async () => {
    const { user } = renderRoute(<VitalsForm encounterId={ENCOUNTER_ID} onDone={() => undefined} />)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Enter at least one measurement.')).toBeInTheDocument()
  })

  it('posts only the entered vital signs with clinician provenance', async () => {
    let body: { measurements: unknown[]; source: string; measuredAt: string } | undefined
    server.use(http.post(`/api/encounters/${ENCOUNTER_ID}/observations`, async ({ request }) => {
      body = (await request.json()) as typeof body
      return HttpResponse.json([], { status: 201 })
    }))
    const onDone = vi.fn()
    const { user } = renderRoute(<VitalsForm encounterId={ENCOUNTER_ID} onDone={onDone} />)
    await user.type(screen.getByLabelText('Heart rate (/min)'), '128')
    await user.type(screen.getByLabelText('SpO₂ (%)'), '91')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await vi.waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(body?.source).toBe('CLINICIAN')
    expect(body?.measurements).toEqual([{ type: 'HEART_RATE', value: 128 }, { type: 'OXYGEN_SATURATION', value: 91 }])
    expect(new Date(body!.measuredAt).toString()).not.toBe('Invalid Date')
  })
})
