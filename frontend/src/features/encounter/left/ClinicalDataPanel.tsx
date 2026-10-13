import AddIcon from '@mui/icons-material/Add'
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined'
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Chip, FormControlLabel, IconButton, Stack, Switch, Table,
  TableBody, TableCell, TableHead, TableRow, Tooltip, Typography,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { useMarkEnteredInError, type EnteredInErrorKind } from '../../../api/hooks'
import type { AbnormalFlag, Provenance } from '../../../api/types'
import { CodeChip, ProvenanceChip } from '../../../components/Chips'
import { ReasonDialog } from '../../../components/ReasonDialog'
import { fmtDate, fmtDateTime, fmtTime, humanize } from '../../../components/format'
import { severityColor } from '../../../theme'
import { useWorkstation } from '../WorkstationContext'
import { AllergyForm, HistoryForm, LabForm, MedicationForm, SymptomForm, VitalsForm } from './forms'

const flagColor = (flag?: AbnormalFlag) =>
  flag === 'CRITICAL_HIGH' || flag === 'CRITICAL_LOW' ? severityColor.CRITICAL
    : flag === 'HIGH' || flag === 'LOW' || flag === 'ABNORMAL' ? severityColor.WARNING : undefined

function Section({ title, count, children, onAdd, addLabel, defaultExpanded = true, readOnly }: {
  title: string; count?: number; children: ReactNode; onAdd?: () => void; addLabel?: string; defaultExpanded?: boolean; readOnly: boolean
}) {
  return (
    <Accordion defaultExpanded={defaultExpanded} sx={{ border: 'none', borderBottom: '1px solid #e5e8eb' }}>
      <AccordionSummary expandIcon={<ExpandMoreIcon fontSize="small" />} sx={{ minHeight: 34, '& .MuiAccordionSummary-content': { my: 0.5, alignItems: 'center', gap: 1 } }}>
        <Typography variant="subtitle2">{title}</Typography>
        {count !== undefined && <Chip label={count} sx={{ height: 18 }} />}
        <Box sx={{ flex: 1 }} />
        {onAdd && !readOnly && (
          <Tooltip title={addLabel ?? 'Add'}>
            <IconButton component="span" aria-label={addLabel ?? `Add ${title}`} onClick={(e) => { e.stopPropagation(); onAdd() }}><AddIcon fontSize="small" /></IconButton>
          </Tooltip>
        )}
      </AccordionSummary>
      <AccordionDetails sx={{ pt: 0, px: 1 }}>{children}</AccordionDetails>
    </Accordion>
  )
}

function RecordMeta({ provenance, enteredInError, reason }: { provenance: Provenance; enteredInError: boolean; reason?: string }) {
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mt: 0.25 }}>
      <ProvenanceChip provenance={provenance} />
      {provenance.source === 'AI_INFERRED' && <SmartToyOutlinedIcon sx={{ fontSize: 14, color: '#5e35b1' }} titleAccess="AI-proposed, confirmed by clinician" />}
      {enteredInError && <Chip label={`entered in error: ${reason}`} sx={{ height: 18, fontSize: '0.6rem', bgcolor: '#eceff1' }} />}
    </Stack>
  )
}

export function ClinicalDataPanel() {
  const { chart, encounterId, readOnly, proposal, reviewProposal } = useWorkstation()
  const [adding, setAdding] = useState<string | null>(null)
  const [showErrors, setShowErrors] = useState(false)
  const [errorTarget, setErrorTarget] = useState<{ kind: EnteredInErrorKind; id: string; label: string } | null>(null)
  const markError = useMarkEnteredInError(encounterId)
  const visible = <T extends { enteredInError: boolean }>(items: T[]) => items.filter((i) => showErrors || !i.enteredInError)
  const struck = (e: boolean) => (e ? { textDecoration: 'line-through', opacity: 0.55 } : {})
  const errorButton = (kind: EnteredInErrorKind, id: string, label: string, enteredInError: boolean) =>
    !readOnly && !enteredInError && (
      <Tooltip title="Mark as entered in error">
        <IconButton aria-label={`Mark ${label} as entered in error`} onClick={() => setErrorTarget({ kind, id, label })}>
          <BlockOutlinedIcon sx={{ fontSize: 15 }} />
        </IconButton>
      </Tooltip>
    )

  const p = chart.patient
  const activeAllergies = chart.allergies.filter((a) => a.status === 'ACTIVE' && !a.enteredInError)
  const symptoms = visible(chart.symptoms)
  const vitals = visible(chart.vitalSigns)
  const labs = visible(chart.labs)

  return (
    <Box>
      {proposal && (
        <Box sx={{ p: 1, borderBottom: '2px dashed #5e35b1', bgcolor: '#f6f2fd' }}>
          <Alert severity="info" icon={<SmartToyOutlinedIcon />} sx={{ mb: 1 }}>
            Reviewing an <strong>AI-proposed</strong> {proposal.category === 'SYMPTOM' ? 'symptom' : 'history entry'}.
            Nothing is recorded until you confirm. Verify with the patient before saving.
          </Alert>
          {proposal.category === 'SYMPTOM'
            ? <SymptomForm encounterId={encounterId} initialText={proposal.description} proposalReference={proposal.reference} onDone={() => reviewProposal(undefined)} />
            : <HistoryForm encounterId={encounterId} initialText={proposal.description} proposalReference={proposal.reference} onDone={() => reviewProposal(undefined)} />}
        </Box>
      )}
      <Box sx={{ px: 1.5, py: 1, borderBottom: '1px solid #e5e8eb' }}>
        <Typography variant="overline" color="text.secondary">Patient</Typography>
        <Typography variant="body2">{p.ageYears} years · biological sex {humanize(p.biologicalSex)}{p.gender ? ` · ${p.gender}` : ''}</Typography>
        <Typography variant="caption" color="text.secondary">DOB {fmtDate(p.dateOfBirth)} · ID {p.externalPatientId}</Typography>
        <Box sx={{ mt: 0.5 }}>
          {activeAllergies.length > 0
            ? <Chip color="error" label={`Allergies: ${activeAllergies.map((a) => a.allergen).join(', ')}`} sx={{ maxWidth: '100%' }} />
            : <Chip variant="outlined" label="No allergies recorded" />}
        </Box>
        <FormControlLabel sx={{ mt: 0.5 }} control={<Switch size="small" checked={showErrors} onChange={(e) => setShowErrors(e.target.checked)} />}
                          label={<Typography variant="caption">Show entries marked entered in error</Typography>} />
      </Box>

      <Section title="Symptoms" count={symptoms.length} onAdd={() => setAdding('symptom')} addLabel="Add symptom" readOnly={readOnly}>
        {adding === 'symptom' && <SymptomForm encounterId={encounterId} onDone={() => setAdding(null)} />}
        {symptoms.length === 0 && <Typography variant="caption" color="text.secondary">No symptoms recorded.</Typography>}
        {symptoms.map((s) => (
          <Box key={s.id} sx={{ py: 0.5, borderBottom: '1px dashed #eceff1', ...struck(s.enteredInError) }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ fontWeight: 600, flex: 1 }}>
                {s.presence === 'ABSENT' ? `No ${s.concept.displayName.toLowerCase()}` : s.concept.displayName}
                {s.severity !== undefined && s.severity !== null && ` · ${s.severity}/10`}
              </Typography>
              {s.presence === 'ABSENT' && <Chip label="pertinent negative" sx={{ height: 18, fontSize: '0.6rem' }} />}
              <CodeChip code={s.concept.code} system={s.concept.system} verification={s.concept.verificationStatus} />
              {errorButton('symptom', s.id, s.concept.displayName, s.enteredInError)}
            </Stack>
            <Typography variant="caption" color="text.secondary">
              {[s.onsetPattern && `onset ${s.onsetPattern.toLowerCase()}`, s.duration, s.location, s.character, s.radiation && `→ ${s.radiation}`,
                s.associatedSymptoms && `with ${s.associatedSymptoms}`].filter(Boolean).join(' · ')}
            </Typography>
            {s.notes && <Typography variant="caption" sx={{ display: 'block', fontStyle: 'italic' }}>{s.notes}</Typography>}
            <RecordMeta provenance={s.provenance} enteredInError={s.enteredInError} reason={s.enteredInErrorReason} />
          </Box>
        ))}
      </Section>

      <Section title="Vital signs" count={vitals.length} onAdd={() => setAdding('vitals')} addLabel="Record vital signs" readOnly={readOnly}>
        {adding === 'vitals' && <VitalsForm encounterId={encounterId} onDone={() => setAdding(null)} />}
        {vitals.length === 0 && <Typography variant="caption" color="text.secondary">No vital signs recorded.</Typography>}
        {vitals.length > 0 && (
          <Table aria-label="Vital signs">
            <TableHead><TableRow><TableCell>Time</TableCell><TableCell>Measurement</TableCell><TableCell>Value</TableCell><TableCell /></TableRow></TableHead>
            <TableBody>
              {vitals.map((v) => (
                <TableRow key={v.id} sx={struck(v.enteredInError)}>
                  <TableCell>{fmtTime(v.measuredAt)}</TableCell>
                  <TableCell><Tooltip title={`${v.concept.displayName}${v.concept.code ? ` · LOINC ${v.concept.code}` : ''} · ${v.provenance.source}`}><span>{v.concept.displayName}</span></Tooltip></TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{v.displayValue}</TableCell>
                  <TableCell padding="none">{errorButton('observation', v.id, v.concept.displayName, v.enteredInError)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Section>

      <Section title="Laboratory" count={labs.length} onAdd={() => setAdding('lab')} addLabel="Record lab result" readOnly={readOnly}>
        {adding === 'lab' && <LabForm encounterId={encounterId} onDone={() => setAdding(null)} />}
        {labs.length === 0 && <Typography variant="caption" color="text.secondary">No laboratory results.</Typography>}
        {labs.map((l) => (
          <Box key={l.id} sx={{ py: 0.5, borderBottom: '1px dashed #eceff1', ...struck(l.enteredInError) }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ flex: 1 }}><strong>{l.testName}</strong> {l.displayValue}</Typography>
              {l.abnormalFlag && (
                <Tooltip title={l.abnormalFlagSource === 'REPORTED' ? 'Flag reported by the laboratory' : 'Derived from the supplied reference range'}>
                  <Chip label={l.abnormalFlag.replace('_', ' ')} sx={{ height: 18, fontSize: '0.6rem', color: '#fff', bgcolor: flagColor(l.abnormalFlag) ?? '#78909c' }} />
                </Tooltip>
              )}
              <CodeChip code={l.testCode} system={l.codingSystem} verification={l.codeVerification} />
              {errorButton('lab', l.id, l.testName, l.enteredInError)}
            </Stack>
            <Typography variant="caption" color="text.secondary">
              {(l.referenceRangeLow !== undefined || l.referenceRangeHigh !== undefined) && `ref ${l.referenceRangeLow ?? ''}–${l.referenceRangeHigh ?? ''} · `}
              collected {fmtDateTime(l.collectedAt)}
            </Typography>
            <RecordMeta provenance={l.provenance} enteredInError={l.enteredInError} reason={l.enteredInErrorReason} />
          </Box>
        ))}
      </Section>

      <Section title="Medications" count={visible(chart.medications).length} onAdd={() => setAdding('medication')} addLabel="Add medication" readOnly={readOnly} defaultExpanded={false}>
        {adding === 'medication' && <MedicationForm encounterId={encounterId} onDone={() => setAdding(null)} />}
        {visible(chart.medications).length === 0 && <Typography variant="caption" color="text.secondary">No medications recorded.</Typography>}
        {visible(chart.medications).map((m) => (
          <Box key={m.id} sx={{ py: 0.5, borderBottom: '1px dashed #eceff1', ...struck(m.enteredInError) }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ flex: 1 }}><strong>{m.medicationName}</strong> {[m.dosage, m.frequency, m.route].filter(Boolean).join(' ')}</Typography>
              <Chip label={m.status.toLowerCase().replace('_', ' ')} variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />
              {errorButton('medication', m.id, m.medicationName, m.enteredInError)}
            </Stack>
            <RecordMeta provenance={m.provenance} enteredInError={m.enteredInError} reason={m.enteredInErrorReason} />
          </Box>
        ))}
      </Section>

      <Section title="Allergies" count={visible(chart.allergies).length} onAdd={() => setAdding('allergy')} addLabel="Add allergy" readOnly={readOnly} defaultExpanded={false}>
        {adding === 'allergy' && <AllergyForm encounterId={encounterId} onDone={() => setAdding(null)} />}
        {visible(chart.allergies).length === 0 && <Typography variant="caption" color="text.secondary">No allergies recorded (this is not the same as "no known allergies").</Typography>}
        {visible(chart.allergies).map((a) => (
          <Box key={a.id} sx={{ py: 0.5, borderBottom: '1px dashed #eceff1', ...struck(a.enteredInError) }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ flex: 1 }}><strong>{a.allergen}</strong>{a.reaction ? ` — ${a.reaction}` : ''}</Typography>
              <Chip label={`${a.severity.toLowerCase()} · ${a.status.toLowerCase()}`} variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />
              {errorButton('allergy', a.id, a.allergen, a.enteredInError)}
            </Stack>
            <RecordMeta provenance={a.provenance} enteredInError={a.enteredInError} reason={a.enteredInErrorReason} />
          </Box>
        ))}
      </Section>

      <Section title="History" count={visible(chart.history).length} onAdd={() => setAdding('history')} addLabel="Add history" readOnly={readOnly} defaultExpanded={false}>
        {adding === 'history' && <HistoryForm encounterId={encounterId} onDone={() => setAdding(null)} />}
        {visible(chart.history).length === 0 && <Typography variant="caption" color="text.secondary">No history recorded.</Typography>}
        {visible(chart.history).map((h) => (
          <Box key={h.id} sx={{ py: 0.5, borderBottom: '1px dashed #eceff1', ...struck(h.enteredInError) }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
              <Typography variant="body2" sx={{ flex: 1 }}>
                <Typography component="span" variant="caption" color="text.secondary">{humanize(h.category)}{h.relationship ? ` (${h.relationship})` : ''}: </Typography>
                <strong>{h.description}</strong>
              </Typography>
              <Chip label={h.status.toLowerCase()} variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />
              {errorButton('history', h.id, h.description, h.enteredInError)}
            </Stack>
            <RecordMeta provenance={h.provenance} enteredInError={h.enteredInError} reason={h.enteredInErrorReason} />
          </Box>
        ))}
      </Section>

      <ReasonDialog open={!!errorTarget} title="Mark as entered in error"
                    description={`"${errorTarget?.label}" will be kept for audit but excluded from assessments.`}
                    onCancel={() => setErrorTarget(null)}
                    onConfirm={(reason) => {
                      if (errorTarget) markError.mutate({ kind: errorTarget.kind, id: errorTarget.id, reason })
                      setErrorTarget(null)
                    }} />
    </Box>
  )
}
