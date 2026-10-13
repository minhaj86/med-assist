import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { chartFixture } from '../../test/fixtures'
import { renderRoute } from '../../test/render'
import { server } from '../../test/server'
import { NewPatientDialog } from './PatientForm'

describe('NewPatientDialog', () => {
  it('validates required fields before calling the API', async () => {
    const onCreated = vi.fn()
    const { user } = renderRoute(<NewPatientDialog open onClose={() => undefined} onCreated={onCreated} />)
    await user.click(screen.getByRole('button', { name: 'Register' }))
    expect(await screen.findAllByText('Required')).not.toHaveLength(0)
    expect(screen.getByText('3-64 letters, digits or hyphens')).toBeInTheDocument()
    expect(onCreated).not.toHaveBeenCalled()
  })

  it('creates a patient with the entered synthetic data', async () => {
    let body: Record<string, unknown> | undefined
    server.use(http.post('/api/patients', async ({ request }) => {
      body = (await request.json()) as Record<string, unknown>
      return HttpResponse.json(chartFixture.patient, { status: 201 })
    }))
    const onCreated = vi.fn()
    const { user } = renderRoute(<NewPatientDialog open onClose={() => undefined} onCreated={onCreated} />)
    await user.type(screen.getByLabelText(/External patient ID/i), 'SYN-TEST-02')
    await user.type(screen.getByLabelText(/First name/i), 'Demo')
    await user.type(screen.getByLabelText(/Last name/i), 'Person')
    await user.type(screen.getByLabelText(/Date of birth/i), '1980-05-17')
    await user.click(screen.getByRole('button', { name: 'Register' }))
    await vi.waitFor(() => expect(onCreated).toHaveBeenCalledWith(chartFixture.patient))
    expect(body).toMatchObject({ externalPatientId: 'SYN-TEST-02', firstName: 'Demo', lastName: 'Person', dateOfBirth: '1980-05-17' })
  })
})
