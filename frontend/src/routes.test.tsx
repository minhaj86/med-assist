import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import type { Me } from './api/types'
import { renderRoute } from './test/render'
import { server } from './test/server'
import { routes } from './routes'

vi.mock('react-oidc-context', () => ({ useAuth: () => ({ signoutRedirect: vi.fn() }), hasAuthParams: () => false }))

const me = (roles: Me['roles']): Me => ({ id: 'u1', username: 'demo.user', displayName: 'Demo User', roles })

function stubAdminApis() {
  server.use(
    http.get('/api/admin/system/health', () => HttpResponse.json({ overall: 'UP', applicationVersion: 'test', uptimeSeconds: 1, javaVersion: '21', activeProfiles: ['test'], checks: [] })),
    http.get('/api/admin/ai/status', () => HttpResponse.json({ provider: 'disabled', enabled: false, status: 'DISABLED', temperature: 0, responseFormat: 'JSON_SCHEMA', maxAttempts: 2, activePrompts: [], embeddingProvider: 'local-hashing', embeddingModel: 'local-hashing-v1', embeddingDimensions: 384, terminologySources: 'local', apiKeyConfigured: false })),
    http.get('/api/admin/knowledge/status', () => HttpResponse.json({ embeddingProvider: 'local-hashing', embeddingModel: 'local-hashing-v1', embeddingDimensions: 384, chunking: { maxChars: 1, overlapChars: 0, minChars: 0 }, retrieval: { topK: 1, minSimilarity: 0.1, maxChunksPerDocument: 1 }, documents: 0, retrievableDocuments: 0, failedDocuments: 0 })),
    http.get('/api/admin/rules', () => HttpResponse.json([])),
    http.get('/api/admin/knowledge/documents', () => HttpResponse.json([])),
  )
}

describe('role-based routing', () => {
  it('sends administrators to the admin area and hides clinical navigation', async () => {
    server.use(http.get('/api/me', () => HttpResponse.json(me(['ADMIN']))))
    stubAdminApis()
    renderRoute(routes, { route: '/' })
    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Patients' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Audit' })).toBeInTheDocument()
  })

  it('sends knowledge managers to the knowledge base', async () => {
    server.use(http.get('/api/me', () => HttpResponse.json(me(['KNOWLEDGE_MANAGER']))))
    stubAdminApis()
    renderRoute(routes, { route: '/' })
    expect(await screen.findByRole('heading', { name: 'Knowledge base' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Audit' })).not.toBeInTheDocument()
  })

  it('blocks administrators from patient data screens', async () => {
    server.use(http.get('/api/me', () => HttpResponse.json(me(['ADMIN']))))
    stubAdminApis()
    renderRoute(routes, { route: '/patients' })
    expect(await screen.findByText(/You do not have access to this area \(requires CLINICIAN\)/)).toBeInTheDocument()
  })

  it('shows the persistent safety disclaimer on every screen', async () => {
    server.use(http.get('/api/me', () => HttpResponse.json(me(['ADMIN']))))
    stubAdminApis()
    renderRoute(routes, { route: '/admin' })
    expect(await screen.findByRole('note', { name: 'Clinical safety disclaimer' })).toBeInTheDocument()
  })
})
