import { Alert, Box } from '@mui/material'
import type { ReactNode } from 'react'
import type { Role } from '../api/types'
import { useCurrentUser } from './useCurrentUser'

export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { hasAnyRole, isLoading } = useCurrentUser()
  if (isLoading) return null
  if (!hasAnyRole(...roles)) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="warning">You do not have access to this area (requires {roles.join(' or ')}).</Alert>
      </Box>
    )
  }
  return <>{children}</>
}
