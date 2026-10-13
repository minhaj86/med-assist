import CheckIcon from '@mui/icons-material/Check'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined'
import { Box, CircularProgress, Stack, Typography } from '@mui/material'
import type { AssessmentRun, AssessmentStage } from '../../../api/types'

const LABELS: Partial<Record<AssessmentStage, string>> = {
  QUEUED: 'Assessment started',
  VALIDATING_INPUT: 'Validating clinical information',
  NORMALIZING_TERMINOLOGY: 'Normalizing terminology',
  RUNNING_RULES: 'Running safety rules',
  CALCULATING_SCORES: 'Calculating scores',
  SEARCHING_KNOWLEDGE: 'Searching knowledge base',
  GENERATING_CANDIDATES: 'Generating candidates',
  GENERATING_REASONING: 'Generating reasoning',
  VALIDATING_RESPONSE: 'Validating response',
  SAVING: 'Saving immutable version',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
}

/** Shows the stages the server has actually reached - no synthetic percentages. */
export function ProgressStages({ run }: { run: AssessmentRun }) {
  const done = run.status === 'COMPLETED' || run.status === 'FAILED'
  return (
    <Box aria-live="polite" aria-label="Assessment progress" sx={{ p: 1, bgcolor: '#f8fafc', border: '1px solid #e0e6ec', mb: 1 }}>
      <Stack spacing={0.25}>
        {run.stages.map((s, i) => {
          const last = i === run.stages.length - 1
          const failed = s.stage === 'FAILED'
          return (
            <Stack key={`${s.stage}-${i}`} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              {failed ? <ErrorOutlineIcon color="error" sx={{ fontSize: 16 }} />
                : last && !done ? <CircularProgress size={13} /> : <CheckIcon color="success" sx={{ fontSize: 16 }} />}
              <Typography variant="body2" sx={{ fontWeight: last ? 600 : 400 }}>{LABELS[s.stage] ?? s.stage}</Typography>
              <Typography variant="caption" color="text.secondary">{new Date(s.at).toLocaleTimeString()}</Typography>
            </Stack>
          )
        })}
      </Stack>
      {run.failureReason && <Typography variant="body2" color="error" sx={{ mt: 0.5 }}>{run.failureReason}</Typography>}
    </Box>
  )
}
