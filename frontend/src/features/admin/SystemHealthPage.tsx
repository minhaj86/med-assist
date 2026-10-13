import { Box, Chip, Grid, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { useSystemConfig, useSystemHealth } from '../../api/hooks'
import { JsonBlock } from '../../components/JsonBlock'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { healthColor } from './healthColor'


const uptime = (seconds: number) => {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${d ? `${d}d ` : ''}${h}h ${m}m`
}

export function SystemHealthPage() {
  const health = useSystemHealth()
  const config = useSystemConfig()
  const h = health.data
  return (
    <Box>
      <PageHeader title="System health" subtitle="Component status (refreshes every 15 s). Prometheus metrics are exposed on the separate management port." />
      <Box sx={{ p: 2 }}>
        <ProblemAlert error={health.error ?? config.error} />
        {h && (
          <>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
              <Chip label={`Overall: ${h.overall}`} color={healthColor(h.overall)} />
              <Chip label={`Version ${h.applicationVersion}`} variant="outlined" />
              <Chip label={`Uptime ${uptime(h.uptimeSeconds)}`} variant="outlined" />
              <Chip label={`Java ${h.javaVersion}`} variant="outlined" />
              <Chip label={`Profiles: ${h.activeProfiles.join(', ') || 'default'}`} variant="outlined" />
            </Stack>
            <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8', mb: 2 }}>
              <Table>
                <TableHead><TableRow><TableCell>Component</TableCell><TableCell>Status</TableCell><TableCell>Detail</TableCell><TableCell>Data</TableCell></TableRow></TableHead>
                <TableBody>
                  {h.checks.map((c) => (
                    <TableRow key={c.component}>
                      <TableCell sx={{ fontWeight: 600 }}>{c.component}</TableCell>
                      <TableCell><Chip label={c.status} color={healthColor(c.status)} sx={{ fontSize: '0.62rem' }} /></TableCell>
                      <TableCell>{c.detail}</TableCell>
                      <TableCell sx={{ fontSize: '0.72rem' }}>{Object.entries(c.data).map(([k, v]) => `${k}: ${String(v)}`).join(' · ')}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
        <Grid container spacing={2}>
          <Grid size={{ xs: 12 }}>
            <Typography variant="subtitle1">Effective configuration (non-secret)</Typography>
            {config.data && <JsonBlock value={config.data} maxHeight={520} />}
          </Grid>
        </Grid>
      </Box>
    </Box>
  )
}
