import CalculateOutlinedIcon from '@mui/icons-material/CalculateOutlined'
import GavelOutlinedIcon from '@mui/icons-material/GavelOutlined'
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined'
import { Box, Chip, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { AlertOrigin, ClinicalSeverity, ReviewState, SupportStatus } from '../../../api/types'
import { DemoRuleChip, OriginChip, SeverityChip } from '../../../components/Chips'
import { humanize } from '../../../components/format'
import { AI_ACCENT, severityColor } from '../../../theme'
import { EvidenceChips, SupportBadge } from '../EvidenceChips'

export interface AlertLike {
  origin: AlertOrigin
  severity: ClinicalSeverity
  title: string
  explanation?: string
  recommendedAction?: string
  sourceReference?: string
  supportingFactKeys: string[]
  evidenceKeys?: string[]
  supportStatus?: SupportStatus
  demonstrationOnly: boolean
  reviewState?: ReviewState
}

/**
 * Deterministic alerts (rules, scores) use a solid severity bar; AI red flags use a dashed violet frame, an AI icon and
 * the label "AI-generated consideration" so they can never be mistaken for rule-based warnings.
 */
export function AlertCard({ alert, actions }: { alert: AlertLike; actions?: ReactNode }) {
  const ai = alert.origin === 'AI'
  const icon = ai ? <SmartToyOutlinedIcon sx={{ color: AI_ACCENT }} fontSize="small" />
    : alert.origin === 'CLINICAL_SCORE' ? <CalculateOutlinedIcon fontSize="small" /> : <GavelOutlinedIcon fontSize="small" />
  return (
    <Box role="alert" aria-label={`${ai ? 'AI-generated' : 'Deterministic'} ${alert.severity.toLowerCase()} alert: ${alert.title}`}
         sx={{
           mb: 1, p: 1, bgcolor: ai ? '#faf8ff' : '#fff',
           borderLeft: ai ? `4px dashed ${AI_ACCENT}` : `5px solid ${severityColor[alert.severity]}`,
           border: ai ? `1px dashed ${AI_ACCENT}` : '1px solid #e0e0e0',
           borderLeftWidth: ai ? 4 : 5,
         }}>
      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
        {icon}
        <SeverityChip severity={alert.severity} variant={ai ? 'outlined' : 'filled'} />
        <OriginChip origin={alert.origin} />
        {alert.demonstrationOnly && <DemoRuleChip />}
        {alert.supportStatus && <SupportBadge support={alert.supportStatus} />}
        {alert.reviewState && <Chip label={humanize(alert.reviewState)} variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />}
        <Typography variant="body2" sx={{ fontWeight: 700 }}>{alert.title}</Typography>
      </Stack>
      {ai && <Typography variant="caption" sx={{ color: AI_ACCENT, fontWeight: 600 }}>AI-generated consideration — verify against clinical findings</Typography>}
      {alert.explanation && <Typography variant="body2" sx={{ mt: 0.5 }}>{alert.explanation}</Typography>}
      {alert.recommendedAction && <Typography variant="caption" sx={{ display: 'block', mt: 0.25 }}>{alert.recommendedAction}</Typography>}
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mt: 0.5 }}>
        {alert.sourceReference && <Typography variant="caption" color="text.secondary">Source: {alert.sourceReference}</Typography>}
        <EvidenceChips factKeys={alert.supportingFactKeys} evidenceKeys={alert.evidenceKeys ?? []} />
        <Box sx={{ flex: 1 }} />
        {actions}
      </Stack>
    </Box>
  )
}
