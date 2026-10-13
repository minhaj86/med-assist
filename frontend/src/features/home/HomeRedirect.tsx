import { Alert, Box, Button, CircularProgress } from '@mui/material'
import { Link as RouterLink, Navigate } from 'react-router'
import { useCurrentUser } from '../../auth/useCurrentUser'
import { ProblemAlert } from '../../components/ProblemAlert'

/** Sends each role to its primary work area. */
export function HomeRedirect() {
  const { user, isLoading, error, hasRole } = useCurrentUser()
  if (isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress /></Box>
  if (error || !user) return <Box sx={{ p: 2 }}><ProblemAlert error={error} title="MedAssist API unavailable" /></Box>
  if (hasRole('CLINICIAN')) return <Navigate to="/dashboard" replace />
  if (hasRole('ADMIN')) return <Navigate to="/admin" replace />
  if (hasRole('KNOWLEDGE_MANAGER')) return <Navigate to="/admin/knowledge" replace />
  return <Box sx={{ p: 3 }}><Alert severity="warning">Your account has no MedAssist role. Contact an administrator.</Alert></Box>
}

export function NotFound() {
  return (
    <Box sx={{ p: 3 }}>
      <Alert severity="info" action={<Button component={RouterLink} to="/">Home</Button>}>Page not found.</Alert>
    </Box>
  )
}
