import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, MenuItem, TextField } from '@mui/material'
import { Controller, useForm } from 'react-hook-form'
import { useCreatePatient } from '../../api/hooks'
import type { Patient } from '../../api/types'
import { ProblemAlert } from '../../components/ProblemAlert'
import { patientSchema, type PatientFormValues } from './patientSchema'


export function NewPatientDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (p: Patient) => void }) {
  const create = useCreatePatient()
  const { register, handleSubmit, control, formState: { errors }, reset } = useForm<PatientFormValues>({
    resolver: zodResolver(patientSchema),
    defaultValues: { externalPatientId: '', firstName: '', lastName: '', dateOfBirth: '', biologicalSex: 'UNKNOWN', gender: '', contactPhone: '' },
  })
  const submit = handleSubmit((values) => create.mutate(values, {
    onSuccess: (p) => {
      reset()
      onCreated(p)
    },
  }))
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={submit} noValidate aria-label="Register patient">
        <DialogTitle>Register patient</DialogTitle>
        <DialogContent>
          <ProblemAlert error={create.error} title="Patient could not be registered" />
          <Grid container spacing={1.5} sx={{ mt: 0.5 }}>
            <Grid size={6}><TextField label="External patient ID" required autoFocus {...register('externalPatientId')}
                                       error={!!errors.externalPatientId} helperText={errors.externalPatientId?.message} /></Grid>
            <Grid size={6}><TextField label="Date of birth" type="date" required slotProps={{ inputLabel: { shrink: true } }}
                                       {...register('dateOfBirth')} error={!!errors.dateOfBirth} helperText={errors.dateOfBirth?.message} /></Grid>
            <Grid size={6}><TextField label="First name" required {...register('firstName')} error={!!errors.firstName} helperText={errors.firstName?.message} /></Grid>
            <Grid size={6}><TextField label="Last name" required {...register('lastName')} error={!!errors.lastName} helperText={errors.lastName?.message} /></Grid>
            <Grid size={6}>
              <Controller name="biologicalSex" control={control} render={({ field }) => (
                <TextField select label="Biological sex" required {...field}>
                  {['FEMALE', 'MALE', 'INTERSEX', 'UNKNOWN'].map((v) => <MenuItem key={v} value={v}>{v.toLowerCase()}</MenuItem>)}
                </TextField>
              )} />
            </Grid>
            <Grid size={6}><TextField label="Gender (optional)" {...register('gender')} /></Grid>
            <Grid size={6}><TextField label="Contact phone (optional)" {...register('contactPhone')}
                                       error={!!errors.contactPhone} helperText={errors.contactPhone?.message} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={create.isPending}>Register</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
