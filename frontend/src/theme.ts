import { createTheme } from '@mui/material/styles'
import type { AlertOrigin, ClinicalSeverity } from './api/types'

/** Severity colours are reserved for clinical severity; AI content uses a distinct (violet) accent. */
export const severityColor: Record<ClinicalSeverity, string> = {
  CRITICAL: '#b71c1c',
  URGENT: '#d84315',
  WARNING: '#b26a00',
  INFO: '#1565c0',
}

export const originColor: Record<AlertOrigin, string> = {
  RULE: '#37474f',
  CLINICAL_SCORE: '#455a64',
  AI: '#5e35b1',
  KNOWLEDGE_BASE: '#00695c',
  CLINICIAN: '#2e7d32',
}

export const AI_ACCENT = '#5e35b1'

export const theme = createTheme({
  palette: {
    primary: { main: '#1f4e79' },
    secondary: { main: '#00695c' },
    background: { default: '#f3f5f7', paper: '#ffffff' },
    error: { main: '#b71c1c' },
    warning: { main: '#b26a00' },
  },
  shape: { borderRadius: 4 },
  typography: {
    fontSize: 13,
    fontFamily: '"Inter", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h5: { fontSize: '1.15rem', fontWeight: 600 },
    h6: { fontSize: '1rem', fontWeight: 600 },
    subtitle1: { fontSize: '0.9rem', fontWeight: 600 },
    subtitle2: { fontSize: '0.8rem', fontWeight: 600, letterSpacing: 0.2 },
    body2: { fontSize: '0.8rem' },
    caption: { fontSize: '0.72rem' },
    overline: { fontSize: '0.68rem', fontWeight: 700, letterSpacing: 0.8, lineHeight: 1.6 },
  },
  components: {
    MuiButton: { defaultProps: { size: 'small', disableElevation: true }, styleOverrides: { root: { textTransform: 'none' } } },
    MuiIconButton: { defaultProps: { size: 'small' } },
    MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
    MuiFormControl: { defaultProps: { size: 'small' } },
    MuiSelect: { defaultProps: { size: 'small' } },
    MuiChip: { defaultProps: { size: 'small' } },
    MuiTable: { defaultProps: { size: 'small' } },
    // Tooltips describe controls; they never replace a control's visible accessible name.
    MuiTooltip: { defaultProps: { arrow: true, describeChild: true } },
    MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { border: '1px solid #dfe3e8' } } },
    MuiAppBar: { styleOverrides: { root: { border: 'none' } } },
    MuiCard: { defaultProps: { variant: 'outlined' } },
    MuiAccordion: { defaultProps: { disableGutters: true, elevation: 0 } },
    MuiTab: { styleOverrides: { root: { textTransform: 'none', minHeight: 36, minWidth: 72 } } },
    MuiTabs: { styleOverrides: { root: { minHeight: 36 } } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 600, backgroundColor: '#f6f8fa' } } },
  },
})
