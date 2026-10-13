import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import {
  Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, MenuItem,
  Stack, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useFinalize } from '../../../api/hooks'
import type { AssessmentVersion, DiagnosisCertainty } from '../../../api/types'
import { ProblemAlert } from '../../../components/ProblemAlert'

interface Row { conditionName: string; certainty: DiagnosisCertainty; sourceSuggestionId?: string }

/** Final clinical assessment: always an explicit, attested clinician action. */
export function FinalAssessmentDialog({ open, onClose, encounterId, version }: {
  open: boolean; onClose: () => void; encounterId: string; version?: AssessmentVersion
}) {
  const finalize = useFinalize(encounterId)
  const accepted = (version?.differentials ?? []).filter((d) => d.reviewState === 'ACCEPTED' || d.reviewState === 'MODIFIED')
  const [rows, setRows] = useState<Row[]>(() => accepted.map((d) => ({ conditionName: d.conditionName, certainty: 'PROVISIONAL', sourceSuggestionId: d.id })))
  const [summary, setSummary] = useState('')
  const [plan, setPlan] = useState('')
  const [attested, setAttested] = useState(false)
  const update = (i: number, patch: Partial<Row>) => setRows((r) => r.map((row, idx) => (idx === i ? { ...row, ...patch } : row)))
  const submit = () => finalize.mutate({
    reviewedVersionId: version?.id, summary, plan: plan || undefined, attestation: attested,
    diagnoses: rows.filter((r) => r.conditionName.trim()).map((r) => ({ ...r, conditionName: r.conditionName.trim() })),
  }, { onSuccess: onClose })
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Document final clinical assessment</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 1 }}>
          AI suggestions are not diagnoses. Only what you document here becomes the clinical assessment
          {version ? ` (reviewed assessment version V${version.versionNumber})` : ''}.
        </Alert>
        <ProblemAlert error={finalize.error} title="Assessment not finalized" />
        <Stack spacing={1.5}>
          <TextField label="Clinical summary" required multiline minRows={3} value={summary} onChange={(e) => setSummary(e.target.value)}
                     slotProps={{ htmlInput: { maxLength: 4000 } }} />
          <TextField label="Plan" multiline minRows={2} value={plan} onChange={(e) => setPlan(e.target.value)} slotProps={{ htmlInput: { maxLength: 4000 } }} />
          <Typography variant="subtitle2">Working diagnoses</Typography>
          {rows.map((r, i) => (
            <Stack key={i} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <TextField label="Condition" value={r.conditionName} onChange={(e) => update(i, { conditionName: e.target.value })} />
              <TextField select label="Certainty" value={r.certainty} sx={{ width: 180 }} onChange={(e) => update(i, { certainty: e.target.value as DiagnosisCertainty })}>
                <MenuItem value="PROVISIONAL">Provisional</MenuItem><MenuItem value="DIFFERENTIAL">Differential</MenuItem><MenuItem value="CONFIRMED">Confirmed</MenuItem>
              </TextField>
              {r.sourceSuggestionId && <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>from accepted suggestion</Typography>}
              <IconButton aria-label="Remove diagnosis" onClick={() => setRows((rs) => rs.filter((_, idx) => idx !== i))}><DeleteOutlineIcon fontSize="small" /></IconButton>
            </Stack>
          ))}
          <Button sx={{ alignSelf: 'flex-start' }} onClick={() => setRows((r) => [...r, { conditionName: '', certainty: 'PROVISIONAL' }])}>Add diagnosis</Button>
          <FormControlLabel control={<Checkbox checked={attested} onChange={(e) => setAttested(e.target.checked)} />}
                            label="I confirm this assessment reflects my own clinical judgment. I have reviewed AI-generated content and remain responsible for all decisions." />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={!attested || !summary.trim() || finalize.isPending}>Finalize assessment</Button>
      </DialogActions>
    </Dialog>
  )
}
