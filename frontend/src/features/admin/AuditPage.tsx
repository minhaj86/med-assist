import {
  Box, Button, Chip, Collapse, IconButton, MenuItem, Pagination, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Fragment, useState } from 'react'
import { useAuditEvents, type AuditFilters } from '../../api/hooks'
import { JsonBlock } from '../../components/JsonBlock'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDateTime, localInputToIso } from '../../components/format'

const AUDIT_EVENT_TYPES = [
  'PATIENT_CREATED', 'PATIENT_UPDATED', 'PATIENT_VIEWED', 'ENCOUNTER_CREATED', 'ENCOUNTER_UPDATED', 'ENCOUNTER_COMPLETED', 'ENCOUNTER_VIEWED',
  'CLINICAL_DATA_CREATED', 'CLINICAL_DATA_MODIFIED', 'CLINICAL_DATA_ENTERED_IN_ERROR', 'SAFETY_RULES_EVALUATED', 'ASSESSMENT_REQUESTED',
  'ASSESSMENT_COMPLETED', 'ASSESSMENT_FAILED', 'ASSESSMENT_VIEWED', 'KNOWLEDGE_RETRIEVAL', 'AI_PROMPT_EXECUTED', 'AI_RESPONSE_VALIDATED',
  'AI_RESPONSE_REJECTED', 'AI_PROVIDER_FAILURE', 'ASSISTANT_QUESTION_ASKED', 'CLINICIAN_FEEDBACK_RECORDED', 'INVESTIGATION_STATUS_CHANGED',
  'FINAL_ASSESSMENT_DOCUMENTED', 'KNOWLEDGE_DOCUMENT_UPLOADED', 'KNOWLEDGE_DOCUMENT_UPDATED', 'KNOWLEDGE_DOCUMENT_DELETED',
  'KNOWLEDGE_DOCUMENT_REINDEX_REQUESTED', 'KNOWLEDGE_INGESTION_COMPLETED', 'KNOWLEDGE_INGESTION_FAILED', 'CLINICAL_RULE_ENABLED',
  'CLINICAL_RULE_DISABLED', 'CLINICAL_RULE_VERSION_CREATED', 'USER_ENABLED', 'USER_DISABLED', 'FHIR_IMPORT', 'FHIR_EXPORT', 'DATA_RETENTION_PURGE',
] as const

const short = (id?: string) => (id ? `${id.slice(0, 8)}…` : '—')

export function AuditPage() {
  const [draft, setDraft] = useState<AuditFilters>({})
  const [filters, setFilters] = useState<AuditFilters>({})
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<string>()
  const events = useAuditEvents(filters, page)
  const apply = () => {
    setPage(0)
    setFilters(Object.fromEntries(Object.entries({ ...draft, from: localInputToIso(draft.from), to: localInputToIso(draft.to) })
      .filter(([, v]) => v)) as AuditFilters)
  }
  const field = (k: keyof AuditFilters) => ({ value: draft[k] ?? '', onChange: (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value })) })
  return (
    <Box>
      <PageHeader title="Audit trail" subtitle="Append-only record of clinically relevant and administrative actions" />
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }} component="form" onSubmit={(e) => { e.preventDefault(); apply() }}>
          <TextField select label="Event type" sx={{ width: 260 }} {...field('eventType')}>
            <MenuItem value="">Any</MenuItem>
            {AUDIT_EVENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </TextField>
          <TextField label="User ID" sx={{ width: 200 }} {...field('userId')} />
          <TextField label="Patient ID" sx={{ width: 200 }} {...field('patientId')} />
          <TextField label="Encounter ID" sx={{ width: 200 }} {...field('encounterId')} />
          <TextField label="From" type="datetime-local" sx={{ width: 210 }} slotProps={{ inputLabel: { shrink: true } }} {...field('from')} />
          <TextField label="To" type="datetime-local" sx={{ width: 210 }} slotProps={{ inputLabel: { shrink: true } }} {...field('to')} />
          <Button type="submit" variant="contained">Apply</Button>
          <Button onClick={() => { setDraft({}); setFilters({}); setPage(0) }}>Clear</Button>
        </Stack>
        <ProblemAlert error={events.error} />
        <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Table>
            <TableHead><TableRow><TableCell /><TableCell>Timestamp</TableCell><TableCell>Event</TableCell><TableCell>Outcome</TableCell><TableCell>User</TableCell>
              <TableCell>Entity</TableCell><TableCell>Patient</TableCell><TableCell>Encounter</TableCell><TableCell>Correlation ID</TableCell></TableRow></TableHead>
            <TableBody>
              {(events.data?.items ?? []).map((e) => (
                <Fragment key={e.id}>
                  <TableRow hover>
                    <TableCell padding="checkbox">
                      <IconButton aria-label="Show metadata" onClick={() => setExpanded(expanded === e.id ? undefined : e.id)}>
                        <ExpandMoreIcon fontSize="small" sx={{ transform: expanded === e.id ? 'rotate(180deg)' : 'none' }} />
                      </IconButton>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{fmtDateTime(e.timestamp)}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.72rem' }}>{e.eventType}</TableCell>
                    <TableCell><Chip label={e.outcome} color={e.outcome === 'SUCCESS' ? 'success' : 'error'} variant="outlined" sx={{ fontSize: '0.6rem' }} /></TableCell>
                    <TableCell>{e.userId ?? 'system'}</TableCell>
                    <TableCell>{e.entityType ? `${e.entityType} ${short(e.entityId)}` : '—'}</TableCell>
                    <TableCell title={e.patientId} sx={{ fontFamily: 'monospace', fontSize: '0.72rem' }}>{short(e.patientId)}</TableCell>
                    <TableCell title={e.encounterId} sx={{ fontFamily: 'monospace', fontSize: '0.72rem' }}>{short(e.encounterId)}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.72rem' }}>{e.correlationId ?? '—'}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell colSpan={9} sx={{ p: 0, borderBottom: expanded === e.id ? undefined : 'none' }}>
                      <Collapse in={expanded === e.id} unmountOnExit><Box sx={{ p: 1 }}><JsonBlock value={e.metadata} /></Box></Collapse>
                    </TableCell>
                  </TableRow>
                </Fragment>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <Stack direction="row" sx={{ alignItems: 'center', mt: 1 }}>
          <Typography variant="caption" sx={{ flex: 1 }}>{events.data ? `${events.data.totalItems} events` : ''}</Typography>
          {events.data && events.data.totalPages > 1 && <Pagination count={events.data.totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} size="small" />}
        </Stack>
      </Box>
    </Box>
  )
}
