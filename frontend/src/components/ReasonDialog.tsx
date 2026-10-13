import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material'
import { useState } from 'react'

/** Asks for a reason (e.g. "entered in error", rejecting a suggestion). */
export function ReasonDialog({ open, title, description, required = true, confirmLabel = 'Confirm', onCancel, onConfirm }: {
  open: boolean
  title: string
  description?: string
  required?: boolean
  confirmLabel?: string
  onCancel: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  const submit = () => {
    if (required && !reason.trim()) return
    onConfirm(reason.trim())
    setReason('')
  }
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {description && <Typography variant="body2" sx={{ mb: 1 }}>{description}</Typography>}
        <TextField autoFocus label={required ? 'Reason (required)' : 'Reason (optional)'} value={reason} multiline minRows={2}
                   onChange={(e) => setReason(e.target.value)} slotProps={{ htmlInput: { maxLength: 500 } }}
                   onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit() }} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={required && !reason.trim()}>{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  )
}
