import { Alert, Box, Chip, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { StatementView } from '../../../api/types'
import { humanize } from '../../../components/format'
import { EvidenceChips, SupportBadge } from '../EvidenceChips'
import { useWorkstation } from '../WorkstationContext'

function Question({ q, children }: { q: string; children: ReactNode }) {
  return (
    <Box sx={{ mb: 1.25 }}>
      <Typography variant="overline" sx={{ color: '#1f4e79', fontWeight: 700 }}>{q}</Typography>
      <Box>{children}</Box>
    </Box>
  )
}

function Statements({ items, empty }: { items: StatementView[]; empty: string }) {
  if (items.length === 0) return <Typography variant="body2" color="text.secondary">{empty}</Typography>
  return (
    <>
      {items.map((s) => (
        <Typography key={s.id} variant="body2" sx={{ py: 0.15 }}>
          • {s.text}<EvidenceChips factKeys={s.factKeys} evidenceKeys={s.evidenceKeys} /><SupportBadge support={s.support} />
        </Typography>
      ))}
    </>
  )
}

/** Explainability for the selected differential: concise clinician-facing summaries, never hidden chain-of-thought. */
export function ExplainPanel() {
  const { version, selectedDifferentialId } = useWorkstation()
  const d = version?.differentials.find((x) => x.id === selectedDifferentialId)
  if (!version) return <Alert severity="info">Run an assessment to see explanations.</Alert>
  if (!d) {
    return (
      <Box>
        <Alert severity="info" sx={{ mb: 1 }}>Select “Explain” on a differential card to see why it is being considered.</Alert>
        <Typography variant="subtitle2">Clinical facts used in V{version.versionNumber}</Typography>
        <Typography variant="caption" color="text.secondary">F-keys are pseudonymized facts sent to rules/AI. No names or identifiers are included.</Typography>
        {version.facts.map((f) => (
          <Stack key={f.key} direction="row" spacing={0.5} sx={{ alignItems: 'baseline', py: 0.15 }}>
            <Chip label={f.key} variant="outlined" sx={{ height: 17, fontSize: '0.6rem', fontFamily: 'monospace' }} />
            <Typography variant="body2" sx={{ flex: 1 }}>{f.description}</Typography>
            {f.provenance && <Typography variant="caption" color="text.secondary">{humanize(f.provenance)}</Typography>}
          </Stack>
        ))}
      </Box>
    )
  }
  const cited = Array.from(new Set([
    ...d.citations,
    ...d.supportingEvidence.flatMap((s) => s.evidenceKeys),
    ...d.contradictingEvidence.flatMap((s) => s.evidenceKeys),
  ]))
  const sources = version.evidence.filter((e) => cited.includes(e.evidenceKey))
  return (
    <Box>
      <Typography variant="subtitle1">{d.conditionName}</Typography>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        {d.method === 'AI_REASONED' ? 'AI-generated summary, validated against recorded facts and retrieved knowledge.' : 'Linked from the knowledge base; not assessed by AI.'}
        {d.rankBasis ? ` Display order: ${d.rankBasis}.` : ''}
      </Typography>
      <Question q="Why considered?">
        <Typography variant="body2">{d.whyConsidered ?? d.reasoningSummary ?? 'Insufficient information to reliably assess this possibility.'}</Typography>
      </Question>
      <Question q="What supports it?"><Statements items={d.supportingEvidence} empty="No supporting findings cited." /></Question>
      <Question q="What argues against it?"><Statements items={d.contradictingEvidence} empty="None identified from the recorded information." /></Question>
      <Question q="What information is missing?"><Statements items={d.missingInformation} empty="None listed." /></Question>
      <Question q="What would help distinguish it?">
        <Typography variant="body2">{d.distinguishingFeatures ?? '—'}</Typography>
        {d.suggestedInvestigations.length > 0 && (
          <Typography variant="body2" sx={{ mt: 0.5 }}>Suggested investigations: {d.suggestedInvestigations.map((i) => i.name).join(', ')}</Typography>
        )}
      </Question>
      <Question q="What knowledge sources support the reasoning?">
        {sources.length === 0 && <Typography variant="body2" color="warning.main">No validated knowledge-base citation. Treat this reasoning as ungrounded.</Typography>}
        {sources.map((e) => (
          <Typography key={e.evidenceKey} variant="body2" sx={{ py: 0.15 }}>
            <EvidenceChips evidenceKeys={[e.evidenceKey]} /> {e.documentTitle}{e.documentVersion ? ` (v${e.documentVersion})` : ''}{e.section ? ` · ${e.section}` : ''}
            {e.syntheticSource && <Chip label="synthetic demo source" sx={{ height: 16, fontSize: '0.58rem', ml: 0.5 }} />}
          </Typography>
        ))}
      </Question>
      {d.reasoningSummary && d.whyConsidered && (
        <Question q="Reasoning summary"><Typography variant="body2">{d.reasoningSummary}</Typography></Question>
      )}
      {d.validationNotes.length > 0 && (
        <Alert severity="warning" variant="outlined" sx={{ mt: 1 }}>
          <strong>Validator notes</strong>
          {d.validationNotes.map((n) => <Typography key={n} variant="body2">• {n}</Typography>)}
        </Alert>
      )}
    </Box>
  )
}
