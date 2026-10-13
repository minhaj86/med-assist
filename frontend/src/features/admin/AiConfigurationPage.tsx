import { Alert, Box, Chip, Grid, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useAiExecutions, useAiPrompts, useAiStatus } from '../../api/hooks'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDateTime } from '../../components/format'

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Grid size={{ xs: 6, md: 3 }}>
      <Typography variant="overline" color="text.secondary">{label}</Typography>
      <Typography variant="body2" component="div">{children ?? '—'}</Typography>
    </Grid>
  )
}

export function AiConfigurationPage() {
  const status = useAiStatus()
  const prompts = useAiPrompts()
  const executions = useAiExecutions()
  const s = status.data
  return (
    <Box>
      <PageHeader title="AI configuration" subtitle="Read-only view. Providers and models are configured through environment variables; secrets are never displayed." />
      <Box sx={{ p: 2 }}>
        <ProblemAlert error={status.error ?? prompts.error ?? executions.error} />
        {s && (
          <Box sx={{ p: 1.5, bgcolor: '#fff', border: '1px solid #dfe3e8', mb: 1.5 }}>
            <Grid container spacing={1}>
              <Item label="Provider">{s.provider}</Item>
              <Item label="Model">{s.model}</Item>
              <Item label="Endpoint">{s.endpoint}</Item>
              <Item label="Status"><Chip label={s.status} color={s.status === 'UP' ? 'success' : s.status === 'DISABLED' ? 'default' : 'warning'} /></Item>
              <Item label="Enabled">{s.enabled ? 'Yes' : 'No'}</Item>
              <Item label="API key">{s.apiKeyConfigured ? 'Configured (hidden)' : 'Not configured'}</Item>
              <Item label="Last successful request">{fmtDateTime(s.lastSuccessfulRequestAt)}</Item>
              <Item label="Last failed request">{fmtDateTime(s.lastFailedRequestAt)}</Item>
              <Item label="Temperature">{s.temperature}</Item>
              <Item label="Response format">{s.responseFormat}</Item>
              <Item label="Max attempts (incl. repair)">{s.maxAttempts}</Item>
              <Item label="Prompt versions">{s.activePrompts.map((p) => `${p.promptId}@${p.version}`).join(', ')}</Item>
              <Item label="Embedding provider">{s.embeddingProvider}</Item>
              <Item label="Embedding model">{`${s.embeddingModel} (${s.embeddingDimensions} dims)`}</Item>
              <Item label="Terminology">{s.terminologySources}</Item>
            </Grid>
            {!s.enabled && (
              <Alert severity="info" sx={{ mt: 1 }}>
                AI reasoning is disabled. Patient records, safety rules, scores and knowledge retrieval continue to work. Set AI_PROVIDER
                (openai, azure-openai or openai-compatible) and the matching environment variables to enable it.
              </Alert>
            )}
          </Box>
        )}

        <Typography variant="subtitle1">Prompt catalogue</Typography>
        <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8', mb: 1.5 }}>
          <Table>
            <TableHead><TableRow><TableCell>Prompt</TableCell><TableCell>Version</TableCell><TableCell>Status</TableCell><TableCell>Intended model</TableCell>
              <TableCell>Output schema</TableCell><TableCell>Checksum</TableCell><TableCell>Created</TableCell></TableRow></TableHead>
            <TableBody>
              {(prompts.data ?? []).map((p) => (
                <TableRow key={`${p.promptId}@${p.version}`}>
                  <TableCell><Tooltip title={p.description ?? ''}><span>{p.promptId}</span></Tooltip></TableCell>
                  <TableCell>{p.version}</TableCell>
                  <TableCell><Chip label={p.status} color={p.status === 'ACTIVE' ? 'success' : 'default'} sx={{ fontSize: '0.62rem' }} /></TableCell>
                  <TableCell>{p.intendedModel ?? 'any'}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.72rem' }}>{p.outputSchemaId}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.72rem' }}>{p.checksum.slice(0, 16)}…</TableCell>
                  <TableCell>{fmtDateTime(p.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>

        <Typography variant="subtitle1">Recent AI executions</Typography>
        <Typography variant="caption" color="text.secondary">Technical metadata only; prompts and responses are not shown here.</Typography>
        <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Table>
            <TableHead><TableRow><TableCell>Started</TableCell><TableCell>Purpose</TableCell><TableCell>Provider / model</TableCell><TableCell>Prompt</TableCell>
              <TableCell>Temp.</TableCell><TableCell>Status</TableCell><TableCell align="right">Attempts</TableCell><TableCell align="right">Duration</TableCell>
              <TableCell align="right">Tokens</TableCell><TableCell>Validation</TableCell></TableRow></TableHead>
            <TableBody>
              {(executions.data ?? []).map((e) => (
                <TableRow key={e.id}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{fmtDateTime(e.startedAt)}</TableCell>
                  <TableCell>{e.purpose}</TableCell>
                  <TableCell>{e.provider} · {e.reportedModel ?? e.requestedModel ?? '—'}</TableCell>
                  <TableCell>{e.promptId}@{e.promptVersion}</TableCell>
                  <TableCell>{e.temperature}</TableCell>
                  <TableCell>
                    <Chip label={e.status} color={e.status === 'SUCCEEDED' ? 'success' : e.status === 'VALIDATION_FAILED' ? 'warning' : 'error'} sx={{ fontSize: '0.62rem' }} />
                    {e.errorCategory && <Typography variant="caption" sx={{ display: 'block' }}>{e.errorCategory}</Typography>}
                  </TableCell>
                  <TableCell align="right">{e.attempts}</TableCell>
                  <TableCell align="right">{e.durationMs} ms</TableCell>
                  <TableCell align="right">{e.promptTokens ?? '—'} / {e.completionTokens ?? '—'}</TableCell>
                  <TableCell sx={{ maxWidth: 280 }}>{e.validationIssues.length === 0 ? 'No issues' : e.validationIssues.slice(0, 3).join('; ')}</TableCell>
                </TableRow>
              ))}
              {executions.data?.length === 0 && <TableRow><TableCell colSpan={10}>No AI executions yet.</TableCell></TableRow>}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
    </Box>
  )
}
