import { Alert, Box, Chip, Stack, Typography } from '@mui/material'
import { useEffect, useRef } from 'react'
import { fmtDate } from '../../../components/format'
import { useWorkstation } from '../WorkstationContext'

/** Retrieved knowledge passages for the selected assessment version. */
export function KnowledgePanel() {
  const { version, highlightedEvidence } = useWorkstation()
  const refs = useRef<Record<string, HTMLDivElement | null>>({})
  useEffect(() => {
    if (highlightedEvidence) refs.current[highlightedEvidence]?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [highlightedEvidence])
  if (!version) return <Alert severity="info">Run an assessment to retrieve knowledge references.</Alert>
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Passages retrieved from approved, enabled knowledge documents. “Retrieval similarity” measures text similarity to the
        query only — it is not clinical confidence or evidence quality.
      </Typography>
      {version.evidence.length === 0 && <Alert severity="info" variant="outlined">No knowledge passages were retrieved for this version.</Alert>}
      {version.evidence.map((e) => {
        const active = e.evidenceKey === highlightedEvidence
        return (
          <Box key={e.evidenceKey} ref={(el: HTMLDivElement | null) => { refs.current[e.evidenceKey] = el }}
               sx={{ mb: 1, p: 1, border: `1px solid ${active ? '#00796b' : '#e0e6ec'}`, bgcolor: active ? '#e0f2f1' : '#fff' }}>
            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
              <Chip label={e.evidenceKey} color="secondary" sx={{ fontFamily: 'monospace' }} />
              <Typography variant="body2" sx={{ fontWeight: 700 }}>{e.documentTitle}</Typography>
              {e.syntheticSource && <Chip label="SYNTHETIC DEMO SOURCE" sx={{ height: 17, fontSize: '0.58rem', bgcolor: '#fff8e1' }} />}
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              {[e.sourceOrganization, e.documentVersion && `v${e.documentVersion}`, e.publicationDate && `published ${fmtDate(e.publicationDate)}`,
                e.section && `section: ${e.section}`, e.page && `page ${e.page}`].filter(Boolean).join(' · ')}
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>{e.excerpt}</Typography>
            <Typography variant="caption" color="text.secondary">Retrieval similarity {e.retrievalSimilarity.toFixed(2)} (not clinical confidence)</Typography>
          </Box>
        )
      })}
    </Box>
  )
}
