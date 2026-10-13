import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined'
import HistoryEduOutlinedIcon from '@mui/icons-material/HistoryEduOutlined'
import KeyboardOutlinedIcon from '@mui/icons-material/KeyboardOutlined'
import LibraryBooksOutlinedIcon from '@mui/icons-material/LibraryBooksOutlined'
import LogoutIcon from '@mui/icons-material/Logout'
import MonitorHeartOutlinedIcon from '@mui/icons-material/MonitorHeartOutlined'
import PeopleAltOutlinedIcon from '@mui/icons-material/PeopleAltOutlined'
import PsychologyOutlinedIcon from '@mui/icons-material/PsychologyOutlined'
import RuleOutlinedIcon from '@mui/icons-material/RuleOutlined'
import TravelExploreOutlinedIcon from '@mui/icons-material/TravelExploreOutlined'
import {
  AppBar, Box, Chip, Dialog, DialogContent, DialogTitle, Divider, IconButton, List, ListItemButton, ListItemIcon,
  ListItemText, ListSubheader, Menu, MenuItem, Table, TableBody, TableCell, TableRow, Toolbar, Tooltip, Typography,
} from '@mui/material'
import { useMemo, useState, type ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import { NavLink, Outlet, useNavigate } from 'react-router'
import type { Role } from '../api/types'
import { useCurrentUser } from '../auth/useCurrentUser'
import { config } from '../config'
import { useShortcuts } from '../hooks/useShortcuts'
import { SafetyDisclaimer } from './SafetyDisclaimer'

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  roles: Role[]
  end?: boolean
}

const clinicalNav: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: <DashboardOutlinedIcon fontSize="small" />, roles: ['CLINICIAN'] },
  { to: '/patients', label: 'Patients', icon: <PeopleAltOutlinedIcon fontSize="small" />, roles: ['CLINICIAN'] },
]

const adminNav: NavItem[] = [
  { to: '/admin', label: 'Admin dashboard', icon: <AdminPanelSettingsOutlinedIcon fontSize="small" />, roles: ['ADMIN'], end: true },
  { to: '/admin/users', label: 'Users', icon: <GroupOutlinedIcon fontSize="small" />, roles: ['ADMIN'] },
  { to: '/admin/knowledge', label: 'Knowledge base', icon: <LibraryBooksOutlinedIcon fontSize="small" />, roles: ['ADMIN', 'KNOWLEDGE_MANAGER'], end: true },
  { to: '/admin/knowledge/search', label: 'Knowledge search', icon: <TravelExploreOutlinedIcon fontSize="small" />, roles: ['ADMIN', 'KNOWLEDGE_MANAGER'] },
  { to: '/admin/rules', label: 'Clinical rules', icon: <RuleOutlinedIcon fontSize="small" />, roles: ['ADMIN'] },
  { to: '/admin/ai', label: 'AI configuration', icon: <PsychologyOutlinedIcon fontSize="small" />, roles: ['ADMIN'] },
  { to: '/admin/audit', label: 'Audit', icon: <HistoryEduOutlinedIcon fontSize="small" />, roles: ['ADMIN'] },
  { to: '/admin/health', label: 'System health', icon: <MonitorHeartOutlinedIcon fontSize="small" />, roles: ['ADMIN'] },
]

const SHORTCUTS: [string, string][] = [
  ['g d', 'Go to dashboard'],
  ['g p', 'Go to patients'],
  ['/', 'Focus patient search'],
  ['Alt + R', 'Run assessment (encounter workstation)'],
  ['Alt + A', 'Ask the AI assistant (encounter workstation)'],
  ['Ctrl/⌘ + Enter', 'Submit the focused form or question'],
  ['?', 'Show keyboard shortcuts'],
]

export function AppShell() {
  const auth = useAuth()
  const navigate = useNavigate()
  const { user, hasAnyRole } = useCurrentUser()
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const [helpOpen, setHelpOpen] = useState(false)

  const shortcuts = useMemo(() => [
    { keys: 'g d', handler: () => navigate('/dashboard') },
    { keys: 'g p', handler: () => navigate('/patients') },
    { keys: '?', handler: () => setHelpOpen(true) },
    { keys: '/', handler: () => document.getElementById('patient-search')?.focus() },
  ], [navigate])
  useShortcuts(shortcuts)

  const renderItems = (items: NavItem[]) => items.filter((i) => hasAnyRole(...i.roles)).map((item) => (
    <ListItemButton key={item.to} component={NavLink} to={item.to} end={item.end}
                    sx={{ py: 0.5, '&.active': { bgcolor: '#e3ecf5', borderLeft: '3px solid #1f4e79', fontWeight: 600 } }}>
      <ListItemIcon sx={{ minWidth: 30 }}>{item.icon}</ListItemIcon>
      <ListItemText primary={item.label} slotProps={{ primary: { variant: 'body2' } }} />
    </ListItemButton>
  ))
  const clinical = renderItems(clinicalNav)
  const admin = renderItems(adminNav)

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppBar position="static">
        <Toolbar variant="dense" sx={{ gap: 1.5 }}>
          <FactCheckOutlinedIcon />
          <Typography variant="h6" component="div">MedAssist</Typography>
          <Typography variant="caption" sx={{ opacity: 0.85 }}>Clinical Decision Support</Typography>
          {config.environmentLabel && (
            <Chip label={config.environmentLabel} sx={{ bgcolor: 'rgba(255,255,255,0.15)', color: '#fff', fontSize: '0.62rem' }} />
          )}
          <Box sx={{ flex: 1 }} />
          <Tooltip title="Keyboard shortcuts (?)">
            <IconButton color="inherit" onClick={() => setHelpOpen(true)} aria-label="Keyboard shortcuts"><KeyboardOutlinedIcon /></IconButton>
          </Tooltip>
          <Chip label={`${user?.displayName ?? user?.username ?? ''} · ${(user?.roles ?? []).join(', ')}`}
                onClick={(e) => setMenuAnchor(e.currentTarget)} sx={{ bgcolor: 'rgba(255,255,255,0.15)', color: '#fff' }} />
          <Menu open={!!menuAnchor} anchorEl={menuAnchor} onClose={() => setMenuAnchor(null)}>
            <MenuItem disabled><Typography variant="caption">Signed in as {user?.username}</Typography></MenuItem>
            <MenuItem onClick={() => void auth.signoutRedirect()}><LogoutIcon fontSize="small" sx={{ mr: 1 }} />Sign out</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <SafetyDisclaimer />
      <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Box component="nav" aria-label="Main navigation"
             sx={{ width: 196, flexShrink: 0, borderRight: '1px solid #dfe3e8', bgcolor: '#fff', overflowY: 'auto' }}>
          {clinical.length > 0 && <List dense subheader={<ListSubheader>Clinical</ListSubheader>}>{clinical}</List>}
          {clinical.length > 0 && admin.length > 0 && <Divider />}
          {admin.length > 0 && <List dense subheader={<ListSubheader>Administration</ListSubheader>}>{admin}</List>}
        </Box>
        <Box component="main" sx={{ flex: 1, minWidth: 0, overflow: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
      <Dialog open={helpOpen} onClose={() => setHelpOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Keyboard shortcuts</DialogTitle>
        <DialogContent>
          <Table>
            <TableBody>
              {SHORTCUTS.map(([k, d]) => (
                <TableRow key={k}><TableCell sx={{ fontFamily: 'monospace', whiteSpace: 'nowrap' }}>{k}</TableCell><TableCell>{d}</TableCell></TableRow>
              ))}
            </TableBody>
          </Table>
        </DialogContent>
      </Dialog>
    </Box>
  )
}
