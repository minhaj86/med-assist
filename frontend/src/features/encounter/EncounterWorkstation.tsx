import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Stack, Tab, Tabs,
  TextField, Tooltip, Typography,
} from '@mui/material'
import { useCallback, useMemo, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import { useChart, useCompleteEncounter, useUpdateChiefComplaint, useVersion } from '../../api/hooks'
import { SyntheticChip } from '../../components/Chips'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDate, fmtDateTime, humanize } from '../../components/format'
import { AssessmentPanel } from './center/AssessmentPanel'
import { ClinicalDataPanel } from './left/ClinicalDataPanel'
import { AssistantPanel } from './right/AssistantPanel'
import { ExplainPanel } from './right/ExplainPanel'
import { KnowledgePanel } from './right/KnowledgePanel'
import { WorkstationContext, type Proposal, type RightTab, type WorkstationState } from './WorkstationContext'

const column = { minHeight: 0, overflowY: 'auto', bgcolor: '#fff', border: '1px solid #dfe3e8' } as const

function ChiefComplaint({ encounterId, value, readOnly }: { encounterId: string; value?: string; readOnly: boolean }) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(value ?? '')
  const update = useUpdateChiefComplaint(encounterId)
  if (!editing) {
    return (
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
        <Typography variant="body2"><strong>Chief complaint:</strong> {value || <em>not recorded</em>}</Typography>
        {!readOnly && (
          <IconButton aria-label="Edit chief complaint" onClick={() => { setText(value ?? ''); setEditing(true) }}><EditOutlinedIcon fontSize="small" /></IconButton>
        )}
      </Stack>
    )
  }
  return (
    <Stack direction="row" spacing={1} component="form"
           onSubmit={(e) => { e.preventDefault(); update.mutate(text.trim(), { onSuccess: () => setEditing(false) }) }} sx={{ alignItems: 'center' }}>
      <TextField autoFocus label="Chief complaint" value={text} onChange={(e) => setText(e.target.value)} sx={{ width: 380 }}
                 slotProps={{ htmlInput: { maxLength: 500 } }} />
      <Button type="submit" variant="contained" disabled={!text.trim() || update.isPending}>Save</Button>
      <Button onClick={() => setEditing(false)}>Cancel</Button>
      <ProblemAlert error={update.error} />
    </Stack>
  )
}

export function EncounterWorkstation() {
  const { encounterId = '' } = useParams()
  const chart = useChart(encounterId)
  const complete = useCompleteEncounter(encounterId)
  const [selectedVersionId, setSelectedVersionId] = useState<string>()
  const version = useVersion(selectedVersionId)
  const [selectedDifferentialId, setSelectedDifferentialId] = useState<string>()
  const [highlightedEvidence, setHighlightedEvidence] = useState<string>()
  const [rightTab, setRightTab] = useState<RightTab>('explain')
  const [proposal, setProposal] = useState<Proposal>()
  const [confirmComplete, setConfirmComplete] = useState(false)

  const onSelectVersion = useCallback((id?: string) => {
    setSelectedVersionId(id)
    setSelectedDifferentialId(undefined)
    setHighlightedEvidence(undefined)
  }, [])

  const readOnly = chart.data?.encounter.status !== 'IN_PROGRESS'
  const state = useMemo<WorkstationState | null>(() => (chart.data ? {
    encounterId,
    chart: chart.data,
    readOnly,
    version: version.data,
    selectedDifferentialId,
    selectDifferential: (id) => { setSelectedDifferentialId(id); setRightTab('explain') },
    highlightedEvidence,
    showEvidence: (key) => { setHighlightedEvidence(key); setRightTab('knowledge') },
    rightTab,
    setRightTab,
    proposal,
    reviewProposal: setProposal,
  } : null), [chart.data, encounterId, readOnly, version.data, selectedDifferentialId, highlightedEvidence, rightTab, proposal])

  if (chart.isLoading) return <Box sx={{ p: 4, textAlign: 'center' }}><CircularProgress /></Box>
  if (chart.error || !state) return <Box sx={{ p: 2 }}><ProblemAlert error={chart.error} title="Encounter could not be loaded" /></Box>

  const { patient, encounter } = state.chart
  return (
    <WorkstationContext.Provider value={state}>
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', p: 1, gap: 1, boxSizing: 'border-box' }}>
        <Box sx={{ px: 1.5, py: 1, bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
            <Tooltip title="Back to patient">
              <IconButton component={RouterLink} to={`/patients/${patient.id}`} aria-label="Back to patient"><ArrowBackIcon fontSize="small" /></IconButton>
            </Tooltip>
            <Typography variant="h6" component="h1">{patient.lastName}, {patient.firstName}</Typography>
            {patient.synthetic && <SyntheticChip />}
            <Typography variant="body2" color="text.secondary">
              {patient.externalPatientId} · {patient.ageYears} y · {humanize(patient.biologicalSex)} · DOB {fmtDate(patient.dateOfBirth)}
            </Typography>
            <Box sx={{ flex: 1 }} />
            <Chip label={humanize(encounter.encounterType)} variant="outlined" />
            <Chip label={humanize(encounter.status)} color={encounter.status === 'IN_PROGRESS' ? 'primary' : 'default'} />
            <Typography variant="caption" color="text.secondary">Started {fmtDateTime(encounter.startedAt)} · {encounter.clinicianId}</Typography>
            {!readOnly && <Button variant="outlined" onClick={() => setConfirmComplete(true)}>Complete encounter</Button>}
          </Stack>
          <ChiefComplaint key={encounter.chiefComplaint ?? ''} encounterId={encounterId} value={encounter.chiefComplaint} readOnly={readOnly} />
          {readOnly && <Alert severity="info" sx={{ mt: 0.5, py: 0 }}>This encounter is {humanize(encounter.status).toLowerCase()}; the record is read-only.</Alert>}
        </Box>

        <Box sx={{ flex: 1, minHeight: 0, display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', lg: 'minmax(300px, 25%) 1fr minmax(320px, 28%)' } }}>
          <Box component="section" aria-label="Clinical information" sx={column}><ClinicalDataPanel /></Box>
          <Box component="section" aria-label="Clinical assessment" sx={{ ...column, bgcolor: '#f7f9fb' }}>
            <AssessmentPanel selectedVersionId={selectedVersionId} onSelectVersion={onSelectVersion} />
          </Box>
          <Box component="section" aria-label="Evidence and assistant" sx={{ ...column, display: 'flex', flexDirection: 'column', overflowY: 'hidden' }}>
            <Tabs value={rightTab} onChange={(_, v: RightTab) => setRightTab(v)} variant="fullWidth" sx={{ borderBottom: '1px solid #e5e8eb', minHeight: 36 }}>
              <Tab value="explain" label="Explainability" sx={{ minHeight: 36 }} />
              <Tab value="knowledge" label={`Knowledge${version.data ? ` (${version.data.evidence.length})` : ''}`} sx={{ minHeight: 36 }} />
              <Tab value="assistant" label="AI assistant" sx={{ minHeight: 36 }} />
            </Tabs>
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', p: 1.25 }}>
              {rightTab === 'explain' && <ExplainPanel />}
              {rightTab === 'knowledge' && <KnowledgePanel />}
              {rightTab === 'assistant' && <AssistantPanel />}
            </Box>
          </Box>
        </Box>
      </Box>

      <Dialog open={confirmComplete} onClose={() => setConfirmComplete(false)}>
        <DialogTitle>Complete encounter?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">The encounter record becomes read-only. Documenting a final assessment is a separate, explicit step.</Typography>
          <ProblemAlert error={complete.error} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmComplete(false)}>Cancel</Button>
          <Button variant="contained" disabled={complete.isPending} onClick={() => complete.mutate(undefined, { onSuccess: () => setConfirmComplete(false) })}>Complete</Button>
        </DialogActions>
      </Dialog>
    </WorkstationContext.Provider>
  )
}
