import AddIcon from '@mui/icons-material/Add'
import {
  Box, Button, Card, CardContent, CardHeader, Dialog, DialogActions, DialogContent, DialogTitle, LinearProgress,
  MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useCreateEncounter, usePatient, usePatientEncounters } from '../../api/hooks'
import type { EncounterType } from '../../api/types'
import { SyntheticChip } from '../../components/Chips'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDate, fmtDateTime, humanize } from '../../components/format'

const ENCOUNTER_TYPES: EncounterType[] = ['EMERGENCY', 'PRIMARY_CARE', 'OUTPATIENT', 'INPATIENT', 'TELEMEDICINE']

export function PatientDetailPage() {
  const { patientId } = useParams()
  const navigate = useNavigate()
  const patient = usePatient(patientId)
  const encounters = usePatientEncounters(patientId)
  const createEncounter = useCreateEncounter(patientId ?? '')
  const [open, setOpen] = useState(false)
  const [type, setType] = useState<EncounterType>('EMERGENCY')
  const [complaint, setComplaint] = useState('')

  const start = () => createEncounter.mutate({ encounterType: type, chiefComplaint: complaint || undefined }, {
    onSuccess: (e) => navigate(`/encounters/${e.id}`),
  })

  const p = patient.data
  return (
    <Box>
      <PageHeader
        title={p ? <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><span>{p.lastName}, {p.firstName}</span>{p.synthetic && <SyntheticChip />}</Stack> : 'Patient'}
        subtitle={p ? `${p.externalPatientId} · born ${fmtDate(p.dateOfBirth)} (${p.ageYears} years) · biological sex ${humanize(p.biologicalSex)}${p.gender ? ` · gender ${p.gender}` : ''}` : undefined}
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)} disabled={!p}>New encounter</Button>} />
      {(patient.isLoading || encounters.isLoading) && <LinearProgress />}
      <ProblemAlert error={patient.error ?? encounters.error} />
      <Box sx={{ p: 2 }}>
        <Card>
          <CardHeader title="Encounters" slotProps={{ title: { variant: 'subtitle1' } }} />
          <CardContent sx={{ p: 0 }}>
            <Table aria-label="Encounters">
              <TableHead><TableRow><TableCell>Started</TableCell><TableCell>Type</TableCell><TableCell>Presenting complaint</TableCell>
                <TableCell>Status</TableCell><TableCell>Clinician</TableCell></TableRow></TableHead>
              <TableBody>
                {encounters.data?.map((e) => (
                  <TableRow key={e.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/encounters/${e.id}`)}>
                    <TableCell>{fmtDateTime(e.startedAt)}</TableCell>
                    <TableCell>{humanize(e.encounterType)}</TableCell>
                    <TableCell>{e.chiefComplaint}</TableCell>
                    <TableCell>{humanize(e.status)}</TableCell>
                    <TableCell>{e.clinicianId}</TableCell>
                  </TableRow>
                ))}
                {encounters.data?.length === 0 && (
                  <TableRow><TableCell colSpan={5}><Typography variant="body2" color="text.secondary">No encounters yet.</Typography></TableCell></TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Box>
      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Start encounter</DialogTitle>
        <DialogContent>
          <ProblemAlert error={createEncounter.error} />
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <TextField select label="Encounter type" value={type} onChange={(e) => setType(e.target.value as EncounterType)}>
              {ENCOUNTER_TYPES.map((t) => <MenuItem key={t} value={t}>{humanize(t)}</MenuItem>)}
            </TextField>
            <TextField label="Presenting complaint" value={complaint} onChange={(e) => setComplaint(e.target.value)} autoFocus
                       slotProps={{ htmlInput: { maxLength: 500 } }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={start} disabled={createEncounter.isPending}>Start encounter</Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
