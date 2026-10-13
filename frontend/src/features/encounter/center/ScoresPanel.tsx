import { Box, Chip, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { ScoreResult } from '../../../api/types'
import { EvidenceChips } from '../EvidenceChips'

export function ScoresPanel({ scores }: { scores: ScoreResult[] }) {
  return (
    <Box>
      {scores.map((s) => (
        <Box key={s.scoreId} sx={{ mb: 1 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{s.name}</Typography>
            <Chip label={s.score === undefined || s.score === null ? 'not calculated' : `${s.score} / ${s.maxScore}`} />
            <Chip label={`v${s.version}`} variant="outlined" />
            {s.demonstrationOnly && <Chip label="DEMONSTRATION — not a validated score" variant="outlined" sx={{ fontSize: '0.6rem' }} />}
          </Stack>
          <Typography variant="body2" sx={{ mt: 0.5 }}>{s.interpretation}</Typography>
          {s.calculationDetails.length > 0 && (
            <Table sx={{ mt: 0.5 }}>
              <TableHead><TableRow><TableCell>Variable</TableCell><TableCell>Value</TableCell><TableCell>Points</TableCell><TableCell>Rationale</TableCell></TableRow></TableHead>
              <TableBody>
                {s.calculationDetails.map((c) => (
                  <TableRow key={c.variable}><TableCell>{c.variable}<EvidenceChips factKeys={[c.factKey]} /></TableCell><TableCell>{c.value}</TableCell>
                    <TableCell>{c.points}</TableCell><TableCell>{c.rationale}</TableCell></TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {s.missingVariables.length > 0 && <Typography variant="caption" color="warning.main">Missing: {s.missingVariables.join(', ')}</Typography>}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>Reference: {s.reference}</Typography>
        </Box>
      ))}
    </Box>
  )
}
