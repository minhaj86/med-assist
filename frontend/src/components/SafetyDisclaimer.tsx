import { Box, Typography } from '@mui/material'
import GppMaybeOutlinedIcon from '@mui/icons-material/GppMaybeOutlined'

export const DISCLAIMER = 'AI-generated clinical decision support. Not a substitute for professional clinical judgment.'

/** Persistent product-level disclaimer shown on every screen. */
export function SafetyDisclaimer() {
  return (
    <Box role="note" aria-label="Clinical safety disclaimer"
         sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 0.5, bgcolor: '#fff4e5', borderBottom: '1px solid #f0c27b' }}>
      <GppMaybeOutlinedIcon fontSize="small" sx={{ color: '#8a5300' }} />
      <Typography variant="caption" sx={{ color: '#5f3b00', fontWeight: 600 }}>
        {DISCLAIMER} The clinician remains responsible for all medical decisions.
      </Typography>
    </Box>
  )
}
