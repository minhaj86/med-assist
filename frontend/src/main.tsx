import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { App } from './App'
import { setAccessTokenProvider } from './api/client'
import { AuthGate } from './auth/AuthGate'
import { oidcConfig } from './auth/oidc'
import { currentAccessToken } from './auth/token'
import { createQueryClient } from './queryClient'
import { theme } from './theme'

const queryClient = createQueryClient()
setAccessTokenProvider(currentAccessToken)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AuthProvider {...oidcConfig}>
        <QueryClientProvider client={queryClient}>
          <AuthGate>
            <App />
          </AuthGate>
        </QueryClientProvider>
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>,
)
