import { Button, ButtonGroup, Menu, MenuItem, Tooltip } from '@mui/material'
import { useState } from 'react'
import { useFeedback } from '../../../api/hooks'
import type { FeedbackAction, SuggestionType } from '../../../api/types'
import { ReasonDialog } from '../../../components/ReasonDialog'
import { useWorkstation } from '../WorkstationContext'

/** Clinician decision on an AI suggestion. Feedback is stored for evaluation only - never for automatic training. */
export function FeedbackButtons({ type, id, label, compact = false }: { type: SuggestionType; id: string; label: string; compact?: boolean }) {
  const { version, encounterId, readOnly } = useWorkstation()
  const feedback = useFeedback(version?.id ?? '', encounterId)
  const [rejecting, setRejecting] = useState(false)
  const [moreAnchor, setMoreAnchor] = useState<HTMLElement | null>(null)
  const disabled = readOnly || !version || version.workflowStatus === 'FINALIZED' || feedback.isPending
  const send = (action: FeedbackAction, reason?: string) =>
    feedback.mutate({ suggestionType: type, suggestionId: id, action, reason })
  return (
    <>
      <ButtonGroup size="small" variant="outlined" aria-label={`Feedback on ${label}`} disabled={disabled}>
        <Tooltip title="Accept this suggestion for clinical consideration"><Button color="success" onClick={() => send('ACCEPTED')}>Accept</Button></Tooltip>
        <Button color="error" onClick={() => setRejecting(true)}>Reject</Button>
        {!compact && <Button onClick={() => send('NEEDS_MORE_INFORMATION')}>Needs more evidence</Button>}
        <Button onClick={(e) => setMoreAnchor(e.currentTarget)} aria-label="More feedback options">…</Button>
      </ButtonGroup>
      <Menu open={!!moreAnchor} anchorEl={moreAnchor} onClose={() => setMoreAnchor(null)}>
        <MenuItem onClick={() => { send('UNCERTAIN'); setMoreAnchor(null) }}>Uncertain</MenuItem>
        <MenuItem onClick={() => { send('NOT_RELEVANT'); setMoreAnchor(null) }}>Not relevant</MenuItem>
        {compact && <MenuItem onClick={() => { send('NEEDS_MORE_INFORMATION'); setMoreAnchor(null) }}>Needs more information</MenuItem>}
      </Menu>
      <ReasonDialog open={rejecting} title={`Reject: ${label}`} required={false} confirmLabel="Reject"
                    onCancel={() => setRejecting(false)} onConfirm={(reason) => { send('REJECTED', reason || undefined); setRejecting(false) }} />
    </>
  )
}
