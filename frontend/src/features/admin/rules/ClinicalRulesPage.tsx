import { Alert, Box, Button, Chip, Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography } from '@mui/material'
import { useMemo, useState } from 'react'
import { useRules, useSetRuleEnabled } from '../../../api/hooks'
import type { RuleDefinition } from '../../../api/types'
import { DemoRuleChip, SeverityChip } from '../../../components/Chips'
import { PageHeader } from '../../../components/PageHeader'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { ReasonDialog } from '../../../components/ReasonDialog'
import { fmtDateTime, humanize } from '../../../components/format'
import { NewRuleVersionDialog } from './NewRuleVersionDialog'

export function ClinicalRulesPage() {
  const rules = useRules()
  const setEnabled = useSetRuleEnabled()
  const [showHistory, setShowHistory] = useState(false)
  const [toggle, setToggle] = useState<RuleDefinition | null>(null)
  const [versioning, setVersioning] = useState<RuleDefinition | null>(null)
  const rows = useMemo(() => (rules.data ?? []).filter((r) => showHistory || r.lifecycleStatus !== 'SUPERSEDED')
    .sort((a, b) => a.category.localeCompare(b.category) || a.ruleId.localeCompare(b.ruleId) || b.createdAt.localeCompare(a.createdAt)), [rules.data, showHistory])
  return (
    <Box>
      <PageHeader title="Clinical rules" subtitle="Deterministic safety rules. They run independently of AI on every assessment and live safety check." />
      <Box sx={{ p: 2 }}>
        <Alert severity="warning" sx={{ mb: 1.5 }}>
          Rules marked DEMONSTRATION use illustrative example thresholds for software demonstration. They are not validated clinical guidance.
        </Alert>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 1 }}>
          <Switch checked={showHistory} onChange={(e) => setShowHistory(e.target.checked)} />
          <Typography variant="body2">Show version history (superseded definitions)</Typography>
        </Stack>
        <ProblemAlert error={rules.error ?? setEnabled.error} />
        <TableContainer sx={{ bgcolor: '#fff', border: '1px solid #dfe3e8' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Rule name</TableCell><TableCell>Category</TableCell><TableCell>Version</TableCell><TableCell>Severity</TableCell>
                <TableCell>Enabled</TableCell><TableCell>Source</TableCell><TableCell>Approved by</TableCell><TableCell>Effective</TableCell><TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id} sx={{ opacity: r.lifecycleStatus === 'SUPERSEDED' ? 0.55 : 1 }}>
                  <TableCell>
                    <Tooltip title={r.description}><Typography variant="body2" sx={{ fontWeight: 600 }}>{r.name}</Typography></Tooltip>
                    <Stack direction="row" spacing={0.5} sx={{ mt: 0.25 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{r.ruleId}</Typography>
                      {r.demonstrationOnly && <DemoRuleChip />}
                      {r.lifecycleStatus !== 'ACTIVE' && <Chip label={r.lifecycleStatus} sx={{ height: 17, fontSize: '0.58rem' }} />}
                    </Stack>
                  </TableCell>
                  <TableCell>{humanize(r.category)}</TableCell>
                  <TableCell>{r.version}</TableCell>
                  <TableCell><SeverityChip severity={r.severity} /></TableCell>
                  <TableCell>
                    <Switch checked={r.enabled} disabled={r.lifecycleStatus === 'SUPERSEDED'} onChange={() => setToggle(r)}
                            slotProps={{ input: { 'aria-label': `${r.enabled ? 'Disable' : 'Enable'} ${r.name}` } }} />
                  </TableCell>
                  <TableCell sx={{ maxWidth: 220 }}>{r.source}</TableCell>
                  <TableCell>{r.approvedBy}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{fmtDateTime(r.effectiveFrom)}{r.effectiveUntil ? ` → ${fmtDateTime(r.effectiveUntil)}` : ''}</TableCell>
                  <TableCell>{r.lifecycleStatus !== 'SUPERSEDED' && <Button onClick={() => setVersioning(r)}>New version</Button>}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
      <ReasonDialog open={!!toggle} title={`${toggle?.enabled ? 'Disable' : 'Enable'} rule “${toggle?.name ?? ''}”?`}
                    description={toggle?.enabled ? 'Disabled rules are listed on every assessment so clinicians know they did not run.' : undefined}
                    confirmLabel={toggle?.enabled ? 'Disable' : 'Enable'} onCancel={() => setToggle(null)}
                    onConfirm={(reason) => toggle && setEnabled.mutate({ id: toggle.id, enabled: !toggle.enabled, reason }, { onSettled: () => setToggle(null) })} />
      {versioning && <NewRuleVersionDialog rule={versioning} onClose={() => setVersioning(null)} />}
    </Box>
  )
}
