import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, query } from './client'
import type {
  AiExecutionSummary, AiStatus, Allergy, AssessmentRun, AssessmentVersion, AssistantAnswer, AssistantMessage,
  AuditEvent, Concept, Dashboard, DocumentMetadataInput, Encounter, EncounterChart, FeedbackAction, FinalAssessment,
  HistoryEntry, Investigation, InvestigationStatus, KnowledgeChunk, KnowledgeDocumentDetail, KnowledgeDocumentSummary,
  KnowledgeSearchHit, KnowledgeStatus, LabResult, Me, Medication, MedicationStatus, PageResponse, Patient,
  PatientSummary, PromptSummary, RuleDefinition, SafetyCheck, SuggestionType, Symptom, SystemHealth, UserAccount,
  VersionSummary, VitalSign, AllergyStatus, ApprovalStatus,
} from './types'

// ---- Session ------------------------------------------------------------------------------------------------

export const useMe = (enabled = true) => useQuery({ queryKey: ['me'], queryFn: () => api.get<Me>('/me'), enabled, staleTime: 60_000 })

// ---- Dashboard & patients ------------------------------------------------------------------------------------

export const useDashboard = (since?: string) =>
  useQuery({ queryKey: ['dashboard', since], queryFn: () => api.get<Dashboard>(`/dashboard${query({ since })}`), refetchInterval: 30_000 })

export const usePatientSearch = (q: string, page: number) =>
  useQuery({
    queryKey: ['patients', q, page],
    queryFn: () => api.get<PageResponse<PatientSummary>>(`/patients${query({ query: q, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

export const usePatient = (id?: string) =>
  useQuery({ queryKey: ['patient', id], queryFn: () => api.get<Patient>(`/patients/${id}`), enabled: !!id })

export const usePatientEncounters = (id?: string) =>
  useQuery({ queryKey: ['patient-encounters', id], queryFn: () => api.get<Encounter[]>(`/patients/${id}/encounters`), enabled: !!id })

export interface PatientInput {
  externalPatientId: string
  firstName: string
  lastName: string
  dateOfBirth: string
  biologicalSex: string
  gender?: string
  contactPhone?: string
}

export function useCreatePatient() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: PatientInput) => api.post<Patient>('/patients', input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['patients'] }),
  })
}

export function useCreateEncounter(patientId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { encounterType: string; chiefComplaint?: string }) =>
      api.post<Encounter>(`/patients/${patientId}/encounters`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['patient-encounters', patientId] }),
  })
}

// ---- Encounter chart & clinical data ----------------------------------------------------------------------

export const useChart = (encounterId?: string) =>
  useQuery({ queryKey: ['chart', encounterId], queryFn: () => api.get<EncounterChart>(`/encounters/${encounterId}/chart`), enabled: !!encounterId })

export const useSafetyCheck = (encounterId?: string) =>
  useQuery({ queryKey: ['safety', encounterId], queryFn: () => api.get<SafetyCheck>(`/encounters/${encounterId}/safety-alerts`), enabled: !!encounterId })

export const useConceptSuggestions = (domain: string, q: string) =>
  useQuery({
    queryKey: ['terminology', domain, q],
    queryFn: () => api.get<Concept[]>(`/terminology/concepts${query({ domain, query: q, limit: 15 })}`),
    staleTime: 300_000,
  })

/** Invalidates everything derived from the encounter's clinical data (chart and live safety rules). */
function useClinicalMutation<TInput, TResult>(encounterId: string, fn: (input: TInput) => Promise<TResult>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['chart', encounterId] })
      qc.invalidateQueries({ queryKey: ['safety', encounterId] })
    },
  })
}

export const useAddSymptom = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: Record<string, unknown>) => api.post<Symptom>(`/encounters/${encounterId}/symptoms`, input))

export const useAddVitals = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: Record<string, unknown>) => api.post<VitalSign[]>(`/encounters/${encounterId}/observations`, input))

export const useAddLab = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: Record<string, unknown>) => api.post<LabResult>(`/encounters/${encounterId}/labs`, input))

export const useAddMedication = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: Record<string, unknown>) => api.post<Medication>(`/encounters/${encounterId}/medications`, input))

export const useAddAllergy = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: Record<string, unknown>) => api.post<Allergy>(`/encounters/${encounterId}/allergies`, input))

export const useAddHistory = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: Record<string, unknown>) => api.post<HistoryEntry>(`/encounters/${encounterId}/history`, input))

export const useUpdateMedicationStatus = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: { id: string; status: MedicationStatus }) =>
    api.patch<Medication>(`/medications/${input.id}`, { status: input.status }))

export const useUpdateAllergyStatus = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: { id: string; status: AllergyStatus }) =>
    api.patch<Allergy>(`/allergies/${input.id}`, { status: input.status }))

export type EnteredInErrorKind = 'symptom' | 'observation' | 'lab' | 'medication' | 'allergy' | 'history'

export const useMarkEnteredInError = (encounterId: string) =>
  useClinicalMutation(encounterId, (input: { kind: EnteredInErrorKind; id: string; reason: string }) => {
    const path = {
      symptom: `/encounters/${encounterId}/symptoms/${input.id}`,
      observation: `/encounters/${encounterId}/observations/${input.id}`,
      lab: `/encounters/${encounterId}/labs/${input.id}`,
      medication: `/medications/${input.id}`,
      allergy: `/allergies/${input.id}`,
      history: `/history/${input.id}`,
    }[input.kind]
    return api.post(`${path}/entered-in-error`, { reason: input.reason })
  })

export function useUpdateChiefComplaint(encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (chiefComplaint: string) => api.patch<Encounter>(`/encounters/${encounterId}`, { chiefComplaint }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['chart', encounterId] }),
  })
}

export function useCompleteEncounter(encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<Encounter>(`/encounters/${encounterId}/complete`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['chart', encounterId] }),
  })
}

// ---- Assessment --------------------------------------------------------------------------------------------

export const useVersions = (encounterId?: string) =>
  useQuery({ queryKey: ['versions', encounterId], queryFn: () => api.get<VersionSummary[]>(`/encounters/${encounterId}/assessments`), enabled: !!encounterId })

export const useVersion = (versionId?: string) =>
  useQuery({ queryKey: ['version', versionId], queryFn: () => api.get<AssessmentVersion>(`/assessments/${versionId}`), enabled: !!versionId })

export function useRun(runId?: string) {
  return useQuery({
    queryKey: ['run', runId],
    queryFn: () => api.get<AssessmentRun>(`/assessment-runs/${runId}`),
    enabled: !!runId,
    refetchInterval: (q) => (q.state.data && (q.state.data.status === 'COMPLETED' || q.state.data.status === 'FAILED') ? false : 1000),
  })
}

export function useStartAssessment(encounterId: string) {
  return useMutation({
    mutationFn: (includeAi: boolean) => api.post<AssessmentRun>(`/encounters/${encounterId}/assessment`, { includeAi }),
  })
}

export function useFeedback(versionId: string, encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { suggestionType: SuggestionType; suggestionId: string; action: FeedbackAction; reason?: string; modifiedText?: string }) =>
      api.post(`/assessments/${versionId}/feedback`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['version', versionId] })
      qc.invalidateQueries({ queryKey: ['investigations', encounterId] })
    },
  })
}

export const useFinalAssessment = (encounterId?: string) =>
  useQuery({
    queryKey: ['final', encounterId],
    queryFn: async () => {
      try {
        return await api.get<FinalAssessment>(`/encounters/${encounterId}/final-assessment`)
      } catch (e) {
        if ((e as { status?: number }).status === 404) return null
        throw e
      }
    },
    enabled: !!encounterId,
  })

export function useFinalize(encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: Record<string, unknown>) => api.post<FinalAssessment>(`/encounters/${encounterId}/final-assessment`, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['final', encounterId] })
      qc.invalidateQueries({ queryKey: ['version'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export const useInvestigations = (encounterId?: string) =>
  useQuery({ queryKey: ['investigations', encounterId], queryFn: () => api.get<Investigation[]>(`/encounters/${encounterId}/investigations`), enabled: !!encounterId })

export function useAddInvestigation(encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { name: string; reason?: string; status: InvestigationStatus }) =>
      api.post<Investigation>(`/encounters/${encounterId}/investigations`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['investigations', encounterId] }),
  })
}

export function useInvestigationStatus(encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: string; status: InvestigationStatus; note?: string }) =>
      api.patch<Investigation>(`/investigations/${input.id}/status`, { status: input.status, note: input.note }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['investigations', encounterId] }),
  })
}

// ---- Assistant ---------------------------------------------------------------------------------------------

export const useConversation = (encounterId?: string) =>
  useQuery({ queryKey: ['assistant', encounterId], queryFn: () => api.get<AssistantMessage[]>(`/encounters/${encounterId}/assistant/conversation`), enabled: !!encounterId })

export function useAskAssistant(encounterId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { question: string; conversationId?: string }) =>
      api.post<AssistantAnswer>(`/encounters/${encounterId}/assistant/questions`, input),
    onSettled: () => qc.invalidateQueries({ queryKey: ['assistant', encounterId] }),
  })
}

// ---- Admin: knowledge --------------------------------------------------------------------------------------

const inProgress = (docs?: KnowledgeDocumentSummary[]) =>
  docs?.some((d) => !['INDEXED', 'FAILED'].includes(d.ingestionStatus)) ?? false

export const useKnowledgeDocuments = () =>
  useQuery({
    queryKey: ['knowledge-docs'],
    queryFn: () => api.get<KnowledgeDocumentSummary[]>('/admin/knowledge/documents'),
    refetchInterval: (q) => (inProgress(q.state.data) ? 2000 : false),
  })

export const useKnowledgeDocument = (id?: string) =>
  useQuery({
    queryKey: ['knowledge-doc', id],
    queryFn: () => api.get<KnowledgeDocumentDetail>(`/admin/knowledge/documents/${id}`),
    enabled: !!id,
    refetchInterval: (q) => (q.state.data && !['INDEXED', 'FAILED'].includes(q.state.data.summary.ingestionStatus) ? 2000 : false),
  })

export const useKnowledgeChunks = (id: string | undefined, page: number) =>
  useQuery({
    queryKey: ['knowledge-chunks', id, page],
    queryFn: () => api.get<PageResponse<KnowledgeChunk>>(`/admin/knowledge/documents/${id}/chunks${query({ page, size: 10 })}`),
    enabled: !!id,
    placeholderData: keepPreviousData,
  })

export const useKnowledgeStatus = () =>
  useQuery({ queryKey: ['knowledge-status'], queryFn: () => api.get<KnowledgeStatus>('/admin/knowledge/status') })

function useKnowledgeMutation<TInput, TResult>(fn: (input: TInput) => Promise<TResult>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['knowledge-docs'] })
      qc.invalidateQueries({ queryKey: ['knowledge-doc'] })
      qc.invalidateQueries({ queryKey: ['knowledge-status'] })
    },
  })
}

function metadataForm(metadata: DocumentMetadataInput, file: File) {
  const form = new FormData()
  form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  form.append('file', file)
  return form
}

export const useUploadDocument = () =>
  useKnowledgeMutation((input: { metadata: DocumentMetadataInput; file: File }) =>
    api.post<KnowledgeDocumentSummary>('/admin/knowledge/documents', metadataForm(input.metadata, input.file)))

export const useUploadDocumentVersion = () =>
  useKnowledgeMutation((input: { documentId: string; metadata: DocumentMetadataInput; file: File }) =>
    api.post<KnowledgeDocumentSummary>(`/admin/knowledge/documents/${input.documentId}/versions`, metadataForm(input.metadata, input.file)))

export const useCreateManualDocument = () =>
  useKnowledgeMutation((input: { metadata: DocumentMetadataInput; text: string }) =>
    api.post<KnowledgeDocumentSummary>('/admin/knowledge/documents/manual', input))

export const useUpdateDocument = () =>
  useKnowledgeMutation((input: { id: string; enabled?: boolean; approvalStatus?: ApprovalStatus; conditionTags?: string[] }) =>
    api.patch<KnowledgeDocumentSummary>(`/admin/knowledge/documents/${input.id}`, {
      enabled: input.enabled, approvalStatus: input.approvalStatus, conditionTags: input.conditionTags,
    }))

export const useReindexDocument = () =>
  useKnowledgeMutation((id: string) => api.post<KnowledgeDocumentSummary>(`/admin/knowledge/documents/${id}/reindex`))

export const useDeleteDocument = () =>
  useKnowledgeMutation((id: string) => api.delete<void>(`/admin/knowledge/documents/${id}`))

export const useKnowledgeSearch = () =>
  useMutation({
    mutationFn: (input: { query: string; topK: number; includeInactive: boolean }) =>
      api.post<KnowledgeSearchHit[]>('/admin/knowledge/search', input),
  })

// ---- Admin: rules, AI, audit, users, system ------------------------------------------------------------

export const useRules = () => useQuery({ queryKey: ['rules'], queryFn: () => api.get<RuleDefinition[]>('/admin/rules') })

export function useSetRuleEnabled() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: string; enabled: boolean; reason?: string }) =>
      api.put<RuleDefinition>(`/admin/rules/${input.id}/enabled`, { enabled: input.enabled, reason: input.reason }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rules'] }),
  })
}

export function usePublishRuleVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { ruleId: string; body: Record<string, unknown> }) =>
      api.post<RuleDefinition>(`/admin/rules/${input.ruleId}/versions`, input.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rules'] }),
  })
}

export const useAiStatus = () => useQuery({ queryKey: ['ai-status'], queryFn: () => api.get<AiStatus>('/admin/ai/status') })
export const useAiPrompts = () => useQuery({ queryKey: ['ai-prompts'], queryFn: () => api.get<PromptSummary[]>('/admin/ai/prompts') })
export const useAiExecutions = () => useQuery({ queryKey: ['ai-executions'], queryFn: () => api.get<AiExecutionSummary[]>('/admin/ai/executions?limit=50') })

export interface AuditFilters {
  eventType?: string
  userId?: string
  patientId?: string
  encounterId?: string
  from?: string
  to?: string
}

export const useAuditEvents = (filters: AuditFilters, page: number) =>
  useQuery({
    queryKey: ['audit', filters, page],
    queryFn: () => api.get<PageResponse<AuditEvent>>(`/admin/audit-events${query({ ...filters, page, size: 50 })}`),
    placeholderData: keepPreviousData,
  })

export const useUsers = () => useQuery({ queryKey: ['users'], queryFn: () => api.get<UserAccount[]>('/admin/users') })

export function useSetUserAccess() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: string; enabled: boolean }) => api.put<UserAccount>(`/admin/users/${input.id}/access`, { enabled: input.enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

export const useSystemHealth = () =>
  useQuery({ queryKey: ['system-health'], queryFn: () => api.get<SystemHealth>('/admin/system/health'), refetchInterval: 15_000 })

export const useSystemConfig = () =>
  useQuery({ queryKey: ['system-config'], queryFn: () => api.get<Record<string, unknown>>('/admin/system/config') })
