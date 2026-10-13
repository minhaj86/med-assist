import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import {
  Alert, Box, Card, CardContent, CardHeader, Chip, LinearProgress, List, ListItemButton, ListItemText, Stack, Table,
  TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { useMemo } from 'react'
import { useNavigate } from 'react-router'
import { useDashboard } from '../../api/hooks'
import type { DashboardEncounter } from '../../api/types'
import { OriginChip, SeverityChip, SyntheticChip } from '../../components/Chips'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDateTime, fmtTime, humanize } from '../../components/format'

function startOfToday() {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d.toISOString()
}

function AssessmentCell({ e }: { e: DashboardEncounter }) {
  if (!e.latestAssessment) return <Typography variant="caption" color="text.secondary">Not assessed</Typography>
  const a = e.latestAssessment
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
      <Chip label={`V${a.versionNumber}`} variant="outlined" />
      {a.criticalAlerts > 0 && <SeverityChip severity="CRITICAL" label={`${a.criticalAlerts} critical`} />}
      {a.urgentAlerts > 0 && <SeverityChip severity="URGENT" label={`${a.urgentAlerts} urgent`} />}
      {e.assessmentWorkflowStatus === 'FINALIZED' && <Chip label="Finalized" color="success" variant="outlined" />}
    </Stack>
  )
}

export function DashboardPage() {
  const since = useMemo(() => startOfToday(), [])
  const { data, isLoading, error } = useDashboard(since)
  const navigate = useNavigate()
  const open = (encounterId: string) => navigate(`/encounters/${encounterId}`)

  return (
    <Box>
      <PageHeader title="Clinician dashboard" subtitle="Today's activity. Safety alerts are deterministic unless labelled AI-generated." />
      {isLoading && <LinearProgress />}
      <ProblemAlert error={error} />
      {data && (
        <Box sx={{ p: 2, display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' }, gap: 2 }}>
          <Stack spacing={2}>
            <Card sx={{ borderColor: data.criticalAlerts.length ? '#b71c1c' : undefined }}>
              <CardHeader avatar={<WarningAmberIcon color={data.criticalAlerts.length ? 'error' : 'disabled'} />}
                          title="Critical safety alerts" subheader="From the latest assessment of encounters in progress"
                          slotProps={{ title: { variant: 'subtitle1' } }} />
              <CardContent sx={{ pt: 0 }}>
                {data.criticalAlerts.length === 0 && <Typography variant="body2" color="text.secondary">No critical alerts.</Typography>}
                <List dense disablePadding>
                  {data.criticalAlerts.map((a, i) => (
                    <ListItemButton key={i} onClick={() => open(a.encounterId)} sx={{ borderLeft: '4px solid #b71c1c', mb: 0.5 }}>
                      <ListItemText
                        primary={<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><SeverityChip severity={a.severity} /><OriginChip origin={a.origin} />
                          <span>{a.title}</span></Stack>}
                        secondary={`${a.patient.lastName}, ${a.patient.firstName} · assessment V${a.versionNumber} at ${fmtTime(a.assessedAt)}`} />
                      {a.patient.synthetic && <SyntheticChip />}
                    </ListItemButton>
                  ))}
                </List>
              </CardContent>
            </Card>
            <Card>
              <CardHeader title="Today's encounters" slotProps={{ title: { variant: 'subtitle1' } }} />
              <Table aria-label="Today's encounters">
                <TableHead>
                  <TableRow><TableCell>Time</TableCell><TableCell>Patient</TableCell><TableCell>Type</TableCell>
                    <TableCell>Presenting complaint</TableCell><TableCell>Status</TableCell><TableCell>Latest assessment</TableCell></TableRow>
                </TableHead>
                <TableBody>
                  {data.todaysEncounters.length === 0 && (
                    <TableRow><TableCell colSpan={6}><Typography variant="body2" color="text.secondary">No encounters today.</Typography></TableCell></TableRow>
                  )}
                  {data.todaysEncounters.map((e) => (
                    <TableRow key={e.encounterId} hover sx={{ cursor: 'pointer' }} onClick={() => open(e.encounterId)}>
                      <TableCell>{fmtTime(e.startedAt)}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                          <span><strong>{e.patient.lastName}</strong>, {e.patient.firstName} · {e.patient.ageYears}y {e.patient.biologicalSex[0]}</span>
                          {e.patient.synthetic && <SyntheticChip />}
                        </Stack>
                      </TableCell>
                      <TableCell>{humanize(e.encounterType)}</TableCell>
                      <TableCell>{e.chiefComplaint}</TableCell>
                      <TableCell>{humanize(e.status)}{e.mine && <Chip label="mine" sx={{ ml: 0.5 }} variant="outlined" />}</TableCell>
                      <TableCell><AssessmentCell e={e} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          </Stack>
          <Stack spacing={2}>
            <Card>
              <CardHeader title="Incomplete assessments" subheader="In-progress encounters without a finalized assessment"
                          slotProps={{ title: { variant: 'subtitle1' } }} />
              <List dense disablePadding>
                {data.incompleteAssessments.length === 0 && <Alert severity="success" sx={{ m: 1 }}>Nothing outstanding.</Alert>}
                {data.incompleteAssessments.map((e) => (
                  <ListItemButton key={e.encounterId} onClick={() => open(e.encounterId)}>
                    <ListItemText primary={`${e.patient.lastName}, ${e.patient.firstName}`}
                                  secondary={`${e.chiefComplaint ?? humanize(e.encounterType)} · ${e.latestAssessment ? `V${e.latestAssessment.versionNumber}` : 'not assessed'}`} />
                  </ListItemButton>
                ))}
              </List>
            </Card>
            <Card>
              <CardHeader title="Recent patients" slotProps={{ title: { variant: 'subtitle1' } }} />
              <List dense disablePadding>
                {data.recentPatients.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 1 }}>No recent patients.</Typography>}
                {data.recentPatients.map((p) => (
                  <ListItemButton key={p.id} onClick={() => navigate(`/patients/${p.id}`)}>
                    <ListItemText primary={`${p.lastName}, ${p.firstName}`} secondary={`${p.externalPatientId} · ${p.ageYears}y`} />
                    {p.synthetic && <SyntheticChip />}
                  </ListItemButton>
                ))}
              </List>
            </Card>
            <Card>
              <CardHeader title="Recent assessments" subheader="Run by you" slotProps={{ title: { variant: 'subtitle1' } }} />
              <List dense disablePadding>
                {data.recentAssessments.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 1 }}>No assessments yet.</Typography>}
                {data.recentAssessments.map((a) => (
                  <ListItemButton key={a.versionId} onClick={() => open(a.encounterId)}>
                    <ListItemText primary={`${a.patient.lastName}, ${a.patient.firstName} · V${a.versionNumber}`}
                                  secondary={`${fmtDateTime(a.createdAt)} · ${a.leadingConsideration ? `leading consideration: ${a.leadingConsideration}` : 'no differential'} · AI ${humanize(a.aiStatus)}`} />
                  </ListItemButton>
                ))}
              </List>
            </Card>
          </Stack>
        </Box>
      )}
    </Box>
  )
}
