import { Alert, AlertTitle } from '@mui/material'
import { ApiError } from '../api/client'

/** Renders an API error (RFC 7807) without technical internals; shows the correlation id for support. */
export function ProblemAlert({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null
  if (error instanceof ApiError) {
    const p = error.problem
    return (
      <Alert severity={error.status === 503 ? 'info' : 'error'} sx={{ my: 1 }}>
        <AlertTitle>{title ?? p.title ?? 'Request failed'}</AlertTitle>
        {p.detail}
        {p.errors && p.errors.length > 0 && (
          <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
            {p.errors.map((e) => <li key={e.field + e.message}>{e.field}: {e.message}</li>)}
          </ul>
        )}
        {p.correlationId && <div style={{ fontSize: '0.7rem', opacity: 0.7, marginTop: 4 }}>Reference: {p.correlationId}</div>}
      </Alert>
    )
  }
  return <Alert severity="error" sx={{ my: 1 }}>{title ?? 'Something went wrong'}: {(error as Error).message}</Alert>
}
