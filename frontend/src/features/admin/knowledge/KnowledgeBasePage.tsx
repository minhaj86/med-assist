import AddIcon from '@mui/icons-material/Add'
import {
  Alert, Box, Button, Chip, CircularProgress, IconButton, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import RefreshIcon from '@mui/icons-material/Refresh'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { useMemo, useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { useDeleteDocument, useKnowledgeDocuments, useKnowledgeStatus, useReindexDocument, useUpdateDocument } from '../../../api/hooks'
import type { KnowledgeDocumentSummary } from '../../../api/types'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { PageHeader } from '../../../components/PageHeader'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { fmtDate, fmtDateTime } from '../../../components/format'
import { ApprovalChip, IngestionChip } from './statusChips'
import { UploadDocumentDialog } from './UploadDocumentDialog'

export function KnowledgeBasePage() {
  const navigate = useNavigate()
  const docs = useKnowledgeDocuments()
  const status = useKnowledgeStatus()
  const update = useUpdateDocument()
  const reindex = useReindexDocument()
  const remove = useDeleteDocument()
  const [uploadOpen, setUploadOpen] = useState(false)
  const [toDelete, setToDelete] = useState<KnowledgeDocumentSummary | null>(null)
  const [filter, setFilter] = useState('')
  const [showSuperseded, setShowSuperseded] = useState(false)
  const rows = useMemo(() => (docs.data ?? [])
    .filter((d) => showSuperseded || !d.supersededById)
    .filter((d) => !filter || `${d.title} ${d.sourceOrganization}`.toLowerCase().includes(filter.toLowerCase())), [docs.data, filter, showSuperseded])

  return (
    <Box>
      <PageHeader title="Knowledge base" subtitle="Approved clinical documents used to ground AI reasoning (retrieval-augmented generation)"
                  actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setUploadOpen(true)}>Add document</Button>} />
      <Box sx={{ p: 2 }}>
        {status.data && (
          <Stack direction="row" spacing={1} sx={{ mb: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
            <Chip label={`Embedding: ${status.data.embeddingProvider} · ${status.data.embeddingModel} (${status.data.embeddingDimensions} dims)`} variant="outlined" />
            <Chip label={`Chunking: ${status.data.chunking.maxChars} chars, overlap ${status.data.chunking.overlapChars}`} variant="outlined" />
            <Chip label={`Retrieval: top ${status.data.retrieval.topK}, min similarity ${status.data.retrieval.minSimilarity}`} variant="outlined" />
            <Chip label={`${status.data.retrievableDocuments} of ${status.data.documents} documents retrievable`} color="primary" variant="outlined" />
            {status.data.failedDocuments > 0 && <Chip label={`${status.data.failedDocuments} failed`} color="error" />}
          </Stack>
        )}
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
          <TextField label="Filter documents" value={filter} onChange={(e) => setFilter(e.target.value)} sx={{ width: 320 }} />
          <Stack direction="row" sx={{ alignItems: 'center' }}><Switch checked={showSuperseded} onChange={(e) => setShowSuperseded(e.target.checked)} />
            <Typography variant="body2">Show superseded versions</Typography></Stack>
        </Stack>
        <ProblemAlert error={docs.error ?? update.error ?? reindex.error ?? remove.error} />
        {docs.isLoading && <CircularProgress size={24} />}
        <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Document</TableCell><TableCell>Source</TableCell><TableCell>Version</TableCell><TableCell>Published</TableCell>
                <TableCell align="right">Chunks</TableCell><TableCell>Status</TableCell><TableCell>Last indexed</TableCell><TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((d) => (
                <TableRow key={d.id} hover sx={{ cursor: 'pointer', opacity: d.supersededById ? 0.6 : 1 }} onClick={() => navigate(`/admin/knowledge/documents/${d.id}`)}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{d.title}</Typography>
                    <Stack direction="row" spacing={0.5} sx={{ mt: 0.25 }}>
                      {d.synthetic && <Chip label="SYNTHETIC DEMO" sx={{ height: 17, fontSize: '0.58rem', bgcolor: '#fff8e1' }} />}
                      {d.supersededById && <Chip label="superseded" sx={{ height: 17, fontSize: '0.58rem' }} />}
                      {!d.usableForClinicalRetrieval && !d.supersededById && (
                        <Tooltip title="Not used for clinical retrieval: requires APPROVED + enabled + INDEXED with the current embedding model">
                          <Chip label="not retrievable" variant="outlined" sx={{ height: 17, fontSize: '0.58rem' }} />
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell>{d.sourceOrganization}</TableCell>
                  <TableCell>{d.version}</TableCell>
                  <TableCell>{fmtDate(d.publicationDate)}</TableCell>
                  <TableCell align="right">{d.chunkCount}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5}>
                      <IngestionChip status={d.ingestionStatus} error={d.ingestionError} />
                      <ApprovalChip status={d.approvalStatus} />
                      {!d.enabled && <Chip label="DISABLED" sx={{ fontSize: '0.62rem' }} />}
                    </Stack>
                  </TableCell>
                  <TableCell>{fmtDateTime(d.lastIndexedAt)}</TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()} sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="View"><IconButton component={RouterLink} to={`/admin/knowledge/documents/${d.id}`} aria-label={`View ${d.title}`}><VisibilityOutlinedIcon fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Re-index"><span><IconButton aria-label={`Re-index ${d.title}`} disabled={reindex.isPending} onClick={() => reindex.mutate(d.id)}><RefreshIcon fontSize="small" /></IconButton></span></Tooltip>
                    <Button onClick={() => update.mutate({ id: d.id, enabled: !d.enabled })}>{d.enabled ? 'Disable' : 'Enable'}</Button>
                    <Tooltip title="Delete"><IconButton aria-label={`Delete ${d.title}`} onClick={() => setToDelete(d)}><DeleteOutlineIcon fontSize="small" /></IconButton></Tooltip>
                  </TableCell>
                </TableRow>
              ))}
              {docs.data && rows.length === 0 && (
                <TableRow><TableCell colSpan={8}><Alert severity="info">No documents. Add an approved clinical document to enable knowledge grounding.</Alert></TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
      {uploadOpen && <UploadDocumentDialog open onClose={() => setUploadOpen(false)} />}
      <ConfirmDialog open={!!toDelete} title="Delete document?" destructive confirmLabel="Delete" pending={remove.isPending}
                     onCancel={() => setToDelete(null)} onConfirm={() => toDelete && remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}>
        {`“${toDelete?.title ?? ''}” and its chunks and embeddings will be removed from the knowledge base. Past assessments keep their recorded citations. This is audited.`}
      </ConfirmDialog>
    </Box>
  )
}
