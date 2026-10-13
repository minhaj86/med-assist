import { Chip, Stack, Tooltip } from '@mui/material'
import type { SupportStatus } from '../../api/types'
import { useWorkstation } from './WorkstationContext'

/** Clickable F-/K-key chips. F = recorded clinical fact; K = retrieved knowledge passage. */
export function EvidenceChips({ factKeys = [], evidenceKeys = [] }: { factKeys?: string[]; evidenceKeys?: string[] }) {
  const { version, showEvidence } = useWorkstation()
  if (factKeys.length === 0 && evidenceKeys.length === 0) return null
  return (
    <Stack direction="row" spacing={0.5} sx={{ display: 'inline-flex', flexWrap: 'wrap', ml: 0.5 }}>
      {factKeys.map((k) => {
        const fact = version?.facts.find((f) => f.key === k)
        return (
          <Tooltip key={k} title={fact ? `Clinical fact: ${fact.description}` : 'Clinical fact'}>
            <Chip label={k} variant="outlined" sx={{ height: 17, fontSize: '0.6rem', fontFamily: 'monospace' }} />
          </Tooltip>
        )
      })}
      {evidenceKeys.map((k) => {
        const ev = version?.evidence.find((e) => e.evidenceKey === k)
        return (
          <Tooltip key={k} title={ev ? `Knowledge: ${ev.documentTitle}${ev.section ? ` · ${ev.section}` : ''}` : 'Knowledge passage'}>
            <Chip label={k} onClick={() => showEvidence(k)} color="secondary" variant="outlined"
                  sx={{ height: 17, fontSize: '0.6rem', fontFamily: 'monospace' }} />
          </Tooltip>
        )
      })}
    </Stack>
  )
}

export function SupportBadge({ support }: { support: SupportStatus }) {
  if (support === 'SUPPORTED' || support === 'NOT_APPLICABLE') return null
  const label = support === 'UNSUPPORTED' ? 'unsupported' : 'withheld'
  const title = support === 'UNSUPPORTED'
    ? 'No valid citation to the provided facts or retrieved knowledge - treat as unsupported'
    : 'Removed by the safety validator'
  return (
    <Tooltip title={title}>
      <Chip label={label} sx={{ height: 17, fontSize: '0.6rem', ml: 0.5, bgcolor: support === 'UNSUPPORTED' ? '#fff3e0' : '#eceff1',
        color: support === 'UNSUPPORTED' ? '#8a4b00' : '#455a64' }} />
    </Tooltip>
  )
}
