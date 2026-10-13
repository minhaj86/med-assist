import { Alert, Box, Button, CircularProgress, Stack, Typography } from '@mui/material'
import { useEffect, type ReactNode } from 'react'
import { hasAuthParams, useAuth } from 'react-oidc-context'

export const UNAUTHORIZED_EVENT = 'medassist:unauthorized'

/** Ensures an authenticated OIDC session (Authorization Code + PKCE) before rendering the application. */
export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth()

  useEffect(() => {
    if (!hasAuthParams() && !auth.isAuthenticated && !auth.activeNavigator && !auth.isLoading && !auth.error) {
      void auth.signinRedirect({ state: { returnTo: window.location.pathname + window.location.search } })
    }
  }, [auth])

  useEffect(() => {
    const handler = () => void auth.signinRedirect({ state: { returnTo: window.location.pathname + window.location.search } })
    window.addEventListener(UNAUTHORIZED_EVENT, handler)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler)
  }, [auth])

  if (auth.error) {
    return (
      <Box sx={{ p: 4, maxWidth: 560, mx: 'auto' }}>
        <Alert severity="error" action={<Button onClick={() => void auth.signinRedirect()}>Retry</Button>}>
          Sign-in failed: {auth.error.message}. Check that the identity provider is reachable.
        </Alert>
      </Box>
    )
  }
  if (auth.isLoading || !auth.isAuthenticated) {
    return (
      <Stack spacing={2} sx={{ alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
        <CircularProgress size={28} />
        <Typography variant="body2" color="text.secondary">Signing in to MedAssist…</Typography>
      </Stack>
    )
  }
  return <>{children}</>
}
