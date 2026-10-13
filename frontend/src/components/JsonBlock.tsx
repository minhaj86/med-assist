import { Box } from '@mui/material'

/** Read-only JSON rendering for admin/debug screens. */
export function JsonBlock({ value, maxHeight = 260 }: { value: unknown; maxHeight?: number }) {
  return (
    <Box component="pre" sx={{ m: 0, p: 1, bgcolor: '#f5f7f9', border: '1px solid #e0e6ec', fontSize: '0.72rem', overflow: 'auto', maxHeight }}>
      {JSON.stringify(value, null, 2)}
    </Box>
  )
}
