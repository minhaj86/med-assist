import SendIcon from '@mui/icons-material/Send'
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined'
import { Alert, Box, Button, Chip, Collapse, Stack, TextField, Tooltip, Typography } from '@mui/material'
import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../../api/client'
import { useAskAssistant, useConversation } from '../../../api/hooks'
import type { AssistantAnswer, AssistantMessage, SupportStatus } from '../../../api/types'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { DISCLAIMER } from '../../../components/SafetyDisclaimer'
import { fmtTime } from '../../../components/format'
import { useShortcuts } from '../../../hooks/useShortcuts'
import { AI_ACCENT } from '../../../theme'
import { SupportBadge } from '../EvidenceChips'
import { useWorkstation } from '../WorkstationContext'

type AnswerEvidence = AssistantAnswer['evidence']
type Statement = AssistantAnswer['statements'][number]

const EXAMPLES = [
  'What information would most affect the differential?',
  'Which recorded findings argue against the leading consideration?',
  'Summarise the safety alerts for this encounter.',
]

function KeyChips({ statement, evidence, onOpen }: { statement: Statement; evidence?: AnswerEvidence; onOpen: (k: string) => void }) {
  return (
    <>
      {statement.factKeys.map((k) => (
        <Tooltip key={k} title="Recorded clinical fact cited by the assistant">
          <Chip label={k} variant="outlined" sx={{ height: 17, fontSize: '0.6rem', fontFamily: 'monospace', ml: 0.5 }} />
        </Tooltip>
      ))}
      {statement.evidenceKeys.map((k) => {
        const ev = evidence?.find((e) => e.evidenceKey === k)
        return (
          <Tooltip key={k} title={ev ? `${ev.documentTitle}${ev.section ? ` · ${ev.section}` : ''}` : 'Knowledge passage retrieved for this answer'}>
            <Chip label={k} color="secondary" variant="outlined" onClick={ev ? () => onOpen(k) : undefined}
                  sx={{ height: 17, fontSize: '0.6rem', fontFamily: 'monospace', ml: 0.5 }} />
          </Tooltip>
        )
      })}
    </>
  )
}

function AssistantBubble({ message, evidence, readOnly }: { message: AssistantMessage; evidence?: AnswerEvidence; readOnly: boolean }) {
  const { reviewProposal } = useWorkstation()
  const [open, setOpen] = useState<string>()
  const s = message.structured
  const passage = evidence?.find((e) => e.evidenceKey === open)
  return (
    <Box sx={{ p: 1, mb: 1, border: `1px dashed ${AI_ACCENT}`, bgcolor: '#faf8ff' }}>
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mb: 0.5 }}>
        <SmartToyOutlinedIcon sx={{ fontSize: 15, color: AI_ACCENT }} />
        <Typography variant="caption" sx={{ color: AI_ACCENT, fontWeight: 700 }}>AI assistant · {fmtTime(message.createdAt)}</Typography>
        {s?.insufficientInformation && <Chip label="insufficient information" sx={{ height: 17, fontSize: '0.58rem', bgcolor: '#fff3e0' }} />}
      </Stack>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{message.content}</Typography>
      {(s?.statements ?? []).map((st, i) => (
        <Typography key={i} variant="body2" sx={{ mt: 0.5, color: st.support === 'WITHHELD' ? 'text.secondary' : undefined }}>
          • {st.text}<KeyChips statement={st} evidence={evidence} onOpen={(k) => setOpen(open === k ? undefined : k)} />
          <SupportBadge support={st.support as SupportStatus} />
        </Typography>
      ))}
      <Collapse in={!!passage}>
        {passage && (
          <Box sx={{ mt: 0.5, p: 0.75, bgcolor: '#e0f2f1', border: '1px solid #b2dfdb' }}>
            <Typography variant="caption" sx={{ fontWeight: 700 }}>{passage.evidenceKey} · {passage.documentTitle}{passage.section ? ` · ${passage.section}` : ''}</Typography>
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{passage.excerpt}</Typography>
          </Box>
        )}
      </Collapse>
      {(s?.proposedUpdates ?? []).length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" sx={{ fontWeight: 700 }}>Proposed record updates (not saved)</Typography>
          {s!.proposedUpdates!.map((p) => (
            <Stack key={p.proposalReference} direction="row" spacing={1} sx={{ alignItems: 'center', mt: 0.25 }}>
              <Typography variant="body2" sx={{ flex: 1 }}>
                <Chip label={p.category === 'SYMPTOM' ? 'Symptom' : 'History'} sx={{ height: 17, fontSize: '0.58rem', mr: 0.5 }} />
                {p.description} <Typography component="span" variant="caption" color="text.secondary">— {p.rationale}</Typography>
              </Typography>
              {!readOnly && (
                <Button onClick={() => reviewProposal({ category: p.category, description: p.description, reference: p.proposalReference })}>
                  Review &amp; record
                </Button>
              )}
            </Stack>
          ))}
        </Box>
      )}
      {(s?.followUpQuestions ?? []).length > 0 && (
        <Box sx={{ mt: 0.75 }}>
          <Typography variant="caption" sx={{ fontWeight: 700 }}>Questions to consider</Typography>
          {s!.followUpQuestions!.map((q) => <Typography key={q} variant="body2">? {q}</Typography>)}
        </Box>
      )}
    </Box>
  )
}

/** Conversational assistant bound to the current encounter. It never modifies patient data. */
export function AssistantPanel() {
  const { encounterId, readOnly } = useWorkstation()
  const conversation = useConversation(encounterId)
  const ask = useAskAssistant(encounterId)
  const [question, setQuestion] = useState('')
  const [evidenceByMessage, setEvidenceByMessage] = useState<Record<string, AnswerEvidence>>({})
  const [rejected, setRejected] = useState<string>()
  const inputRef = useRef<HTMLInputElement>(null)
  const endRef = useRef<HTMLDivElement>(null)
  const messages = conversation.data ?? []
  const conversationId = messages.at(-1)?.conversationId

  useEffect(() => { endRef.current?.scrollIntoView?.({ block: 'end' }) }, [messages.length])

  const submit = () => {
    const q = question.trim()
    if (!q || ask.isPending) return
    setRejected(undefined)
    ask.mutate({ question: q, conversationId }, {
      onSuccess: (answer) => {
        setQuestion('')
        if (answer.messageId) setEvidenceByMessage((m) => ({ ...m, [answer.messageId!]: answer.evidence }))
        if (answer.status === 'OUTPUT_REJECTED') setRejected(answer.message)
      },
    })
  }
  const shortcuts = useMemo(() => [{ keys: 'alt+a', handler: () => inputRef.current?.focus(), allowInInputs: true }], [])
  useShortcuts(shortcuts)
  const unavailable = ask.error instanceof ApiError && ask.error.status === 503

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Alert severity="info" icon={<SmartToyOutlinedIcon />} sx={{ mb: 1, py: 0 }}>
        {DISCLAIMER} The assistant sees this encounter’s pseudonymized data and cannot change the record.
      </Alert>
      <Box sx={{ flex: 1, overflowY: 'auto', minHeight: 120 }}>
        {messages.length === 0 && (
          <Box>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>Ask about this encounter, for example:</Typography>
            {EXAMPLES.map((e) => (
              <Chip key={e} label={e} onClick={() => setQuestion(e)} variant="outlined" sx={{ mb: 0.5, mr: 0.5, height: 'auto', '& .MuiChip-label': { whiteSpace: 'normal', py: 0.25 } }} />
            ))}
          </Box>
        )}
        {messages.map((m) => m.role === 'CLINICIAN'
          ? (
            <Box key={m.id} sx={{ p: 1, mb: 1, bgcolor: '#eef3f8', borderLeft: '3px solid #1f4e79' }}>
              <Typography variant="caption" color="text.secondary">You · {fmtTime(m.createdAt)}</Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{m.content}</Typography>
            </Box>
          )
          : <AssistantBubble key={m.id} message={m} evidence={evidenceByMessage[m.id]} readOnly={readOnly} />)}
        <div ref={endRef} />
      </Box>
      {unavailable && (
        <Alert severity="info" sx={{ mb: 1 }}>
          The AI assistant is temporarily unavailable. Safety rules, data entry and assessments without AI continue to work.
        </Alert>
      )}
      {!unavailable && <ProblemAlert error={ask.error} title="Question not answered" />}
      {rejected && <Alert severity="info" sx={{ mb: 1 }}>{rejected}</Alert>}
      <Stack direction="row" spacing={1} component="form" onSubmit={(e) => { e.preventDefault(); submit() }}>
        <TextField inputRef={inputRef} fullWidth multiline maxRows={4} placeholder="Ask the assistant (Alt+A to focus, Ctrl+Enter to send)"
                   value={question} onChange={(e) => setQuestion(e.target.value)} disabled={readOnly}
                   onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); submit() } }}
                   slotProps={{ htmlInput: { maxLength: 2000, 'aria-label': 'Question for the AI assistant' } }} />
        <Button type="submit" variant="contained" disabled={!question.trim() || ask.isPending || readOnly} aria-label="Send question"><SendIcon fontSize="small" /></Button>
      </Stack>
      {ask.isPending && <Typography variant="caption" color="text.secondary">Retrieving knowledge and generating a validated answer…</Typography>}
    </Box>
  )
}
