import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import type { ReactNode } from 'react'

export function ConfirmDialog({ open, title, children, confirmLabel = 'Confirm', destructive = false, pending = false, onCancel, onConfirm }: {
  open: boolean
  title: string
  children?: ReactNode
  confirmLabel?: string
  destructive?: boolean
  pending?: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>{typeof children === 'string' ? <Typography variant="body2">{children}</Typography> : children}</DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" color={destructive ? 'error' : 'primary'} disabled={pending} onClick={onConfirm}>{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  )
}
