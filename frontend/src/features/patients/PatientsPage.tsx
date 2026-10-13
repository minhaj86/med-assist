import PersonAddAlt1OutlinedIcon from '@mui/icons-material/PersonAddAlt1Outlined'
import SearchIcon from '@mui/icons-material/Search'
import {
  Box, Button, InputAdornment, LinearProgress, Stack, Table, TableBody, TableCell, TableHead, TablePagination,
  TableRow, TextField, Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { usePatientSearch } from '../../api/hooks'
import { SyntheticChip } from '../../components/Chips'
import { PageHeader } from '../../components/PageHeader'
import { ProblemAlert } from '../../components/ProblemAlert'
import { fmtDate, humanize } from '../../components/format'
import { NewPatientDialog } from './PatientForm'

export function PatientsPage() {
  const [input, setInput] = useState('')
  const [term, setTerm] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()
  useEffect(() => {
    const t = window.setTimeout(() => { setTerm(input.trim()); setPage(0) }, 250)
    return () => window.clearTimeout(t)
  }, [input])
  const { data, isFetching, error } = usePatientSearch(term, page)

  return (
    <Box>
      <PageHeader title="Patients" subtitle="Search by name or external patient identifier. Press / to focus search."
                  actions={<Button variant="contained" startIcon={<PersonAddAlt1OutlinedIcon />} onClick={() => setCreating(true)}>Register patient</Button>} />
      <Box sx={{ p: 2 }}>
        <TextField id="patient-search" placeholder="Search patients…" value={input} onChange={(e) => setInput(e.target.value)}
                   slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> },
                     htmlInput: { 'aria-label': 'Search patients' } }}
                   sx={{ maxWidth: 480, bgcolor: '#fff' }} />
        {isFetching && <LinearProgress sx={{ mt: 1 }} />}
        <ProblemAlert error={error} />
        <Table sx={{ mt: 1, bgcolor: '#fff' }} aria-label="Patients">
          <TableHead>
            <TableRow><TableCell>Name</TableCell><TableCell>External ID</TableCell><TableCell>Date of birth</TableCell>
              <TableCell>Age</TableCell><TableCell>Biological sex</TableCell><TableCell /></TableRow>
          </TableHead>
          <TableBody>
            {data?.items.map((p) => (
              <TableRow key={p.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/patients/${p.id}`)}>
                <TableCell><strong>{p.lastName}</strong>, {p.firstName}</TableCell>
                <TableCell sx={{ fontFamily: 'monospace' }}>{p.externalPatientId}</TableCell>
                <TableCell>{fmtDate(p.dateOfBirth)}</TableCell>
                <TableCell>{p.ageYears}</TableCell>
                <TableCell>{humanize(p.biologicalSex)}</TableCell>
                <TableCell align="right">{p.synthetic && <SyntheticChip />}</TableCell>
              </TableRow>
            ))}
            {data && data.items.length === 0 && (
              <TableRow><TableCell colSpan={6}><Typography variant="body2" color="text.secondary">No matching patients.</Typography></TableCell></TableRow>
            )}
          </TableBody>
        </Table>
        {data && (
          <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
            <TablePagination component="div" count={data.totalItems} page={page} rowsPerPage={20} rowsPerPageOptions={[20]}
                             onPageChange={(_, p) => setPage(p)} />
          </Stack>
        )}
      </Box>
      <NewPatientDialog open={creating} onClose={() => setCreating(false)} onCreated={(p) => navigate(`/patients/${p.id}`)} />
    </Box>
  )
}
