import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Autocomplete, Box, Button, Grid, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import {
  useAddAllergy, useAddHistory, useAddLab, useAddMedication, useAddSymptom, useAddVitals, useConceptSuggestions,
} from '../../../api/hooks'
import type { VitalSignType } from '../../../api/types'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { localInputToIso, nowLocalInput } from '../../../components/format'

const optionalText = (max: number) => z.string().max(max).optional()
const optionalNumber = z.string().optional().refine((v) => !v || !Number.isNaN(Number(v)), 'Must be a number')

function FormActions({ onCancel, pending, label = 'Save' }: { onCancel: () => void; pending: boolean; label?: string }) {
  return (
    <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', mt: 1 }}>
      <Button onClick={onCancel}>Cancel</Button>
      <Button type="submit" variant="contained" disabled={pending}>{label}</Button>
    </Stack>
  )
}

/** Submits the enclosing form with Ctrl/⌘+Enter. */
const submitOnCtrlEnter = (e: React.KeyboardEvent<HTMLFormElement>) => {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
    e.preventDefault()
    e.currentTarget.requestSubmit()
  }
}

function ConceptField({ domain, value, onChange, label, error }: {
  domain: string; value: string; onChange: (v: string) => void; label: string; error?: string
}) {
  const [input, setInput] = useState(value)
  const suggestions = useConceptSuggestions(domain, input)
  return (
    <Autocomplete freeSolo options={(suggestions.data ?? []).map((c) => c.displayName)} inputValue={input}
                  onInputChange={(_, v) => { setInput(v); onChange(v) }}
                  renderInput={(params) => <TextField {...params} label={label} required error={!!error} helperText={error} autoFocus />} />
  )
}

// ---- Symptom ----------------------------------------------------------------------------------------------

const symptomSchema = z.object({
  symptom: z.string().trim().min(1, 'Required').max(200),
  presence: z.enum(['PRESENT', 'ABSENT']),
  onsetPattern: z.enum(['', 'SUDDEN', 'GRADUAL', 'UNKNOWN']),
  duration: optionalText(100),
  severity: z.string().optional().refine((v) => !v || (/^\d+$/.test(v) && Number(v) <= 10), '0-10'),
  location: optionalText(200),
  character: optionalText(200),
  radiation: optionalText(200),
  aggravatingFactors: optionalText(500),
  relievingFactors: optionalText(500),
  associatedSymptoms: optionalText(500),
  notes: optionalText(2000),
})
type SymptomValues = z.infer<typeof symptomSchema>

export function SymptomForm({ encounterId, onDone, initialText, proposalReference }: {
  encounterId: string; onDone: () => void; initialText?: string; proposalReference?: string
}) {
  const add = useAddSymptom(encounterId)
  const { control, register, handleSubmit, formState: { errors } } = useForm<SymptomValues>({
    resolver: zodResolver(symptomSchema),
    defaultValues: { symptom: initialText ?? '', presence: 'PRESENT', onsetPattern: '' },
  })
  const submit = handleSubmit((v) => add.mutate({
    ...v, onsetPattern: v.onsetPattern || undefined, severity: v.severity ? Number(v.severity) : undefined,
    aiProposalReference: proposalReference,
  }, { onSuccess: onDone }))
  return (
    <Box component="form" onSubmit={submit} onKeyDown={submitOnCtrlEnter} noValidate aria-label="Add symptom" sx={{ p: 1, bgcolor: '#f8fafc' }}>
      <ProblemAlert error={add.error} />
      <Grid container spacing={1}>
        <Grid size={12}>
          <Controller name="symptom" control={control} render={({ field }) => (
            <ConceptField domain="SYMPTOM" label="Symptom" value={field.value} onChange={field.onChange} error={errors.symptom?.message} />
          )} />
        </Grid>
        <Grid size={12}>
          <Controller name="presence" control={control} render={({ field }) => (
            <ToggleButtonGroup exclusive size="small" value={field.value} onChange={(_, v) => v && field.onChange(v)} aria-label="Presence">
              <ToggleButton value="PRESENT">Present</ToggleButton>
              <ToggleButton value="ABSENT">Absent (pertinent negative)</ToggleButton>
            </ToggleButtonGroup>
          )} />
        </Grid>
        <Grid size={6}>
          <Controller name="onsetPattern" control={control} render={({ field }) => (
            <TextField select label="Onset" {...field}>
              <MenuItem value="">—</MenuItem><MenuItem value="SUDDEN">Sudden</MenuItem>
              <MenuItem value="GRADUAL">Gradual</MenuItem><MenuItem value="UNKNOWN">Unknown</MenuItem>
            </TextField>
          )} />
        </Grid>
        <Grid size={6}><TextField label="Severity (0-10)" {...register('severity')} error={!!errors.severity} helperText={errors.severity?.message} /></Grid>
        <Grid size={6}><TextField label="Duration" {...register('duration')} /></Grid>
        <Grid size={6}><TextField label="Location" {...register('location')} /></Grid>
        <Grid size={6}><TextField label="Character" {...register('character')} /></Grid>
        <Grid size={6}><TextField label="Radiation" {...register('radiation')} /></Grid>
        <Grid size={6}><TextField label="Aggravating factors" {...register('aggravatingFactors')} /></Grid>
        <Grid size={6}><TextField label="Relieving factors" {...register('relievingFactors')} /></Grid>
        <Grid size={12}><TextField label="Associated symptoms" {...register('associatedSymptoms')} /></Grid>
        <Grid size={12}><TextField label="Clinician notes" multiline minRows={2} {...register('notes')} /></Grid>
      </Grid>
      <FormActions onCancel={onDone} pending={add.isPending} label={proposalReference ? 'Confirm and save' : 'Save'} />
    </Box>
  )
}

// ---- Vital signs ------------------------------------------------------------------------------------------

const VITAL_FIELDS: { type: VitalSignType; label: string; unit: string }[] = [
  { type: 'SYSTOLIC_BLOOD_PRESSURE', label: 'Systolic BP', unit: 'mmHg' },
  { type: 'DIASTOLIC_BLOOD_PRESSURE', label: 'Diastolic BP', unit: 'mmHg' },
  { type: 'HEART_RATE', label: 'Heart rate', unit: '/min' },
  { type: 'RESPIRATORY_RATE', label: 'Resp. rate', unit: '/min' },
  { type: 'OXYGEN_SATURATION', label: 'SpO₂', unit: '%' },
  { type: 'BODY_TEMPERATURE', label: 'Temperature', unit: '°C' },
  { type: 'BODY_WEIGHT', label: 'Weight', unit: 'kg' },
  { type: 'BODY_HEIGHT', label: 'Height', unit: 'cm' },
  { type: 'BODY_MASS_INDEX', label: 'BMI', unit: 'kg/m²' },
]

const vitalsSchema = z.object({
  measuredAt: z.string().min(1, 'Required'),
  values: z.record(z.string(), z.string().optional().refine((v) => !v || !Number.isNaN(Number(v)), 'Number')),
  consciousnessLevel: z.enum(['', 'ALERT', 'NEW_CONFUSION', 'VOICE', 'PAIN', 'UNRESPONSIVE']),
  source: z.enum(['CLINICIAN', 'DEVICE']),
})
type VitalsValues = z.infer<typeof vitalsSchema>

export function VitalsForm({ encounterId, onDone }: { encounterId: string; onDone: () => void }) {
  const add = useAddVitals(encounterId)
  const [empty, setEmpty] = useState(false)
  const { control, register, handleSubmit, formState: { errors } } = useForm<VitalsValues>({
    resolver: zodResolver(vitalsSchema),
    defaultValues: { measuredAt: nowLocalInput(), values: {}, consciousnessLevel: '', source: 'CLINICIAN' },
  })
  const submit = handleSubmit((v) => {
    const measurements: Record<string, unknown>[] = VITAL_FIELDS
      .filter((f) => v.values[f.type])
      .map((f) => ({ type: f.type, value: Number(v.values[f.type]) }))
    if (v.consciousnessLevel) measurements.push({ type: 'CONSCIOUSNESS_LEVEL', consciousnessLevel: v.consciousnessLevel })
    if (measurements.length === 0) {
      setEmpty(true)
      return
    }
    add.mutate({ measuredAt: localInputToIso(v.measuredAt), measurements, source: v.source }, { onSuccess: onDone })
  })
  return (
    <Box component="form" onSubmit={submit} onKeyDown={submitOnCtrlEnter} noValidate aria-label="Record vital signs" sx={{ p: 1, bgcolor: '#f8fafc' }}>
      <ProblemAlert error={add.error} title="Vital signs not saved" />
      {empty && <Alert severity="info" sx={{ mb: 1 }}>Enter at least one measurement.</Alert>}
      <Grid container spacing={1}>
        <Grid size={7}><TextField type="datetime-local" label="Measured at" slotProps={{ inputLabel: { shrink: true } }} {...register('measuredAt')} /></Grid>
        <Grid size={5}>
          <Controller name="source" control={control} render={({ field }) => (
            <TextField select label="Source" {...field}><MenuItem value="CLINICIAN">Clinician</MenuItem><MenuItem value="DEVICE">Device</MenuItem></TextField>
          )} />
        </Grid>
        {VITAL_FIELDS.map((f, i) => (
          <Grid size={4} key={f.type}>
            <TextField label={`${f.label} (${f.unit})`} autoFocus={i === 0} inputMode="decimal" {...register(`values.${f.type}`)}
                       error={!!errors.values?.[f.type]} />
          </Grid>
        ))}
        <Grid size={12}>
          <Controller name="consciousnessLevel" control={control} render={({ field }) => (
            <TextField select label="Level of consciousness (ACVPU)" {...field}>
              <MenuItem value="">—</MenuItem><MenuItem value="ALERT">Alert</MenuItem><MenuItem value="NEW_CONFUSION">New confusion</MenuItem>
              <MenuItem value="VOICE">Responds to voice</MenuItem><MenuItem value="PAIN">Responds to pain</MenuItem>
              <MenuItem value="UNRESPONSIVE">Unresponsive</MenuItem>
            </TextField>
          )} />
        </Grid>
      </Grid>
      <FormActions onCancel={onDone} pending={add.isPending} />
    </Box>
  )
}

// ---- Laboratory --------------------------------------------------------------------------------------------

const labSchema = z.object({
  testName: z.string().trim().min(1, 'Required').max(200),
  codingSystem: z.enum(['', 'LOINC', 'SNOMED_CT', 'LOCAL']),
  testCode: optionalText(50),
  numericValue: optionalNumber,
  textValue: optionalText(500),
  unit: optionalText(50),
  referenceRangeLow: optionalNumber,
  referenceRangeHigh: optionalNumber,
  abnormalFlag: z.enum(['', 'NORMAL', 'LOW', 'HIGH', 'CRITICAL_LOW', 'CRITICAL_HIGH', 'ABNORMAL']),
  collectedAt: z.string().min(1, 'Required'),
}).refine((v) => v.numericValue || v.textValue, { message: 'Enter a numeric or text result', path: ['numericValue'] })
type LabValues = z.infer<typeof labSchema>

export function LabForm({ encounterId, onDone }: { encounterId: string; onDone: () => void }) {
  const add = useAddLab(encounterId)
  const { control, register, handleSubmit, formState: { errors } } = useForm<LabValues>({
    resolver: zodResolver(labSchema),
    defaultValues: { testName: '', codingSystem: '', abnormalFlag: '', collectedAt: nowLocalInput() },
  })
  const num = (v?: string) => (v ? Number(v) : undefined)
  const submit = handleSubmit((v) => add.mutate({
    testName: v.testName, codingSystem: v.testCode ? (v.codingSystem || 'LOINC') : undefined, testCode: v.testCode || undefined,
    numericValue: num(v.numericValue), textValue: v.textValue || undefined, unit: v.unit || undefined,
    referenceRangeLow: num(v.referenceRangeLow), referenceRangeHigh: num(v.referenceRangeHigh),
    abnormalFlag: v.abnormalFlag || undefined, collectedAt: localInputToIso(v.collectedAt),
  }, { onSuccess: onDone }))
  return (
    <Box component="form" onSubmit={submit} onKeyDown={submitOnCtrlEnter} noValidate aria-label="Record lab result" sx={{ p: 1, bgcolor: '#f8fafc' }}>
      <ProblemAlert error={add.error} title="Result not saved" />
      <Grid container spacing={1}>
        <Grid size={12}>
          <Controller name="testName" control={control} render={({ field }) => (
            <ConceptField domain="LAB_TEST" label="Test" value={field.value} onChange={field.onChange} error={errors.testName?.message} />
          )} />
        </Grid>
        <Grid size={5}>
          <Controller name="codingSystem" control={control} render={({ field }) => (
            <TextField select label="Code system" {...field}><MenuItem value="">—</MenuItem><MenuItem value="LOINC">LOINC</MenuItem>
              <MenuItem value="SNOMED_CT">SNOMED CT</MenuItem><MenuItem value="LOCAL">Local</MenuItem></TextField>
          )} />
        </Grid>
        <Grid size={7}><TextField label="Code (optional, unverified)" {...register('testCode')} /></Grid>
        <Grid size={4}><TextField label="Value" inputMode="decimal" {...register('numericValue')} error={!!errors.numericValue} helperText={errors.numericValue?.message} /></Grid>
        <Grid size={4}><TextField label="Unit" {...register('unit')} /></Grid>
        <Grid size={4}><TextField label="or text result" {...register('textValue')} /></Grid>
        <Grid size={4}><TextField label="Ref. low" {...register('referenceRangeLow')} error={!!errors.referenceRangeLow} /></Grid>
        <Grid size={4}><TextField label="Ref. high" {...register('referenceRangeHigh')} error={!!errors.referenceRangeHigh} /></Grid>
        <Grid size={4}>
          <Controller name="abnormalFlag" control={control} render={({ field }) => (
            <TextField select label="Lab flag" {...field}><MenuItem value="">From range</MenuItem>
              {['NORMAL', 'LOW', 'HIGH', 'CRITICAL_LOW', 'CRITICAL_HIGH', 'ABNORMAL'].map((f) => <MenuItem key={f} value={f}>{f.replace('_', ' ').toLowerCase()}</MenuItem>)}
            </TextField>
          )} />
        </Grid>
        <Grid size={12}><TextField type="datetime-local" label="Collected at" slotProps={{ inputLabel: { shrink: true } }} {...register('collectedAt')} /></Grid>
      </Grid>
      <FormActions onCancel={onDone} pending={add.isPending} />
    </Box>
  )
}

// ---- Medication, allergy, history ---------------------------------------------------------------------------

const medicationSchema = z.object({
  medicationName: z.string().trim().min(1, 'Required').max(200),
  dosage: optionalText(100), frequency: optionalText(100), route: optionalText(50),
  startDate: z.string().optional(),
  status: z.enum(['ACTIVE', 'ON_HOLD', 'COMPLETED', 'STOPPED', 'UNKNOWN']),
  source: z.enum(['CLINICIAN', 'PATIENT_REPORTED']),
})
type MedicationValues = z.infer<typeof medicationSchema>

export function MedicationForm({ encounterId, onDone }: { encounterId: string; onDone: () => void }) {
  const add = useAddMedication(encounterId)
  const { control, register, handleSubmit, formState: { errors } } = useForm<MedicationValues>({
    resolver: zodResolver(medicationSchema), defaultValues: { medicationName: '', status: 'ACTIVE', source: 'PATIENT_REPORTED' },
  })
  const submit = handleSubmit((v) => add.mutate({ ...v, startDate: v.startDate || undefined }, { onSuccess: onDone }))
  return (
    <Box component="form" onSubmit={submit} onKeyDown={submitOnCtrlEnter} noValidate aria-label="Add medication" sx={{ p: 1, bgcolor: '#f8fafc' }}>
      <ProblemAlert error={add.error} />
      <Grid container spacing={1}>
        <Grid size={12}><TextField label="Medication" required autoFocus {...register('medicationName')} error={!!errors.medicationName} helperText={errors.medicationName?.message} /></Grid>
        <Grid size={4}><TextField label="Dosage" {...register('dosage')} /></Grid>
        <Grid size={4}><TextField label="Frequency" {...register('frequency')} /></Grid>
        <Grid size={4}><TextField label="Route" {...register('route')} /></Grid>
        <Grid size={4}><TextField type="date" label="Start date" slotProps={{ inputLabel: { shrink: true } }} {...register('startDate')} /></Grid>
        <Grid size={4}>
          <Controller name="status" control={control} render={({ field }) => (
            <TextField select label="Status" {...field}>{['ACTIVE', 'ON_HOLD', 'COMPLETED', 'STOPPED', 'UNKNOWN'].map((s) => <MenuItem key={s} value={s}>{s.toLowerCase().replace('_', ' ')}</MenuItem>)}</TextField>
          )} />
        </Grid>
        <Grid size={4}>
          <Controller name="source" control={control} render={({ field }) => (
            <TextField select label="Source" {...field}><MenuItem value="PATIENT_REPORTED">Patient-reported</MenuItem><MenuItem value="CLINICIAN">Clinician</MenuItem></TextField>
          )} />
        </Grid>
      </Grid>
      <FormActions onCancel={onDone} pending={add.isPending} />
    </Box>
  )
}

const allergySchema = z.object({
  allergen: z.string().trim().min(1, 'Required').max(200),
  category: z.enum(['', 'MEDICATION', 'FOOD', 'ENVIRONMENT', 'BIOLOGIC', 'OTHER']),
  reaction: optionalText(300),
  severity: z.enum(['MILD', 'MODERATE', 'SEVERE', 'UNKNOWN']),
})
type AllergyValues = z.infer<typeof allergySchema>

export function AllergyForm({ encounterId, onDone }: { encounterId: string; onDone: () => void }) {
  const add = useAddAllergy(encounterId)
  const { control, register, handleSubmit, formState: { errors } } = useForm<AllergyValues>({
    resolver: zodResolver(allergySchema), defaultValues: { allergen: '', category: '', severity: 'UNKNOWN' },
  })
  const submit = handleSubmit((v) => add.mutate({ ...v, category: v.category || undefined, source: 'PATIENT_REPORTED' }, { onSuccess: onDone }))
  return (
    <Box component="form" onSubmit={submit} onKeyDown={submitOnCtrlEnter} noValidate aria-label="Add allergy" sx={{ p: 1, bgcolor: '#f8fafc' }}>
      <ProblemAlert error={add.error} />
      <Grid container spacing={1}>
        <Grid size={8}><TextField label="Allergen" required autoFocus {...register('allergen')} error={!!errors.allergen} helperText={errors.allergen?.message} /></Grid>
        <Grid size={4}>
          <Controller name="category" control={control} render={({ field }) => (
            <TextField select label="Category" {...field}><MenuItem value="">—</MenuItem>
              {['MEDICATION', 'FOOD', 'ENVIRONMENT', 'BIOLOGIC', 'OTHER'].map((c) => <MenuItem key={c} value={c}>{c.toLowerCase()}</MenuItem>)}</TextField>
          )} />
        </Grid>
        <Grid size={8}><TextField label="Reaction" {...register('reaction')} /></Grid>
        <Grid size={4}>
          <Controller name="severity" control={control} render={({ field }) => (
            <TextField select label="Severity" {...field}>{['MILD', 'MODERATE', 'SEVERE', 'UNKNOWN'].map((c) => <MenuItem key={c} value={c}>{c.toLowerCase()}</MenuItem>)}</TextField>
          )} />
        </Grid>
      </Grid>
      <FormActions onCancel={onDone} pending={add.isPending} />
    </Box>
  )
}

const historySchema = z.object({
  category: z.enum(['PAST_DIAGNOSIS', 'FAMILY_HISTORY', 'SURGICAL_HISTORY', 'SMOKING', 'ALCOHOL', 'SOCIAL_HISTORY', 'HOSPITALIZATION']),
  description: z.string().trim().min(1, 'Required').max(500),
  relationship: optionalText(50),
  status: z.enum(['ACTIVE', 'RESOLVED', 'CURRENT', 'FORMER', 'NEVER', 'UNKNOWN']),
  onsetDate: z.string().optional(),
  notes: optionalText(2000),
}).refine((v) => v.category !== 'FAMILY_HISTORY' || !!v.relationship?.trim(), { message: 'Relationship is required for family history', path: ['relationship'] })
type HistoryValues = z.infer<typeof historySchema>

export function HistoryForm({ encounterId, onDone, initialText, proposalReference }: {
  encounterId: string; onDone: () => void; initialText?: string; proposalReference?: string
}) {
  const add = useAddHistory(encounterId)
  const { control, register, handleSubmit, formState: { errors } } = useForm<HistoryValues>({
    resolver: zodResolver(historySchema),
    defaultValues: { category: proposalReference ? 'SOCIAL_HISTORY' : 'PAST_DIAGNOSIS', description: initialText ?? '', status: 'UNKNOWN' },
  })
  const category = useWatch({ control, name: 'category' })
  const submit = handleSubmit((v) => add.mutate({
    ...v, onsetDate: v.onsetDate || undefined, relationship: v.relationship || undefined, aiProposalReference: proposalReference,
  }, { onSuccess: onDone }))
  return (
    <Box component="form" onSubmit={submit} onKeyDown={submitOnCtrlEnter} noValidate aria-label="Add history" sx={{ p: 1, bgcolor: '#f8fafc' }}>
      <ProblemAlert error={add.error} />
      <Grid container spacing={1}>
        <Grid size={6}>
          <Controller name="category" control={control} render={({ field }) => (
            <TextField select label="Category" {...field}>
              {[['PAST_DIAGNOSIS', 'Past diagnosis'], ['FAMILY_HISTORY', 'Family history'], ['SURGICAL_HISTORY', 'Surgical history'],
                ['SMOKING', 'Smoking'], ['ALCOHOL', 'Alcohol'], ['SOCIAL_HISTORY', 'Social history'], ['HOSPITALIZATION', 'Previous hospitalization']]
                .map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
            </TextField>
          )} />
        </Grid>
        <Grid size={6}>
          <Controller name="status" control={control} render={({ field }) => (
            <TextField select label="Status" {...field}>{['ACTIVE', 'RESOLVED', 'CURRENT', 'FORMER', 'NEVER', 'UNKNOWN'].map((s) => <MenuItem key={s} value={s}>{s.toLowerCase()}</MenuItem>)}</TextField>
          )} />
        </Grid>
        <Grid size={12}><TextField label="Description" required autoFocus {...register('description')} error={!!errors.description} helperText={errors.description?.message} /></Grid>
        {category === 'FAMILY_HISTORY' && (
          <Grid size={6}><TextField label="Relationship" required {...register('relationship')} error={!!errors.relationship} helperText={errors.relationship?.message} /></Grid>
        )}
        <Grid size={6}><TextField type="date" label="Onset / date" slotProps={{ inputLabel: { shrink: true } }} {...register('onsetDate')} /></Grid>
        <Grid size={12}><TextField label="Notes" multiline minRows={2} {...register('notes')} /></Grid>
      </Grid>
      <FormActions onCancel={onDone} pending={add.isPending} label={proposalReference ? 'Confirm and save' : 'Save'} />
    </Box>
  )
}
