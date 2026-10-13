import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { knowledgeDocs, knowledgeStatus } from '../../../test/fixtures'
import { renderRoute } from '../../../test/render'
import { server } from '../../../test/server'
import { KnowledgeBasePage } from './KnowledgeBasePage'

function useKnowledgeApi() {
  const state = { docs: [...knowledgeDocs], deleted: [] as string[], patches: [] as unknown[] }
  server.use(
    http.get('/api/admin/knowledge/documents', () => HttpResponse.json(state.docs)),
    http.get('/api/admin/knowledge/status', () => HttpResponse.json(knowledgeStatus)),
    http.delete('/api/admin/knowledge/documents/:id', ({ params }) => {
      state.deleted.push(params.id as string)
      state.docs = state.docs.filter((d) => d.id !== params.id)
      return new HttpResponse(null, { status: 204 })
    }),
    http.patch('/api/admin/knowledge/documents/:id', async ({ params, request }) => {
      const body = (await request.json()) as { enabled?: boolean }
      state.patches.push({ id: params.id, ...body })
      state.docs = state.docs.map((d) => (d.id === params.id && body.enabled !== undefined ? { ...d, enabled: body.enabled } : d))
      return HttpResponse.json(state.docs.find((d) => d.id === params.id))
    }),
  )
  return state
}

const renderPage = () => renderRoute(<KnowledgeBasePage />, { path: '/admin/knowledge', route: '/admin/knowledge' })

describe('KnowledgeBasePage', () => {
  it('lists documents with the required columns and status', async () => {
    useKnowledgeApi()
    renderPage()
    expect(await screen.findByText('Synthetic chest pain note')).toBeInTheDocument()
    const headers = screen.getAllByRole('columnheader').map((h) => h.textContent)
    expect(headers).toEqual(['Document', 'Source', 'Version', 'Published', 'Chunks', 'Status', 'Last indexed', 'Actions'])
    const failedRow = screen.getByText('Draft local protocol (synthetic)').closest('tr')!
    expect(within(failedRow).getByText('FAILED')).toBeInTheDocument()
    expect(within(failedRow).getByText('not retrievable')).toBeInTheDocument()
    expect(screen.getByText('1 of 2 documents retrievable')).toBeInTheDocument()
  })

  it('disables and deletes documents through the admin API', async () => {
    const api = useKnowledgeApi()
    const { user } = renderPage()
    const row = (await screen.findByText('Synthetic chest pain note')).closest('tr')!
    await user.click(within(row).getByRole('button', { name: 'Disable' }))
    await vi.waitFor(() => expect(api.patches).toEqual([{ id: 'doc-1', enabled: false }]))
    expect(await within(row).findByText('DISABLED')).toBeInTheDocument()

    await user.click(within(row).getByRole('button', { name: 'Delete Synthetic chest pain note' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete document?' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))
    await vi.waitFor(() => expect(api.deleted).toEqual(['doc-1']))
    await vi.waitFor(() => expect(screen.queryByText('Synthetic chest pain note')).not.toBeInTheDocument())
  })

  it('requires file content before a document can be uploaded', async () => {
    useKnowledgeApi()
    const { user } = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Add document' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add knowledge document' })
    expect(within(dialog).getByText(/Never upload patient-identifiable information/)).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Add document' })).toBeDisabled()
    await user.click(within(dialog).getByRole('tab', { name: 'Enter text manually' }))
    await user.type(within(dialog).getByLabelText('Document text'), '# Heading\nSynthetic text')
    expect(within(dialog).getByRole('button', { name: 'Add document' })).toBeEnabled()
  })
})
