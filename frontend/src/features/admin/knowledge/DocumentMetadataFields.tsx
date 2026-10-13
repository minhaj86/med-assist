import { Grid, MenuItem, TextField } from '@mui/material'
import type { Control, FieldErrors, UseFormRegister } from 'react-hook-form'
import { Controller } from 'react-hook-form'
import { APPROVAL_STATUSES, DOCUMENT_TYPES, type MetadataFormValues } from './documentMetadata'

const label = (s: string) => s.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())

export function DocumentMetadataFields({ register, control, errors }: {
  register: UseFormRegister<MetadataFormValues>; control: Control<MetadataFormValues>; errors: FieldErrors<MetadataFormValues>
}) {
  const err = (k: keyof MetadataFormValues) => ({ error: !!errors[k], helperText: errors[k]?.message })
  return (
    <Grid container spacing={1.5}>
      <Grid size={{ xs: 12, md: 8 }}><TextField label="Title" required {...register('title')} {...err('title')} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}>
        <Controller name="documentType" control={control} render={({ field }) => (
          <TextField select label="Document type" {...field}>{DOCUMENT_TYPES.map((t) => <MenuItem key={t} value={t}>{label(t)}</MenuItem>)}</TextField>
        )} />
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}><TextField label="Source organization" required {...register('sourceOrganization')} {...err('sourceOrganization')} /></Grid>
      <Grid size={{ xs: 12, md: 6 }}><TextField label="Source URL" {...register('sourceUrl')} {...err('sourceUrl')} /></Grid>
      <Grid size={{ xs: 6, md: 3 }}><TextField label="Version" required {...register('version')} {...err('version')} /></Grid>
      <Grid size={{ xs: 6, md: 3 }}><TextField label="Jurisdiction" {...register('jurisdiction')} {...err('jurisdiction')} /></Grid>
      <Grid size={{ xs: 6, md: 3 }}><TextField label="Publication date" type="date" {...register('publicationDate')} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
      <Grid size={{ xs: 6, md: 3 }}><TextField label="Review date" type="date" {...register('reviewDate')} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}>
        <Controller name="approvalStatus" control={control} render={({ field }) => (
          <TextField select label="Approval status" {...field} helperText="Only APPROVED + enabled documents are used for clinical retrieval">
            {APPROVAL_STATUSES.map((t) => <MenuItem key={t} value={t}>{label(t)}</MenuItem>)}
          </TextField>
        )} />
      </Grid>
      <Grid size={{ xs: 12, md: 8 }}>
        <TextField label="Condition tags (comma-separated)" {...register('conditionTags')} helperText="Used to link documents to possible conditions during retrieval" />
      </Grid>
    </Grid>
  )
}
