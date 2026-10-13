import { Chip, Tooltip } from '@mui/material'
import type { ChipProps } from '@mui/material'
import type { AlertOrigin, ClinicalSeverity, ConceptVerificationStatus, DataProvenance, Provenance } from '../api/types'
import { AI_ACCENT, originColor, severityColor } from '../theme'

export function SyntheticChip() {
  return (
    <Tooltip title="Fictional patient for software demonstration only">
      <Chip label="SYNTHETIC TEST DATA" sx={{ bgcolor: '#ede7f6', color: '#4527a0', fontWeight: 700, fontSize: '0.65rem' }} />
    </Tooltip>
  )
}

export function SeverityChip({ severity, ...rest }: { severity: ClinicalSeverity } & ChipProps) {
  return <Chip label={severity} sx={{ bgcolor: severityColor[severity], color: '#fff', fontWeight: 700, fontSize: '0.65rem' }} {...rest} />
}

const originLabel: Record<AlertOrigin, string> = {
  RULE: 'RULE',
  CLINICAL_SCORE: 'CLINICAL SCORE',
  AI: 'AI-GENERATED',
  KNOWLEDGE_BASE: 'KNOWLEDGE BASE',
  CLINICIAN: 'CLINICIAN',
}

/** Always shows where an alert or suggestion came from. */
export function OriginChip({ origin }: { origin: AlertOrigin }) {
  const ai = origin === 'AI'
  return (
    <Chip variant={ai ? 'outlined' : 'filled'} label={originLabel[origin]}
          sx={{ fontWeight: 700, fontSize: '0.62rem', ...(ai ? { borderColor: AI_ACCENT, color: AI_ACCENT, borderStyle: 'dashed' }
            : { bgcolor: originColor[origin], color: '#fff' }) }} />
  )
}

export function DemoRuleChip() {
  return (
    <Tooltip title="Illustrative example threshold - not validated clinical guidance and not clinically approved">
      <Chip label="DEMONSTRATION RULE" variant="outlined" sx={{ fontSize: '0.6rem', borderColor: '#9e9e9e', color: '#616161' }} />
    </Tooltip>
  )
}

const provenanceLabel: Record<DataProvenance, string> = {
  CLINICIAN: 'Clinician',
  FHIR: 'FHIR import',
  LAB_IMPORT: 'Lab import',
  PATIENT_REPORTED: 'Patient-reported',
  DEVICE: 'Device',
  AI_INFERRED: 'AI-proposed, clinician-confirmed',
}

export function ProvenanceChip({ provenance }: { provenance: Provenance }) {
  const ai = provenance.source === 'AI_INFERRED'
  return (
    <Tooltip title={`Recorded by ${provenance.recordedBy} at ${new Date(provenance.recordedAt).toLocaleString()}`
      + (provenance.confirmedBy ? ` · confirmed by ${provenance.confirmedBy}` : '')
      + (provenance.sourceDetail ? ` · ${provenance.sourceDetail}` : '')}>
      <Chip label={provenanceLabel[provenance.source]} variant="outlined"
            sx={{ height: 18, fontSize: '0.6rem', ...(ai ? { borderColor: AI_ACCENT, color: AI_ACCENT } : {}) }} />
    </Tooltip>
  )
}

export function CodeChip({ code, system, verification }: { code?: string; system?: string; verification: ConceptVerificationStatus }) {
  if (!code) {
    return (
      <Tooltip title="No verified terminology code (free-text concept)">
        <Chip label="uncoded" variant="outlined" sx={{ height: 18, fontSize: '0.6rem', color: 'text.secondary' }} />
      </Tooltip>
    )
  }
  const verified = verification === 'VERIFIED_TERMINOLOGY_SERVICE' || verification === 'CURATED_REFERENCE'
  return (
    <Tooltip title={`${system} ${code} · ${verification.replaceAll('_', ' ').toLowerCase()}`}>
      <Chip label={`${system === 'LOINC' ? 'LOINC' : system} ${code}${verified ? '' : ' (unverified)'}`} variant="outlined"
            sx={{ height: 18, fontSize: '0.6rem' }} />
    </Tooltip>
  )
}
