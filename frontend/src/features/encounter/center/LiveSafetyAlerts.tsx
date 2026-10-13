import { Alert, Box, LinearProgress, Stack, Typography } from '@mui/material'
import { useSafetyCheck } from '../../../api/hooks'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { fmtTime } from '../../../components/format'
import { useWorkstation } from '../WorkstationContext'
import { AlertCard } from './AlertCards'

/** Deterministic rules and scores evaluated on the current data. Independent of the AI provider. */
export function LiveSafetyAlerts() {
  const { encounterId } = useWorkstation()
  const { data, isFetching, error } = useSafetyCheck(encounterId)
  return (
    <Box sx={{ mb: 2 }} aria-label="Live safety alerts">
      <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', mb: 0.5 }}>
        <Typography variant="subtitle1">Safety alerts</Typography>
        <Typography variant="caption" color="text.secondary">
          Deterministic rules &amp; scores on current data{data ? ` · ${data.rulesEvaluated} rules evaluated at ${fmtTime(data.evaluatedAt)}` : ''}
          {data && data.disabledRules.length > 0 ? ` · ${data.disabledRules.length} disabled by administrator` : ''}
        </Typography>
      </Stack>
      {isFetching && <LinearProgress sx={{ mb: 0.5 }} />}
      <ProblemAlert error={error} title="Safety rules could not be evaluated" />
      {data && data.alerts.length === 0 && (
        <Alert severity="success" variant="outlined">No rule-based safety alerts for the recorded data. This does not exclude serious illness.</Alert>
      )}
      {data?.alerts.map((a, i) => <AlertCard key={`${a.sourceReference}-${i}`} alert={a} />)}
    </Box>
  )
}
