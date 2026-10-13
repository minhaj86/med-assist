import { Chip, Tooltip } from '@mui/material'
import type { ApprovalStatus, IngestionStatus } from '../../../api/types'

export function IngestionChip({ status, error }: { status: IngestionStatus; error?: string }) {
  const color = status === 'INDEXED' ? 'success' : status === 'FAILED' ? 'error' : 'info'
  const chip = <Chip label={status} color={color} sx={{ fontSize: '0.62rem' }} />
  return error ? <Tooltip title={error}>{chip}</Tooltip> : chip
}

export function ApprovalChip({ status }: { status: ApprovalStatus }) {
  return <Chip label={status} variant="outlined" color={status === 'APPROVED' ? 'success' : status === 'RETIRED' ? 'default' : 'warning'} sx={{ fontSize: '0.62rem' }} />
}
