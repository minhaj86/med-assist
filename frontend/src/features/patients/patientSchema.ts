import { z } from 'zod'

export const patientSchema = z.object({
  externalPatientId: z.string().regex(/^[A-Za-z0-9-]{3,64}$/, '3-64 letters, digits or hyphens'),
  firstName: z.string().trim().min(1, 'Required').max(100),
  lastName: z.string().trim().min(1, 'Required').max(100),
  dateOfBirth: z.string().min(1, 'Required').refine((v) => new Date(v) < new Date(), 'Must be in the past'),
  biologicalSex: z.enum(['FEMALE', 'MALE', 'INTERSEX', 'UNKNOWN']),
  gender: z.string().max(50).optional(),
  contactPhone: z.string().regex(/^[0-9 +()-]*$/, 'Digits, spaces, +, ( ) and - only').max(40).optional(),
})

export type PatientFormValues = z.infer<typeof patientSchema>
