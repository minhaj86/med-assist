import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined'
import HelpOutlineIcon from '@mui/icons-material/HelpOutlineOutlined'
import HighlightOffIcon from '@mui/icons-material/HighlightOff'
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined'
import LibraryBooksOutlinedIcon from '@mui/icons-material/LibraryBooksOutlined'
import { Box, Button, Chip, Collapse, Stack, Tooltip, Typography } from '@mui/material'
import { useState, type ReactNode } from 'react'
import type { Differential, Investigation, StatementView } from '../../../api/types'
import { humanize } from '../../../components/format'
import { AI_ACCENT } from '../../../theme'
import { EvidenceChips, SupportBadge } from '../EvidenceChips'
import { useWorkstation } from '../WorkstationContext'
import { FeedbackButtons } from './FeedbackButtons'

const confidenceColor = { HIGH: '#1f4e79', MODERATE: '#4f6d8a', LOW: '#8796a5' }

function Block({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box sx={{ mt: 0.75 }}>
      <Typography variant="overline" color="text.secondary">{title}</Typography>
      {children}
    </Box>
  )
}

function StatementLine({ s, icon }: { s: StatementView; icon: ReactNode }) {
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'flex-start', py: 0.15 }}>
      <Box sx={{ mt: '2px' }}>{icon}</Box>
      <Typography variant="body2" sx={{ color: s.support === 'WITHHELD' ? 'text.secondary' : undefined, fontStyle: s.support === 'WITHHELD' ? 'italic' : undefined }}>
        {s.text}
        <EvidenceChips factKeys={s.factKeys} evidenceKeys={s.evidenceKeys} />
        <SupportBadge support={s.support} />
      </Typography>
    </Stack>
  )
}

export function DifferentialCard({ differential: d, investigations }: { differential: Differential; investigations: Investigation[] }) {
  const { selectDifferential, selectedDifferentialId, version } = useWorkstation()
  const [expanded, setExpanded] = useState(d.rank <= 2)
  const ai = d.method === 'AI_REASONED'
  const selected = selectedDifferentialId === d.id
  const capped = d.originalConfidence && d.confidence !== d.originalConfidence
  return (
    <Box component="article" aria-label={`Possible differential ${d.rank}: ${d.conditionName}`}
         sx={{ mb: 1, p: 1, bgcolor: '#fff', border: `1px ${ai ? 'dashed' : 'solid'} ${selected ? '#1f4e79' : ai ? '#b9a6e3' : '#cfd8dc'}`,
           boxShadow: selected ? '0 0 0 2px rgba(31,78,121,0.15)' : 'none' }}>
      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
        <Chip label={`#${d.rank}`} sx={{ fontWeight: 700 }} />
        <Typography variant="subtitle1" sx={{ mr: 0.5 }}>{d.conditionName}</Typography>
        <Chip label="Possible differential" variant="outlined" sx={{ fontSize: '0.62rem' }} />
        {ai
          ? <Chip icon={<SmartToyOutlinedIcon sx={{ fontSize: 14 }} />} label="AI-reasoned" variant="outlined" sx={{ color: AI_ACCENT, borderColor: AI_ACCENT, fontSize: '0.62rem' }} />
          : <Chip icon={<LibraryBooksOutlinedIcon sx={{ fontSize: 14 }} />} label="Knowledge-linked, not AI-assessed" variant="outlined" color="secondary" sx={{ fontSize: '0.62rem' }} />}
        {d.confidence && (
          <Tooltip title="Qualitative: how strongly the provided evidence supports considering this possibility. Not a probability.">
            <Chip label={`Confidence: ${d.confidence}${capped ? ` (capped from ${d.originalConfidence})` : ''}`}
                  sx={{ bgcolor: confidenceColor[d.confidence], color: '#fff', fontSize: '0.62rem' }} />
          </Tooltip>
        )}
        <Tooltip title="Whether the reasoning cites validated knowledge-base passages">
          <Chip label={humanize(d.grounding)} variant="outlined" sx={{ fontSize: '0.62rem' }}
                color={d.grounding === 'GROUNDED' ? 'success' : d.grounding === 'UNGROUNDED' ? 'warning' : 'default'} />
        </Tooltip>
        {ai && <Chip label={`Review: ${humanize(d.reviewState)}`} variant="outlined" sx={{ fontSize: '0.62rem' }} />}
        {d.code && <Chip label={`${d.codingSystem} ${d.code}`} variant="outlined" sx={{ fontSize: '0.62rem' }} />}
      </Stack>
      {d.whyConsidered && <Typography variant="body2" sx={{ mt: 0.5 }}><strong>Why considered:</strong> {d.whyConsidered}</Typography>}
      <Collapse in={expanded}>
        {ai && (
          <>
            <Block title="Supporting">
              {d.supportingEvidence.length === 0 && <Typography variant="body2" color="text.secondary">None cited</Typography>}
              {d.supportingEvidence.map((s) => <StatementLine key={s.id} s={s} icon={<CheckCircleOutlineIcon sx={{ fontSize: 15, color: '#2e7d32' }} />} />)}
            </Block>
            <Block title="Contradicting">
              {d.contradictingEvidence.length === 0 && <Typography variant="body2" color="text.secondary">None identified</Typography>}
              {d.contradictingEvidence.map((s) => <StatementLine key={s.id} s={s} icon={<HighlightOffIcon sx={{ fontSize: 15, color: '#c62828' }} />} />)}
            </Block>
            <Block title="Missing information">
              {d.missingInformation.length === 0 && <Typography variant="body2" color="text.secondary">None listed</Typography>}
              {d.missingInformation.map((s) => <StatementLine key={s.id} s={s} icon={<HelpOutlineIcon sx={{ fontSize: 15, color: '#b26a00' }} />} />)}
            </Block>
            <Block title="Suggested investigations (suggestions only — never orders)">
              {d.suggestedInvestigations.length === 0 && <Typography variant="body2" color="text.secondary">None suggested</Typography>}
              {d.suggestedInvestigations.map((i) => {
                const workflow = investigations.find((w) => w.sourceSuggestionId === i.id)
                return (
                  <Stack key={i.id} direction="row" spacing={0.75} sx={{ alignItems: 'center', py: 0.25, flexWrap: 'wrap' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{i.name}</Typography>
                    <Chip label={workflow ? workflow.status : 'SUGGESTED'} variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />
                    <SupportBadge support={i.support} />
                    <EvidenceChips evidenceKeys={i.evidenceKeys.filter((k) => k.startsWith('K'))} factKeys={i.evidenceKeys.filter((k) => k.startsWith('F'))} />
                    <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>{i.reason}</Typography>
                    {!workflow && <FeedbackButtons type="INVESTIGATION" id={i.id} label={i.name} compact />}
                  </Stack>
                )
              })}
            </Block>
            {d.distinguishingFeatures && <Block title="What would help distinguish it"><Typography variant="body2">{d.distinguishingFeatures}</Typography></Block>}
            {d.reasoningSummary && <Block title="Reasoning summary"><Typography variant="body2">{d.reasoningSummary}</Typography></Block>}
          </>
        )}
        <Block title="Knowledge sources">
          {d.citations.length === 0
            ? <Typography variant="body2" color="warning.main">No validated knowledge citation</Typography>
            : <Stack spacing={0.25}>{d.citations.map((k) => {
              const ev = version?.evidence.find((e) => e.evidenceKey === k)
              return (
                <Stack key={k} direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                  <EvidenceChips evidenceKeys={[k]} />
                  <Typography variant="caption">{ev ? `${ev.documentTitle} (v${ev.documentVersion}${ev.section ? `, ${ev.section}` : ''})` : ''}</Typography>
                  {ev?.syntheticSource && <Chip label="synthetic demo source" sx={{ height: 16, fontSize: '0.58rem' }} />}
                </Stack>
              )
            })}</Stack>}
        </Block>
        {d.validationNotes.length > 0 && (
          <Box sx={{ mt: 0.5, p: 0.5, bgcolor: '#f5f5f5' }}>
            {d.validationNotes.map((n) => <Typography key={n} variant="caption" sx={{ display: 'block' }}>Validator: {n}</Typography>)}
          </Box>
        )}
        {d.rankBasis && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>Rank basis: {d.rankBasis}</Typography>}
      </Collapse>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 0.75, flexWrap: 'wrap', rowGap: 0.5 }}>
        {ai && <FeedbackButtons type="DIFFERENTIAL" id={d.id} label={d.conditionName} />}
        <Button onClick={() => setExpanded((e) => !e)}>{expanded ? 'Collapse' : 'Expand reasoning'}</Button>
        <Button onClick={() => selectDifferential(d.id)} variant={selected ? 'contained' : 'text'}>Explain</Button>
      </Stack>
    </Box>
  )
}
