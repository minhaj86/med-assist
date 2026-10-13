import {
  Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Grid, MenuItem, TextField,
} from '@mui/material'
import { useState } from 'react'
import { usePublishRuleVersion } from '../../../api/hooks'
import type { ClinicalSeverity, RuleDefinition } from '../../../api/types'
import { ProblemAlert } from '../../../components/ProblemAlert'

const SEVERITIES: ClinicalSeverity[] = ['INFO', 'WARNING', 'URGENT', 'CRITICAL']

/** Rule changes always create a new immutable version; historic definitions are never overwritten. */
export function NewRuleVersionDialog({ rule, onClose }: { rule: RuleDefinition; onClose: () => void }) {
  const publish = usePublishRuleVersion()
  const [form, setForm] = useState({
    version: '', name: rule.name, severity: rule.severity, parameters: JSON.stringify(rule.parameters, null, 2),
    description: rule.description, recommendedAction: rule.recommendedAction, references: rule.references.join('\n'),
    source: rule.source, approvedBy: '', demonstrationOnly: rule.demonstrationOnly, effectiveFrom: '', changeReason: '', enabled: true,
  })
  const [jsonError, setJsonError] = useState<string>()
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }))
  const submit = () => {
    let parameters: unknown
    try {
      parameters = JSON.parse(form.parameters)
      setJsonError(undefined)
    } catch {
      setJsonError('Parameters must be valid JSON')
      return
    }
    publish.mutate({
      ruleId: rule.ruleId,
      body: {
        version: form.version.trim(), name: form.name.trim() || undefined, severity: form.severity, parameters,
        description: form.description, recommendedAction: form.recommendedAction,
        references: form.references.split('\n').map((r) => r.trim()).filter(Boolean), source: form.source, approvedBy: form.approvedBy,
        demonstrationOnly: form.demonstrationOnly, effectiveFrom: form.effectiveFrom ? new Date(form.effectiveFrom).toISOString() : undefined,
        changeReason: form.changeReason, enabled: form.enabled,
      },
    }, { onSuccess: onClose })
  }
  const required = [form.version, form.description, form.recommendedAction, form.source, form.approvedBy, form.changeReason]
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>New version of “{rule.name}” (current v{rule.version})</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 1 }}>
          Clinical rule changes require clinical governance review. The new version is stored immutably; the current version is superseded
          from the effective date and remains available for audit. Keep “demonstration only” checked unless the thresholds come from an approved source.
        </Alert>
        <ProblemAlert error={publish.error} title="Version not published" />
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 4 }}><TextField label="New version" required value={form.version} onChange={(e) => set('version', e.target.value)} helperText="e.g. 1.1.0" /></Grid>
          <Grid size={{ xs: 8 }}><TextField label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} /></Grid>
          <Grid size={{ xs: 4 }}>
            <TextField select label="Severity" value={form.severity} onChange={(e) => set('severity', e.target.value as ClinicalSeverity)}>
              {SEVERITIES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid size={{ xs: 4 }}><TextField label="Effective from" type="datetime-local" value={form.effectiveFrom} onChange={(e) => set('effectiveFrom', e.target.value)}
                                            slotProps={{ inputLabel: { shrink: true } }} helperText="Empty = immediately" /></Grid>
          <Grid size={{ xs: 4 }}><TextField label="Approved by" required value={form.approvedBy} onChange={(e) => set('approvedBy', e.target.value)} /></Grid>
          <Grid size={{ xs: 12 }}>
            <TextField label={`Parameters (JSON, logic: ${rule.logicKey})`} multiline minRows={5} value={form.parameters} error={!!jsonError} helperText={jsonError}
                       onChange={(e) => set('parameters', e.target.value)} slotProps={{ htmlInput: { style: { fontFamily: 'monospace', fontSize: '0.78rem' } } }} />
          </Grid>
          <Grid size={{ xs: 12 }}><TextField label="Description" required multiline minRows={2} value={form.description} onChange={(e) => set('description', e.target.value)} /></Grid>
          <Grid size={{ xs: 12 }}><TextField label="Recommended action" required multiline value={form.recommendedAction} onChange={(e) => set('recommendedAction', e.target.value)} /></Grid>
          <Grid size={{ xs: 6 }}><TextField label="Source" required value={form.source} onChange={(e) => set('source', e.target.value)} /></Grid>
          <Grid size={{ xs: 6 }}><TextField label="References (one per line)" multiline value={form.references} onChange={(e) => set('references', e.target.value)} /></Grid>
          <Grid size={{ xs: 12 }}><TextField label="Change reason" required value={form.changeReason} onChange={(e) => set('changeReason', e.target.value)} /></Grid>
          <Grid size={{ xs: 6 }}><FormControlLabel control={<Checkbox checked={form.demonstrationOnly} onChange={(e) => set('demonstrationOnly', e.target.checked)} />} label="Demonstration only (not approved clinical guidance)" /></Grid>
          <Grid size={{ xs: 6 }}><FormControlLabel control={<Checkbox checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />} label="Enabled" /></Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={publish.isPending || required.some((v) => !v.trim())}>Publish version</Button>
      </DialogActions>
    </Dialog>
  )
}
