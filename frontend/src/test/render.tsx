import { ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement, ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import type { AssessmentVersion, EncounterChart } from '../api/types'
import { WorkstationContext, type WorkstationState } from '../features/encounter/WorkstationContext'
import { theme } from '../theme'
import { chartFixture } from './fixtures'

export function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } } })
}

export function Providers({ children, client }: { children: ReactNode; client?: QueryClient }) {
  return (
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={client ?? createTestQueryClient()}>{children}</QueryClientProvider>
    </ThemeProvider>
  )
}

/** Renders a routed element (or a route table) inside a memory router with all providers. */
export function renderRoute(element: ReactElement | RouteObject[], { path = '/', route = '/' }: { path?: string; route?: string } = {}) {
  const routes: RouteObject[] = Array.isArray(element)
    ? element
    : [{ path, element }, { path: '*', element: <div data-testid="other-route" /> }]
  const router = createMemoryRouter(routes, { initialEntries: [route] })
  const user = userEvent.setup()
  const result = render(<Providers><RouterProvider router={router} /></Providers>)
  return { ...result, user, router }
}

export function workstationState(overrides: Partial<WorkstationState> & { chart?: EncounterChart; version?: AssessmentVersion } = {}): WorkstationState {
  const noop = () => undefined
  return {
    encounterId: chartFixture.encounter.id,
    chart: chartFixture,
    readOnly: false,
    version: undefined,
    selectedDifferentialId: undefined,
    selectDifferential: noop,
    highlightedEvidence: undefined,
    showEvidence: noop,
    rightTab: 'explain',
    setRightTab: noop,
    proposal: undefined,
    reviewProposal: noop,
    ...overrides,
  }
}

/** Renders a component that needs the encounter workstation context. */
export function renderInWorkstation(ui: ReactElement, state: WorkstationState = workstationState()) {
  return renderRoute(<WorkstationContext.Provider value={state}>{ui}</WorkstationContext.Provider>)
}
