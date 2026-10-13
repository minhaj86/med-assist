import { Box, Button, Chip, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useAddInvestigation, useInvestigationStatus, useInvestigations } from '../../../api/hooks'
import type { InvestigationStatus } from '../../../api/types'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { useWorkstation } from '../WorkstationContext'

const NEXT: Record<InvestigationStatus, InvestigationStatus[]> = {
  SUGGESTED: ['ACCEPTED', 'REJECTED'],
  ACCEPTED: ['ORDERED', 'REJECTED'],
  ORDERED: ['COMPLETED'],
  REJECTED: ['ACCEPTED'],
  COMPLETED: [],
}

const ACTION_LABEL: Record<InvestigationStatus, string> = {
  SUGGESTED: 'Suggest', ACCEPTED: 'Accept', REJECTED: 'Reject', ORDERED: 'Mark ordered', COMPLETED: 'Mark completed',
}

export function InvestigationsCard() {
  const { encounterId, readOnly } = useWorkstation()
  const { data } = useInvestigations(encounterId)
  const change = useInvestigationStatus(encounterId)
  const add = useAddInvestigation(encounterId)
  const [name, setName] = useState('')
  const [status, setStatus] = useState<InvestigationStatus>('ACCEPTED')
  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle1">Investigations</Typography>
      <Typography variant="caption" color="text.secondary">
        Workflow of investigations you accepted. MedAssist never places orders: “ordered” records an action you took in the clinical system.
      </Typography>
      <ProblemAlert error={change.error ?? add.error} />
      {(data ?? []).map((i) => (
        <Stack key={i.id} direction="row" spacing={1} sx={{ alignItems: 'center', py: 0.5, borderBottom: '1px dashed #eceff1' }}>
          <Typography variant="body2" sx={{ fontWeight: 600, flex: 1 }}>{i.name}</Typography>
          <Chip label={`proposed by ${i.proposedBy.toLowerCase()}`} variant="outlined" sx={{ height: 18, fontSize: '0.6rem' }} />
          <Chip label={i.status} color={i.status === 'COMPLETED' ? 'success' : i.status === 'REJECTED' ? 'default' : 'primary'} sx={{ height: 18, fontSize: '0.6rem' }} />
          {!readOnly && NEXT[i.status].map((next) => (
            <Button key={next} onClick={() => change.mutate({ id: i.id, status: next })} disabled={change.isPending}>{ACTION_LABEL[next]}</Button>
          ))}
        </Stack>
      ))}
      {data?.length === 0 && <Typography variant="body2" color="text.secondary">No investigations in the workflow yet.</Typography>}
      {!readOnly && (
        <Stack direction="row" spacing={1} sx={{ mt: 1 }} component="form"
               onSubmit={(e) => { e.preventDefault(); if (name.trim()) add.mutate({ name: name.trim(), status }, { onSuccess: () => setName('') }) }}>
          <TextField label="Add investigation" value={name} onChange={(e) => setName(e.target.value)} slotProps={{ htmlInput: { maxLength: 300 } }} />
          <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value as InvestigationStatus)} sx={{ width: 160 }}>
            <MenuItem value="ACCEPTED">Accepted</MenuItem><MenuItem value="ORDERED">Ordered</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" disabled={!name.trim() || add.isPending}>Add</Button>
        </Stack>
      )}
    </Box>
  )
}
