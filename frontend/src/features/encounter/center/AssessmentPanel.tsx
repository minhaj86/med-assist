import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined'
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, FormControlLabel, MenuItem, Stack, Switch,
  TextField, Tooltip, Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { api } from '../../../api/client'
import { useFinalAssessment, useInvestigations, useRun, useStartAssessment, useVersions } from '../../../api/hooks'
import type { AiReasoningStatus, AssessmentRun } from '../../../api/types'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { fmtDateTime, humanize } from '../../../components/format'
import { useShortcuts } from '../../../hooks/useShortcuts'
import { AI_ACCENT } from '../../../theme'
import { useWorkstation } from '../WorkstationContext'
import { AlertCard } from './AlertCards'
import { DifferentialCard } from './DifferentialCard'
import { FeedbackButtons } from './FeedbackButtons'
import { FinalAssessmentDialog } from './FinalAssessmentDialog'
import { InvestigationsCard } from './InvestigationsCard'
import { LiveSafetyAlerts } from './LiveSafetyAlerts'
import { ProgressStages } from './ProgressStages'
import { ScoresPanel } from './ScoresPanel'

const AI_STATUS_SEVERITY: Record<AiReasoningStatus, 'success' | 'info' | 'warning'> = {
  COMPLETED: 'success', NOT_REQUESTED: 'info', NOT_CONFIGURED: 'info', UNAVAILABLE: 'info', OUTPUT_REJECTED: 'warning',
}

export function AssessmentPanel({ selectedVersionId, onSelectVersion }: {
  selectedVersionId?: string
  onSelectVersion: (id?: string) => void
}) {
  const { encounterId, readOnly, version } = useWorkstation()
  const qc = useQueryClient()
  const versions = useVersions(encounterId)
  const finalAssessment = useFinalAssessment(encounterId)
  const investigations = useInvestigations(encounterId)
  const start = useStartAssessment(encounterId)
  const [includeAi, setIncludeAi] = useState(true)
  const [runId, setRunId] = useState<string>()
  const [finalizing, setFinalizing] = useState(false)
  const run = useRun(runId)
  const finalized = finalAssessment.data?.status === 'FINALIZED'

  // Resume progress display for a run started before a page reload.
  useEffect(() => {
    api.get<AssessmentRun>(`/encounters/${encounterId}/assessment-runs/latest`)
      .then((r) => { if (r.status === 'QUEUED' || r.status === 'RUNNING') setRunId(r.runId) })
      .catch(() => undefined)
  }, [encounterId])

  useEffect(() => {
    if (run.data?.status === 'COMPLETED' && run.data.versionId) {
      void qc.invalidateQueries({ queryKey: ['versions', encounterId] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
      onSelectVersion(run.data.versionId)
    }
  }, [run.data?.status, run.data?.versionId, encounterId, qc, onSelectVersion])

  useEffect(() => {
    if (!selectedVersionId && versions.data && versions.data.length > 0) onSelectVersion(versions.data[0].id)
  }, [versions.data, selectedVersionId, onSelectVersion])

  const running = run.data ? run.data.status === 'QUEUED' || run.data.status === 'RUNNING' : start.isPending
  const canRun = !readOnly && !finalized && !running
  const runAssessment = () => {
    if (!canRun) return
    start.mutate(includeAi, { onSuccess: (r) => setRunId(r.runId) })
  }
  const shortcuts = useMemo(() => [{ keys: 'alt+r', handler: runAssessment, allowInInputs: true }], [canRun, includeAi]) // eslint-disable-line react-hooks/exhaustive-deps
  useShortcuts(shortcuts)

  return (
    <Box sx={{ p: 1.5 }}>
      <LiveSafetyAlerts />

      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="subtitle1" sx={{ flex: 1 }}>Clinical assessment</Typography>
        <Tooltip title="When off, only deterministic rules, scores and knowledge retrieval run">
          <FormControlLabel control={<Switch size="small" checked={includeAi} onChange={(e) => setIncludeAi(e.target.checked)} />}
                            label={<Typography variant="body2">Include AI reasoning</Typography>} />
        </Tooltip>
        <Button variant="contained" startIcon={<PlayArrowIcon />} onClick={runAssessment} disabled={!canRun}
                title="Run assessment (Alt+R)">{versions.data?.length ? 'Re-run assessment' : 'Run assessment'}</Button>
        {versions.data && versions.data.length > 0 && (
          <TextField select label="Version" value={selectedVersionId ?? ''} onChange={(e) => onSelectVersion(e.target.value)} sx={{ width: 230 }}>
            {versions.data.map((v) => (
              <MenuItem key={v.id} value={v.id}>V{v.versionNumber} · {fmtDateTime(v.createdAt)}</MenuItem>
            ))}
          </TextField>
        )}
        {!readOnly && !finalized && version && (
          <Button variant="outlined" color="success" onClick={() => setFinalizing(true)}>Document final assessment</Button>
        )}
      </Stack>

      <ProblemAlert error={start.error} title="Assessment could not be started" />
      {run.data && (running || run.data.status === 'FAILED') && <ProgressStages run={run.data} />}

      {finalized && finalAssessment.data && (
        <Alert severity="success" sx={{ mb: 1 }}>
          <strong>Final assessment documented</strong> by {finalAssessment.data.finalizedBy} at {fmtDateTime(finalAssessment.data.finalizedAt)}.
          <Typography variant="body2" sx={{ mt: 0.5 }}>{finalAssessment.data.summary}</Typography>
          {finalAssessment.data.diagnoses.map((d) => (
            <Chip key={d.conditionName} label={`${d.conditionName} (${d.certainty.toLowerCase()})`} sx={{ mr: 0.5, mt: 0.5 }} />
          ))}
          {finalAssessment.data.plan && <Typography variant="body2" sx={{ mt: 0.5 }}><strong>Plan:</strong> {finalAssessment.data.plan}</Typography>}
        </Alert>
      )}

      {!version && !running && versions.data?.length === 0 && (
        <Alert severity="info">No assessment yet. Record the clinical information, then run an assessment (Alt+R).</Alert>
      )}

      {version && (
        <Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap' }}>
            <Chip label={`Assessment V${version.versionNumber}`} color="primary" />
            <Typography variant="caption" color="text.secondary">
              Immutable record · {fmtDateTime(version.createdAt)} by {version.createdBy} · input hash {version.inputHash.slice(0, 12)}…
            </Typography>
          </Stack>
          <Alert severity={AI_STATUS_SEVERITY[version.ai.status]} icon={<SmartToyOutlinedIcon />} sx={{ mb: 1 }}>
            <strong>AI reasoning: {humanize(version.ai.status)}.</strong> {version.ai.message}
          </Alert>
          {version.dataQualityWarnings.map((w) => <Alert key={w} severity="info" variant="outlined" sx={{ mb: 0.5, py: 0 }}>{w}</Alert>)}

          {version.changes.previousVersionNumber !== undefined && version.changes.previousVersionNumber !== null && (
            <Accordion sx={{ mb: 1 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="subtitle2">What changed since V{version.changes.previousVersionNumber}
                  ({version.changes.inputChanges.length} input, {version.changes.outputChanges.length} output changes)</Typography>
              </AccordionSummary>
              <AccordionDetails>
                {[...version.changes.inputChanges, ...version.changes.outputChanges].map((c, i) => (
                  <Typography key={i} variant="body2" sx={{ py: 0.15 }}>
                    <Chip label={humanize(c.type)} sx={{ height: 18, fontSize: '0.6rem', mr: 0.5 }} />
                    {c.description}{c.previousDescription ? <span style={{ color: '#78909c' }}> (was: {c.previousDescription})</span> : null}
                  </Typography>
                ))}
                {version.changes.inputChanges.length + version.changes.outputChanges.length === 0 && <Typography variant="body2">No changes.</Typography>}
              </AccordionDetails>
            </Accordion>
          )}

          {version.aiRedFlags.length > 0 && (
            <Box sx={{ mb: 1.5 }}>
              <Typography variant="subtitle2" sx={{ color: AI_ACCENT }}>AI-generated red flags (require clinical verification)</Typography>
              {version.aiRedFlags.map((f) => (
                <AlertCard key={f.id} alert={f} actions={<FeedbackButtons type="RED_FLAG" id={f.id} label={f.title} compact />} />
              ))}
            </Box>
          )}

          <Typography variant="subtitle2" sx={{ mt: 1 }}>Possible differential diagnoses ({version.differentials.length})</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
            For clinician consideration only. Ranks are display order, not likelihood.
          </Typography>
          {version.differentials.length === 0 && <Alert severity="info" variant="outlined">No differential considerations in this version.</Alert>}
          {version.differentials.map((d) => <DifferentialCard key={d.id} differential={d} investigations={investigations.data ?? []} />)}

          {(version.uncertainties.length > 0 || version.additionalQuestions.length > 0) && (
            <Box sx={{ mt: 1, p: 1, border: `1px dashed ${AI_ACCENT}`, bgcolor: '#faf8ff' }}>
              <Typography variant="subtitle2" sx={{ color: AI_ACCENT }}>AI-identified uncertainty</Typography>
              {version.uncertainties.map((u) => <Typography key={u} variant="body2">• {u}</Typography>)}
              {version.additionalQuestions.length > 0 && <Typography variant="subtitle2" sx={{ mt: 0.5 }}>Questions that could change the assessment</Typography>}
              {version.additionalQuestions.map((q) => <Typography key={q} variant="body2">? {q}</Typography>)}
            </Box>
          )}

          <Accordion sx={{ mt: 1 }}>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}><Typography variant="subtitle2">Clinical scores ({version.scores.length})</Typography></AccordionSummary>
            <AccordionDetails><ScoresPanel scores={version.scores} /></AccordionDetails>
          </Accordion>
          <Accordion>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography variant="subtitle2">Deterministic alerts at time of assessment ({version.safetyAlerts.length}) · {version.executedRules.length} rule versions executed</Typography>
            </AccordionSummary>
            <AccordionDetails>
              {version.safetyAlerts.map((a) => <AlertCard key={a.id} alert={a} />)}
              {version.disabledRules.length > 0 && <Typography variant="caption">Disabled rules: {version.disabledRules.join(', ')}</Typography>}
              {version.validationIssues.length > 0 && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 700 }}>AI output validation findings</Typography>
                  {version.validationIssues.map((v, i) => <Typography key={i} variant="caption" sx={{ display: 'block' }}>• {v}</Typography>)}
                </Box>
              )}
            </AccordionDetails>
          </Accordion>
        </Box>
      )}

      <InvestigationsCard />
      {finalizing && <FinalAssessmentDialog open onClose={() => setFinalizing(false)} encounterId={encounterId} version={version} />}
    </Box>
  )
}
