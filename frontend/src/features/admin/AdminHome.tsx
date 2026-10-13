import { Box, Card, CardActionArea, CardContent, Chip, Grid, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useAiStatus, useKnowledgeStatus, useRules, useSystemHealth } from '../../api/hooks'
import { PageHeader } from '../../components/PageHeader'
import { fmtDateTime } from '../../components/format'
import { healthColor } from './healthColor'

function Tile({ title, to, children }: { title: string; to: string; children: ReactNode }) {
  const navigate = useNavigate()
  return (
    <Grid size={{ xs: 12, md: 6, lg: 3 }}>
      <Card variant="outlined" sx={{ height: '100%' }}>
        <CardActionArea onClick={() => navigate(to)} sx={{ height: '100%', alignItems: 'flex-start' }}>
          <CardContent>
            <Typography variant="subtitle1" sx={{ mb: 0.5 }}>{title}</Typography>
            {children}
          </CardContent>
        </CardActionArea>
      </Card>
    </Grid>
  )
}

export function AdminHome() {
  const health = useSystemHealth()
  const ai = useAiStatus()
  const knowledge = useKnowledgeStatus()
  const rules = useRules()
  const active = (rules.data ?? []).filter((r) => r.lifecycleStatus === 'ACTIVE')
  return (
    <Box>
      <PageHeader title="Administration" subtitle="Operational overview. No patient-level data is shown here." />
      <Grid container spacing={2} sx={{ p: 2 }}>
        <Tile title="System health" to="/admin/health">
          {health.data && <Chip label={health.data.overall} color={healthColor(health.data.overall)} />}
          <Stack spacing={0.25} sx={{ mt: 1 }}>
            {health.data?.checks.map((c) => <Typography key={c.component} variant="body2">{c.component}: {c.status}</Typography>)}
          </Stack>
        </Tile>
        <Tile title="AI configuration" to="/admin/ai">
          {ai.data && (
            <>
              <Chip label={ai.data.enabled ? `${ai.data.provider} · ${ai.data.model ?? ''}` : 'AI disabled'} color={ai.data.enabled ? 'primary' : 'default'} />
              <Typography variant="body2" sx={{ mt: 1 }}>Status: {ai.data.status}</Typography>
              <Typography variant="body2">Last success: {fmtDateTime(ai.data.lastSuccessfulRequestAt)}</Typography>
            </>
          )}
        </Tile>
        <Tile title="Knowledge base" to="/admin/knowledge">
          {knowledge.data && (
            <>
              <Typography variant="body2">{knowledge.data.retrievableDocuments} of {knowledge.data.documents} documents retrievable</Typography>
              {knowledge.data.failedDocuments > 0 && <Chip label={`${knowledge.data.failedDocuments} ingestion failures`} color="error" sx={{ mt: 0.5 }} />}
              <Typography variant="body2" sx={{ mt: 0.5 }}>Embeddings: {knowledge.data.embeddingModel}</Typography>
            </>
          )}
        </Tile>
        <Tile title="Clinical rules" to="/admin/rules">
          <Typography variant="body2">{active.filter((r) => r.enabled).length} enabled / {active.length} active rules</Typography>
          <Typography variant="body2">{active.filter((r) => r.demonstrationOnly).length} demonstration-only</Typography>
        </Tile>
      </Grid>
    </Box>
  )
}
