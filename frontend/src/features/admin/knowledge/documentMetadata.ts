import { z } from 'zod'
import type { DocumentMetadataInput } from '../../../api/types'

export const DOCUMENT_TYPES = ['CLINICAL_GUIDELINE', 'CLINICAL_PROTOCOL', 'REFERENCE_TEXT', 'LOCAL_POLICY', 'DRUG_INFORMATION', 'SYNTHETIC_DEMO'] as const
export const APPROVAL_STATUSES = ['DRAFT', 'APPROVED', 'RETIRED'] as const

export const metadataSchema = z.object({
  title: z.string().trim().min(1, 'Required').max(300),
  documentType: z.enum(DOCUMENT_TYPES),
  sourceOrganization: z.string().trim().min(1, 'Required').max(300),
  sourceUrl: z.string().trim().max(1000).refine((v) => v === '' || /^https?:\/\//.test(v), 'Must be an http(s) URL'),
  jurisdiction: z.string().trim().max(100),
  version: z.string().trim().min(1, 'Required').max(50),
  publicationDate: z.string(),
  reviewDate: z.string(),
  approvalStatus: z.enum(APPROVAL_STATUSES),
  conditionTags: z.string().max(4000),
})

export type MetadataFormValues = z.infer<typeof metadataSchema>

export const emptyMetadata: MetadataFormValues = {
  title: '', documentType: 'CLINICAL_GUIDELINE', sourceOrganization: '', sourceUrl: '', jurisdiction: '', version: '1.0',
  publicationDate: '', reviewDate: '', approvalStatus: 'DRAFT', conditionTags: '',
}

export const toMetadataInput = (v: MetadataFormValues): DocumentMetadataInput => ({
  title: v.title,
  documentType: v.documentType,
  sourceOrganization: v.sourceOrganization,
  sourceUrl: v.sourceUrl || undefined,
  jurisdiction: v.jurisdiction || undefined,
  version: v.version,
  publicationDate: v.publicationDate || undefined,
  reviewDate: v.reviewDate || undefined,
  approvalStatus: v.approvalStatus,
  conditionTags: v.conditionTags.split(',').map((t) => t.trim()).filter(Boolean),
})
