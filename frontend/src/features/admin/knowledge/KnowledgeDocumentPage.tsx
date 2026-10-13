import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, CircularProgress, Grid, MenuItem, Pagination, Stack,
  Switch, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { useState, type ReactNode } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router'
import { useDeleteDocument, useKnowledgeChunks, useKnowledgeDocument, useReindexDocument, useUpdateDocument } from '../../../api/hooks'
import type { ApprovalStatus } from '../../../api/types'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { JsonBlock } from '../../../components/JsonBlock'
import { PageHeader } from '../../../components/PageHeader'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { fmtDate, fmtDateTime } from '../../../components/format'
import { APPROVAL_STATUSES } from './documentMetadata'
import { ApprovalChip, IngestionChip } from './statusChips'
import { UploadDocumentDialog } from './UploadDocumentDialog'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Grid size={{ xs: 6, md: 3 }}>
      <Typography variant="overline" color="text.secondary">{label}</Typography>
      <Typography variant="body2" component="div" sx={{ wordBreak: 'break-word' }}>{children ?? '—'}</Typography>
    </Grid>
  )
}

export function KnowledgeDocumentPage() {
  const { documentId } = useParams()
  const navigate = useNavigate()
  const doc = useKnowledgeDocument(documentId)
  const [page, setPage] = useState(0)
  const chunks = useKnowledgeChunks(documentId, page)
  const update = useUpdateDocument()
  const reindex = useReindexDocument()
  const remove = useDeleteDocument()
  const [tags, setTags] = useState<string>()
  const [versionOpen, setVersionOpen] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  if (doc.isLoading) return <Box sx={{ p: 3 }}><CircularProgress /></Box>
  if (!doc.data) return <Box sx={{ p: 2 }}><ProblemAlert error={doc.error} title="Document could not be loaded" /></Box>
  const d = doc.data
  const s = d.summary
  const tagText = tags ?? d.conditionTags.join(', ')
  return (
    <Box>
      <PageHeader title={s.title} subtitle={<RouterLink to="/admin/knowledge">← Knowledge base</RouterLink>}
                  actions={
                    <Stack direction="row" spacing={1}>
                      <Button variant="outlined" onClick={() => reindex.mutate(s.id)} disabled={reindex.isPending}>Re-index</Button>
                      <Button variant="outlined" onClick={() => setVersionOpen(true)} disabled={!!s.supersededById}>Upload new version</Button>
                      <Button variant="outlined" color="error" onClick={() => setConfirmDelete(true)}>Delete</Button>
                    </Stack>
                  } />
      <Box sx={{ p: 2 }}>
        <ProblemAlert error={update.error ?? reindex.error ?? remove.error} />
        {s.synthetic && <Alert severity="warning" sx={{ mb: 1 }}>Synthetic demonstration document — not real clinical guidance.</Alert>}
        {s.supersededById && <Alert severity="info" sx={{ mb: 1 }}>This version has been superseded and is no longer used for retrieval.</Alert>}
        <Box sx={{ p: 1.5, bgcolor: '#fff', border: '1px solid #dfe3e8', mb: 1.5 }}>
          <Typography variant="subtitle1">Metadata</Typography>
          <Grid container spacing={1}>
            <Field label="Document type">{s.documentType}</Field>
            <Field label="Source organization">{s.sourceOrganization}</Field>
            <Field label="Source URL">{d.sourceUrl ? <a href={d.sourceUrl} target="_blank" rel="noreferrer noopener">{d.sourceUrl}</a> : undefined}</Field>
            <Field label="Jurisdiction">{d.jurisdiction}</Field>
            <Field label="Version">{s.version}</Field>
            <Field label="Published">{fmtDate(s.publicationDate)}</Field>
            <Field label="Review date">{fmtDate(s.reviewDate)}</Field>
            <Field label="Uploaded">{`${fmtDateTime(s.uploadedAt)} by ${s.uploadedBy}`}</Field>
            <Field label="File">{d.originalFilename ? `${d.originalFilename} (${d.contentType}, ${Math.ceil(d.sizeBytes / 1024)} KB${d.pageCount ? `, ${d.pageCount} pages` : ''})` : d.contentType}</Field>
            <Field label="Checksum (SHA-256)"><span style={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>{d.checksum}</span></Field>
            <Field label="Ingestion"><IngestionChip status={s.ingestionStatus} error={s.ingestionError} /></Field>
            <Field label="Last indexed">{fmtDateTime(s.lastIndexedAt)}</Field>
          </Grid>
          {s.ingestionError && <Alert severity="error" sx={{ mt: 1 }}>{s.ingestionError}</Alert>}
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mt: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
            <TextField select label="Approval status" value={s.approvalStatus} sx={{ width: 180 }}
                       onChange={(e) => update.mutate({ id: s.id, approvalStatus: e.target.value as ApprovalStatus })}>
              {APPROVAL_STATUSES.map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
            </TextField>
            <Stack direction="row" sx={{ alignItems: 'center' }}>
              <Switch checked={s.enabled} onChange={(e) => update.mutate({ id: s.id, enabled: e.target.checked })} slotProps={{ input: { 'aria-label': 'Enabled' } }} />
              <Typography variant="body2">{s.enabled ? 'Enabled' : 'Disabled'}</Typography>
            </Stack>
            <ApprovalChip status={s.approvalStatus} />
            <Chip label={s.usableForClinicalRetrieval ? 'Used for clinical retrieval' : 'Not used for clinical retrieval'}
                  color={s.usableForClinicalRetrieval ? 'success' : 'default'} variant="outlined" />
            <TextField label="Condition tags" value={tagText} onChange={(e) => setTags(e.target.value)} sx={{ minWidth: 320 }} />
            <Button disabled={tags === undefined} onClick={() => update.mutate({ id: s.id, conditionTags: tagText.split(',').map((t) => t.trim()).filter(Boolean) }, { onSuccess: () => setTags(undefined) })}>
              Save tags
            </Button>
          </Stack>
        </Box>

        <Box sx={{ p: 1.5, bgcolor: '#fff', border: '1px solid #dfe3e8', mb: 1.5 }}>
          <Typography variant="subtitle1">Embedding status</Typography>
          <Typography variant="body2">
            {d.embeddedChunks} of {s.chunkCount} chunks embedded with the current model <strong>{d.currentEmbeddingModel}</strong>
            {s.embeddingModel && s.embeddingModel !== d.currentEmbeddingModel ? ` (indexed with ${s.embeddingModel})` : ''}.
          </Typography>
          {!d.embeddingsCurrent && s.ingestionStatus === 'INDEXED' && (
            <Alert severity="warning" sx={{ mt: 0.5 }}>Embeddings are not current for the configured model. Re-index to make this document retrievable.</Alert>
          )}
        </Box>

        <Box sx={{ p: 1.5, bgcolor: '#fff', border: '1px solid #dfe3e8', mb: 1.5 }}>
          <Typography variant="subtitle1">Versions</Typography>
          <Table>
            <TableHead><TableRow><TableCell>Version</TableCell><TableCell>Published</TableCell><TableCell>Status</TableCell><TableCell>Uploaded</TableCell></TableRow></TableHead>
            <TableBody>
              {d.versions.map((v) => (
                <TableRow key={v.id} hover selected={v.id === s.id} sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/knowledge/documents/${v.id}`)}>
                  <TableCell>{v.version}{v.supersededById ? ' (superseded)' : ''}</TableCell><TableCell>{fmtDate(v.publicationDate)}</TableCell>
                  <TableCell><ApprovalChip status={v.approvalStatus} /> <IngestionChip status={v.ingestionStatus} /></TableCell>
                  <TableCell>{fmtDateTime(v.uploadedAt)} · {v.uploadedBy}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>

        <Accordion sx={{ mb: 1.5 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}><Typography variant="subtitle1">Extracted text</Typography></AccordionSummary>
          <AccordionDetails>
            <Box component="pre" sx={{ whiteSpace: 'pre-wrap', fontSize: '0.78rem', maxHeight: 420, overflow: 'auto', m: 0 }}>{d.extractedText ?? '(not available)'}</Box>
          </AccordionDetails>
        </Accordion>

        <Box sx={{ p: 1.5, bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Stack direction="row" sx={{ alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle1" sx={{ flex: 1 }}>Chunks ({chunks.data?.totalItems ?? s.chunkCount})</Typography>
            {chunks.data && chunks.data.totalPages > 1 && (
              <Pagination count={chunks.data.totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} size="small" />
            )}
          </Stack>
          {(chunks.data?.items ?? []).map((c) => (
            <Box key={c.id} sx={{ mb: 1, p: 1, border: '1px solid #e0e6ec' }}>
              <Stack direction="row" spacing={1} sx={{ mb: 0.5 }}>
                <Chip label={`#${c.chunkIndex}`} />
                {c.section && <Chip label={`Section: ${c.section}`} variant="outlined" />}
                {c.pageNumber && <Chip label={`Page ${c.pageNumber}`} variant="outlined" />}
                <Typography variant="caption" color="text.secondary">{c.charCount} chars · ~{c.tokenEstimate} tokens</Typography>
              </Stack>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{c.content}</Typography>
              <Accordion disableGutters sx={{ mt: 0.5, border: 'none' }}>
                <AccordionSummary expandIcon={<ExpandMoreIcon fontSize="small" />} sx={{ minHeight: 28, px: 0 }}><Typography variant="caption">Metadata</Typography></AccordionSummary>
                <AccordionDetails sx={{ px: 0 }}><JsonBlock value={c.metadata} /></AccordionDetails>
              </Accordion>
            </Box>
          ))}
        </Box>
      </Box>
      {versionOpen && (
        <UploadDocumentDialog open onClose={() => setVersionOpen(false)} newVersionOf={s.id}
                              initial={{ title: s.title, documentType: s.documentType, sourceOrganization: s.sourceOrganization, sourceUrl: d.sourceUrl ?? '',
                                jurisdiction: d.jurisdiction ?? '', version: '', approvalStatus: 'DRAFT', conditionTags: d.conditionTags.join(', ') }} />
      )}
      <ConfirmDialog open={confirmDelete} title="Delete document?" destructive confirmLabel="Delete" pending={remove.isPending}
                     onCancel={() => setConfirmDelete(false)}
                     onConfirm={() => remove.mutate(s.id, { onSuccess: () => navigate('/admin/knowledge') })}>
        This version, its chunks and embeddings will be removed. Past assessments keep their recorded citations.
      </ConfirmDialog>
    </Box>
  )
}
