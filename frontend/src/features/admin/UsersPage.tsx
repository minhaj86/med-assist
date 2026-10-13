import { Alert, Box, Button, Chip, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material'
import { useState } from 'react'
import { useSetUserAccess, useUsers } from '../../api/hooks'
import type { UserAccount } from '../../api/types'
import { useCurrentUser } from '../../auth/useCurrentUser'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDateTime } from '../../components/format'
import { config } from '../../config'

export function UsersPage() {
  const users = useUsers()
  const setAccess = useSetUserAccess()
  const { user: me } = useCurrentUser()
  const [target, setTarget] = useState<UserAccount | null>(null)
  return (
    <Box>
      <PageHeader title="Users" subtitle="Accounts seen by MedAssist. Identities, credentials and role assignments are managed in the identity provider."
                  actions={config.keycloakAdminUrl
                    ? <Button variant="outlined" href={config.keycloakAdminUrl} target="_blank" rel="noreferrer noopener">Open identity provider</Button>
                    : undefined} />
      <Box sx={{ p: 2 }}>
        <Alert severity="info" sx={{ mb: 1.5 }}>
          Disabling an account here blocks access to MedAssist APIs immediately, independent of the identity provider session.
        </Alert>
        <ProblemAlert error={users.error ?? setAccess.error} />
        <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Table>
            <TableHead><TableRow><TableCell>Username</TableCell><TableCell>Name</TableCell><TableCell>Email</TableCell><TableCell>Roles</TableCell>
              <TableCell>First seen</TableCell><TableCell>Last seen</TableCell><TableCell>Access</TableCell></TableRow></TableHead>
            <TableBody>
              {(users.data ?? []).map((u) => (
                <TableRow key={u.id}>
                  <TableCell sx={{ fontWeight: 600 }}>{u.username}</TableCell>
                  <TableCell>{u.displayName ?? '—'}</TableCell>
                  <TableCell>{u.email ?? '—'}</TableCell>
                  <TableCell>{u.roles.map((r) => <Chip key={r} label={r} sx={{ mr: 0.5, fontSize: '0.62rem' }} />)}</TableCell>
                  <TableCell>{fmtDateTime(u.firstSeenAt)}</TableCell>
                  <TableCell>{fmtDateTime(u.lastSeenAt)}</TableCell>
                  <TableCell>
                    <Switch checked={u.enabled} disabled={u.id === me?.id} onChange={() => setTarget(u)}
                            slotProps={{ input: { 'aria-label': `${u.enabled ? 'Disable' : 'Enable'} ${u.username}` } }} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
      <ConfirmDialog open={!!target} title={`${target?.enabled ? 'Disable' : 'Enable'} ${target?.username ?? ''}?`} pending={setAccess.isPending}
                     destructive={!!target?.enabled} confirmLabel={target?.enabled ? 'Disable' : 'Enable'} onCancel={() => setTarget(null)}
                     onConfirm={() => target && setAccess.mutate({ id: target.id, enabled: !target.enabled }, { onSettled: () => setTarget(null) })}>
        This change is recorded in the audit trail.
      </ConfirmDialog>
    </Box>
  )
}
