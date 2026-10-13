import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { UNAUTHORIZED_EVENT } from './auth/AuthGate'

const onError = (error: unknown) => {
  if (error instanceof ApiError && error.status === 401) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}

export function createQueryClient() {
  return new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: false,
        // Client errors (4xx) are never retried; transient failures are retried twice.
        retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  })
}
