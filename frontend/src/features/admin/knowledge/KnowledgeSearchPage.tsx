import SearchIcon from '@mui/icons-material/Search'
import { Alert, Box, Button, Chip, FormControlLabel, Stack, Switch, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import { useKnowledgeSearch } from '../../../api/hooks'
import { JsonBlock } from '../../../components/JsonBlock'
import { PageHeader } from '../../../components/PageHeader'
import { ProblemAlert } from '../../../components/ProblemAlert'

/** RAG debugging: shows exactly what retrieval returns for a query. */
export function KnowledgeSearchPage() {
  const search = useKnowledgeSearch()
  const [query, setQuery] = useState('chest pain')
  const [topK, setTopK] = useState(8)
  const [includeInactive, setIncludeInactive] = useState(false)
  const run = () => query.trim() && search.mutate({ query: query.trim(), topK, includeInactive })
  return (
    <Box>
      <PageHeader title="Knowledge search" subtitle="Validate retrieval behaviour and debug the knowledge base. Results are not clinical advice." />
      <Box sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} component="form" onSubmit={(e) => { e.preventDefault(); run() }} sx={{ alignItems: 'center', mb: 1.5 }}>
          <TextField label="Query" value={query} onChange={(e) => setQuery(e.target.value)} sx={{ flex: 1 }} slotProps={{ htmlInput: { maxLength: 2000 } }} />
          <TextField label="Top K" type="number" value={topK} onChange={(e) => setTopK(Math.min(50, Math.max(1, Number(e.target.value) || 1)))} sx={{ width: 100 }} />
          <FormControlLabel control={<Switch checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />} label="Include draft/disabled" />
          <Button type="submit" variant="contained" startIcon={<SearchIcon />} disabled={search.isPending}>Search</Button>
        </Stack>
        <Alert severity="info" variant="outlined" sx={{ mb: 1 }}>
          Similarity is cosine similarity between query and chunk embeddings. It reflects textual relevance only and is never a measure of clinical confidence.
        </Alert>
        <ProblemAlert error={search.error} />
        {search.data && search.data.length === 0 && <Alert severity="info">No chunks matched.</Alert>}
        {search.data?.map((h, i) => (
          <Box key={h.chunkId} sx={{ mb: 1, p: 1.25, bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
              <Chip label={`#${i + 1}`} />
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                <RouterLink to={`/admin/knowledge/documents/${h.documentId}`}>{h.documentTitle}</RouterLink>
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {h.sourceOrganization} · v{h.documentVersion}{h.section ? ` · section: ${h.section}` : ''}{h.page ? ` · page ${h.page}` : ''}
              </Typography>
              <Box sx={{ flex: 1 }} />
              <Chip label={`similarity ${h.retrievalSimilarity.toFixed(3)}`} variant="outlined" />
              <Chip label={h.usableForClinicalRetrieval ? 'clinically retrievable' : 'excluded from clinical retrieval'}
                    color={h.usableForClinicalRetrieval ? 'success' : 'default'} variant="outlined" />
            </Stack>
            <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>{h.content}</Typography>
            <Box sx={{ mt: 0.5 }}><JsonBlock value={h.metadata} maxHeight={140} /></Box>
          </Box>
        ))}
      </Box>
    </Box>
  )
}
