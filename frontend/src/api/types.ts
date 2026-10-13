// Types mirroring the MedAssist REST API (backend DTOs).

export type Role = 'CLINICIAN' | 'ADMIN' | 'KNOWLEDGE_MANAGER'
export type DataProvenance = 'CLINICIAN' | 'FHIR' | 'LAB_IMPORT' | 'PATIENT_REPORTED' | 'DEVICE' | 'AI_INFERRED'
export type CodingSystem = 'SNOMED_CT' | 'ICD_10' | 'LOINC' | 'LOCAL'
export type ConceptVerificationStatus = 'VERIFIED_TERMINOLOGY_SERVICE' | 'CURATED_REFERENCE' | 'PROVIDED_UNVERIFIED' | 'UNVERIFIED'
export type ClinicalSeverity = 'INFO' | 'WARNING' | 'URGENT' | 'CRITICAL'
export type AlertOrigin = 'RULE' | 'CLINICAL_SCORE' | 'AI' | 'KNOWLEDGE_BASE' | 'CLINICIAN'
export type BiologicalSex = 'FEMALE' | 'MALE' | 'INTERSEX' | 'UNKNOWN'
export type EncounterType = 'EMERGENCY' | 'PRIMARY_CARE' | 'OUTPATIENT' | 'INPATIENT' | 'TELEMEDICINE'
export type EncounterStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface Me {
  id: string
  username: string
  displayName: string
  roles: Role[]
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface Provenance {
  source: DataProvenance
  sourceDetail?: string
  recordedBy: string
  recordedAt: string
  confirmedBy?: string
  confirmedAt?: string
}

export interface Concept {
  system?: CodingSystem
  code?: string
  displayName: string
  verificationStatus: ConceptVerificationStatus
}

export interface Patient {
  id: string
  externalPatientId: string
  firstName: string
  lastName: string
  dateOfBirth: string
  ageYears: number
  biologicalSex: BiologicalSex
  gender?: string
  contactPhone?: string
  synthetic: boolean
  provenance: DataProvenance
  createdAt: string
  updatedAt: string
}

export type PatientSummary = Pick<Patient, 'id' | 'externalPatientId' | 'firstName' | 'lastName' | 'dateOfBirth' | 'ageYears' | 'biologicalSex' | 'synthetic'>

export interface Encounter {
  id: string
  patientId: string
  encounterType: EncounterType
  status: EncounterStatus
  chiefComplaint?: string
  startedAt: string
  completedAt?: string
  clinicianId: string
  provenance: DataProvenance
  createdAt: string
  updatedAt: string
}

interface ClinicalRecord {
  id: string
  provenance: Provenance
  enteredInError: boolean
  enteredInErrorReason?: string
}

export type SymptomPresence = 'PRESENT' | 'ABSENT'
export type OnsetPattern = 'SUDDEN' | 'GRADUAL' | 'UNKNOWN'

export interface Symptom extends ClinicalRecord {
  encounterId: string
  concept: Concept
  presence: SymptomPresence
  onsetPattern?: OnsetPattern
  onsetAt?: string
  duration?: string
  severity?: number
  location?: string
  character?: string
  radiation?: string
  aggravatingFactors?: string
  relievingFactors?: string
  associatedSymptoms?: string
  notes?: string
  createdAt: string
  updatedAt: string
}

export type VitalSignType =
  | 'SYSTOLIC_BLOOD_PRESSURE' | 'DIASTOLIC_BLOOD_PRESSURE' | 'HEART_RATE' | 'RESPIRATORY_RATE'
  | 'OXYGEN_SATURATION' | 'BODY_TEMPERATURE' | 'BODY_WEIGHT' | 'BODY_HEIGHT' | 'BODY_MASS_INDEX' | 'CONSCIOUSNESS_LEVEL'
export type ConsciousnessLevel = 'ALERT' | 'NEW_CONFUSION' | 'VOICE' | 'PAIN' | 'UNRESPONSIVE'

export interface VitalSign extends ClinicalRecord {
  encounterId: string
  category: 'VITAL_SIGN'
  vitalSignType: VitalSignType
  concept: Concept
  numericValue?: number
  textValue?: string
  unit?: string
  displayValue: string
  measuredAt: string
}

export type AbnormalFlag = 'NORMAL' | 'LOW' | 'HIGH' | 'CRITICAL_LOW' | 'CRITICAL_HIGH' | 'ABNORMAL'

export interface LabResult extends ClinicalRecord {
  encounterId: string
  testCode?: string
  codingSystem?: CodingSystem
  testName: string
  codeVerification: ConceptVerificationStatus
  numericValue?: number
  textValue?: string
  unit?: string
  referenceRangeLow?: number
  referenceRangeHigh?: number
  abnormalFlag?: AbnormalFlag
  abnormalFlagSource: 'REPORTED' | 'DERIVED_FROM_REFERENCE_RANGE' | 'NONE'
  displayValue: string
  collectedAt: string
}

export type MedicationStatus = 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'STOPPED' | 'UNKNOWN'

export interface Medication extends ClinicalRecord {
  patientId: string
  encounterId?: string
  medicationName: string
  codingSystem?: CodingSystem
  code?: string
  dosage?: string
  frequency?: string
  route?: string
  startDate?: string
  endDate?: string
  status: MedicationStatus
  createdAt: string
}

export type AllergySeverity = 'MILD' | 'MODERATE' | 'SEVERE' | 'UNKNOWN'
export type AllergyStatus = 'ACTIVE' | 'INACTIVE' | 'RESOLVED'
export type AllergyCategory = 'MEDICATION' | 'FOOD' | 'ENVIRONMENT' | 'BIOLOGIC' | 'OTHER'

export interface Allergy extends ClinicalRecord {
  patientId: string
  allergen: string
  category?: AllergyCategory
  reaction?: string
  severity: AllergySeverity
  status: AllergyStatus
  createdAt: string
}

export type HistoryCategory = 'PAST_DIAGNOSIS' | 'FAMILY_HISTORY' | 'SURGICAL_HISTORY' | 'SMOKING' | 'ALCOHOL' | 'SOCIAL_HISTORY' | 'HOSPITALIZATION'
export type HistoryStatus = 'ACTIVE' | 'RESOLVED' | 'CURRENT' | 'FORMER' | 'NEVER' | 'UNKNOWN'

export interface HistoryEntry extends ClinicalRecord {
  patientId: string
  category: HistoryCategory
  concept?: Concept
  description: string
  relationship?: string
  status: HistoryStatus
  onsetDate?: string
  endDate?: string
  notes?: string
  createdAt: string
}

export interface EncounterChart {
  encounter: Encounter
  patient: Patient
  symptoms: Symptom[]
  vitalSigns: VitalSign[]
  labs: LabResult[]
  medications: Medication[]
  allergies: Allergy[]
  history: HistoryEntry[]
}

// --- Safety rules & scores ---------------------------------------------------------------------------------

export interface SupportingObservation {
  factKey: string
  sourceType: string
  sourceId: string
  description: string
}

export interface RuleResult {
  ruleId: string
  ruleVersion: string
  ruleName: string
  severity: ClinicalSeverity
  triggered: boolean
  explanation: string
  supportingObservations: SupportingObservation[]
  recommendedAction?: string
  references: string[]
  demonstrationOnly: boolean
  evaluationError: boolean
}

export interface ScoreComponent {
  variable: string
  value: string
  points: number
  rationale: string
  factKey: string
}

export interface ScoreResult {
  scoreId: string
  name: string
  version: string
  score?: number
  maxScore?: number
  calculationDetails: ScoreComponent[]
  inputVariables: Record<string, string>
  missingVariables: string[]
  interpretation: string
  reference: string
  demonstrationOnly: boolean
  alertSeverity?: ClinicalSeverity
  alertMessage?: string
}

export interface DerivedSafetyAlert {
  origin: AlertOrigin
  severity: ClinicalSeverity
  title: string
  explanation?: string
  recommendedAction?: string
  sourceReference?: string
  supportingFactKeys: string[]
  demonstrationOnly: boolean
}

export interface SafetyCheck {
  evaluatedAt: string
  alerts: DerivedSafetyAlert[]
  rulesEvaluated: number
  disabledRules: string[]
  ruleResults: RuleResult[]
  scores: ScoreResult[]
}

// --- Assessment -------------------------------------------------------------------------------------------

export type AssessmentStage =
  | 'QUEUED' | 'VALIDATING_INPUT' | 'NORMALIZING_TERMINOLOGY' | 'RUNNING_RULES' | 'CALCULATING_SCORES'
  | 'SEARCHING_KNOWLEDGE' | 'GENERATING_CANDIDATES' | 'GENERATING_REASONING' | 'VALIDATING_RESPONSE' | 'SAVING'
  | 'COMPLETED' | 'FAILED'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type AiReasoningStatus = 'COMPLETED' | 'NOT_REQUESTED' | 'NOT_CONFIGURED' | 'UNAVAILABLE' | 'OUTPUT_REJECTED'
export type ConfidenceLevel = 'LOW' | 'MODERATE' | 'HIGH'
export type AssessmentMethod = 'AI_REASONED' | 'KNOWLEDGE_LINKED'
export type GroundingStatus = 'GROUNDED' | 'PARTIALLY_GROUNDED' | 'UNGROUNDED'
export type SupportStatus = 'SUPPORTED' | 'UNSUPPORTED' | 'WITHHELD' | 'NOT_APPLICABLE'
export type ReviewState = 'AI_GENERATED' | 'REVIEWED' | 'ACCEPTED' | 'REJECTED' | 'MODIFIED' | 'FINALIZED'
export type FeedbackAction = 'ACCEPTED' | 'REJECTED' | 'UNCERTAIN' | 'NOT_RELEVANT' | 'NEEDS_MORE_INFORMATION'
export type SuggestionType = 'DIFFERENTIAL' | 'RED_FLAG' | 'INVESTIGATION'
export type WorkflowStatus = 'IN_PROGRESS' | 'FINALIZED'

export interface AssessmentRun {
  runId: string
  assessmentId: string
  encounterId: string
  status: RunStatus
  currentStage: AssessmentStage
  stages: { stage: AssessmentStage; at: string }[]
  includeAi: boolean
  requestedAt: string
  completedAt?: string
  versionId?: string
  failureReason?: string
}

export interface VersionSummary {
  id: string
  encounterId: string
  versionNumber: number
  createdAt: string
  createdBy: string
  aiStatus: AiReasoningStatus
  criticalAlerts: number
  urgentAlerts: number
  alertCount: number
  differentialCount: number
  leadingConsideration?: string
  inputChangeCount: number
}

export interface FeedbackView {
  action: FeedbackAction
  clinicianId: string
  at: string
  reason?: string
  modifiedText?: string
}

export interface AlertView {
  id: string
  origin: AlertOrigin
  severity: ClinicalSeverity
  title: string
  explanation?: string
  recommendedAction?: string
  sourceReference?: string
  supportingFactKeys: string[]
  evidenceKeys: string[]
  supportStatus: SupportStatus
  demonstrationOnly: boolean
  reviewState?: ReviewState
  latestFeedback?: FeedbackView
}

export interface StatementView {
  id: string
  type: 'SUPPORTING' | 'CONTRADICTING' | 'MISSING_INFORMATION'
  text: string
  factKeys: string[]
  evidenceKeys: string[]
  support: SupportStatus
}

export interface InvestigationSuggestion {
  id: string
  name: string
  reason?: string
  proposedBy: 'AI' | 'RULE' | 'CLINICAL_SCORE' | 'KNOWLEDGE_BASE' | 'CLINICIAN'
  evidenceKeys: string[]
  support: SupportStatus
  reviewState: ReviewState
  latestFeedback?: FeedbackView
}

export interface Differential {
  id: string
  rank: number
  rankBasis?: string
  conditionName: string
  codingSystem?: CodingSystem
  code?: string
  codeVerification: ConceptVerificationStatus
  confidence?: ConfidenceLevel
  originalConfidence?: ConfidenceLevel
  method: AssessmentMethod
  grounding: GroundingStatus
  whyConsidered?: string
  supportingEvidence: StatementView[]
  contradictingEvidence: StatementView[]
  missingInformation: StatementView[]
  suggestedInvestigations: InvestigationSuggestion[]
  distinguishingFeatures?: string
  reasoningSummary?: string
  citations: string[]
  validationNotes: string[]
  reviewState: ReviewState
  latestFeedback?: FeedbackView
}

export interface EvidenceView {
  evidenceKey: string
  chunkId: string
  documentId: string
  documentTitle: string
  documentVersion?: string
  sourceOrganization?: string
  section?: string
  page?: number
  publicationDate?: string
  retrievalSimilarity: number
  excerpt: string
  syntheticSource: boolean
}

export interface FactView {
  key: string
  category: string
  description: string
  provenance?: DataProvenance
}

export interface Change {
  type: string
  category: string
  description: string
  previousDescription?: string
}

export interface AssessmentVersion {
  id: string
  assessmentId: string
  encounterId: string
  patientId: string
  versionNumber: number
  createdAt: string
  createdBy: string
  workflowStatus: WorkflowStatus
  inputHash: string
  ai: { status: AiReasoningStatus; message?: string; executionId?: string }
  facts: FactView[]
  changes: { previousVersionNumber?: number; inputChanges: Change[]; outputChanges: Change[] }
  safetyAlerts: AlertView[]
  aiRedFlags: AlertView[]
  ruleResults: RuleResult[]
  scores: ScoreResult[]
  evidence: EvidenceView[]
  differentials: Differential[]
  uncertainties: string[]
  additionalQuestions: string[]
  dataQualityWarnings: string[]
  validationIssues: string[]
  executedRules: { definitionId: string; ruleId: string; version: string }[]
  disabledRules: string[]
}

export type DiagnosisCertainty = 'PROVISIONAL' | 'DIFFERENTIAL' | 'CONFIRMED'

export interface FinalDiagnosis {
  conditionName: string
  codingSystem?: CodingSystem
  code?: string
  codeVerification: ConceptVerificationStatus
  certainty: DiagnosisCertainty
  sourceSuggestionId?: string
}

export interface FinalAssessment {
  assessmentId: string
  encounterId: string
  status: WorkflowStatus
  finalizedVersionId?: string
  summary?: string
  plan?: string
  diagnoses: FinalDiagnosis[]
  finalizedBy?: string
  finalizedAt?: string
}

export type InvestigationStatus = 'SUGGESTED' | 'ACCEPTED' | 'REJECTED' | 'ORDERED' | 'COMPLETED'

export interface Investigation {
  id: string
  encounterId: string
  name: string
  proposedBy: InvestigationSuggestion['proposedBy']
  reason?: string
  supportingEvidence: string[]
  status: InvestigationStatus
  sourceSuggestionId?: string
  sourceAssessmentVersionId?: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

// --- Assistant --------------------------------------------------------------------------------------------

export interface AssistantAnswer {
  conversationId: string
  messageId?: string
  status: 'ANSWERED' | 'OUTPUT_REJECTED'
  message: string
  answer?: string
  statements: { text: string; factKeys: string[]; evidenceKeys: string[]; support: SupportStatus }[]
  insufficientInformation: boolean
  proposedUpdates: { category: 'SYMPTOM' | 'HISTORY'; description: string; rationale: string; proposalReference: string }[]
  followUpQuestions: string[]
  evidence: (Omit<EvidenceView, 'chunkId'>)[]
  aiExecutionId?: string
  createdAt: string
}

export interface AssistantMessage {
  id: string
  conversationId: string
  role: 'CLINICIAN' | 'ASSISTANT'
  content: string
  structured?: {
    statements?: AssistantAnswer['statements']
    proposedUpdates?: AssistantAnswer['proposedUpdates']
    followUpQuestions?: string[]
    insufficientInformation?: boolean
    evidenceKeys?: string[]
  }
  aiExecutionId?: string
  createdAt: string
}

// --- Dashboard --------------------------------------------------------------------------------------------

export interface DashboardEncounter {
  encounterId: string
  patient: PatientSummary & { biologicalSex: string }
  encounterType: EncounterType
  status: EncounterStatus
  chiefComplaint?: string
  startedAt: string
  clinicianId: string
  mine: boolean
  assessmentWorkflowStatus?: WorkflowStatus
  latestAssessment?: { versionId: string; versionNumber: number; createdAt: string; aiStatus: AiReasoningStatus; criticalAlerts: number; urgentAlerts: number }
}

export interface Dashboard {
  since: string
  todaysEncounters: DashboardEncounter[]
  recentPatients: PatientSummary[]
  incompleteAssessments: DashboardEncounter[]
  criticalAlerts: { encounterId: string; patient: PatientSummary; severity: ClinicalSeverity; origin: AlertOrigin; title: string; demonstrationOnly: boolean; versionNumber: number; assessedAt: string }[]
  recentAssessments: { versionId: string; encounterId: string; patient: PatientSummary; versionNumber: number; createdAt: string; aiStatus: AiReasoningStatus; alertCount: number; leadingConsideration?: string }[]
}

// --- Admin ------------------------------------------------------------------------------------------------

export type DocumentType = 'CLINICAL_GUIDELINE' | 'CLINICAL_PROTOCOL' | 'REFERENCE_TEXT' | 'LOCAL_POLICY' | 'DRUG_INFORMATION' | 'SYNTHETIC_DEMO'
export type ApprovalStatus = 'DRAFT' | 'APPROVED' | 'RETIRED'
export type IngestionStatus = 'PENDING' | 'EXTRACTING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXED' | 'FAILED'

export interface KnowledgeDocumentSummary {
  id: string
  seriesId: string
  title: string
  documentType: DocumentType
  sourceOrganization: string
  version: string
  publicationDate?: string
  reviewDate?: string
  approvalStatus: ApprovalStatus
  enabled: boolean
  ingestionStatus: IngestionStatus
  ingestionError?: string
  chunkCount: number
  lastIndexedAt?: string
  embeddingModel?: string
  synthetic: boolean
  supersededById?: string
  uploadedAt: string
  uploadedBy: string
  usableForClinicalRetrieval: boolean
}

export interface KnowledgeDocumentDetail {
  summary: KnowledgeDocumentSummary
  sourceUrl?: string
  jurisdiction?: string
  checksum: string
  contentType: string
  originalFilename?: string
  sizeBytes: number
  pageCount?: number
  conditionTags: string[]
  supersedesId?: string
  embeddedChunks: number
  currentEmbeddingModel: string
  embeddingsCurrent: boolean
  extractedText?: string
  versions: KnowledgeDocumentSummary[]
}

export interface KnowledgeChunk {
  id: string
  chunkIndex: number
  section?: string
  pageNumber?: number
  content: string
  charCount: number
  tokenEstimate: number
  metadata: Record<string, unknown>
}

export interface KnowledgeSearchHit {
  chunkId: string
  documentId: string
  documentTitle: string
  documentVersion: string
  sourceOrganization: string
  section?: string
  page?: number
  retrievalSimilarity: number
  content: string
  metadata: Record<string, unknown>
  usableForClinicalRetrieval: boolean
}

export interface KnowledgeStatus {
  embeddingProvider: string
  embeddingModel: string
  embeddingDimensions: number
  chunking: { maxChars: number; overlapChars: number; minChars: number }
  retrieval: { topK: number; minSimilarity: number; maxChunksPerDocument: number }
  documents: number
  retrievableDocuments: number
  failedDocuments: number
}

export interface DocumentMetadataInput {
  title: string
  documentType: DocumentType
  sourceOrganization: string
  sourceUrl?: string
  jurisdiction?: string
  version: string
  publicationDate?: string
  reviewDate?: string
  approvalStatus: ApprovalStatus
  conditionTags: string[]
}

export type RuleCategory = 'CARDIOVASCULAR' | 'RESPIRATORY' | 'NEUROLOGICAL' | 'INFECTION' | 'LABORATORY' | 'GENERAL'

export interface RuleDefinition {
  id: string
  ruleId: string
  version: string
  name: string
  category: RuleCategory
  severity: ClinicalSeverity
  logicKey: string
  parameters: unknown
  description: string
  recommendedAction: string
  references: string[]
  source: string
  approvedBy: string
  demonstrationOnly: boolean
  effectiveFrom: string
  effectiveUntil?: string
  enabled: boolean
  lifecycleStatus: 'ACTIVE' | 'SCHEDULED' | 'SUPERSEDED'
  supersedesId?: string
  changeReason?: string
  createdAt: string
  createdBy: string
}

export interface PromptSummary {
  promptId: string
  version: string
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  checksum: string
  outputSchemaId: string
  intendedModel?: string
  description?: string
  createdAt: string
}

export interface AiStatus {
  provider: string
  model?: string
  endpoint?: string
  enabled: boolean
  status: string
  temperature: number
  responseFormat: string
  maxAttempts: number
  lastSuccessfulRequestAt?: string
  lastFailedRequestAt?: string
  activePrompts: PromptSummary[]
  embeddingProvider: string
  embeddingModel: string
  embeddingDimensions: number
  terminologySources: string
  apiKeyConfigured: boolean
}

export interface AiExecutionSummary {
  id: string
  purpose: string
  provider: string
  requestedModel?: string
  reportedModel?: string
  promptId: string
  promptVersion: string
  temperature: number
  startedAt: string
  durationMs: number
  attempts: number
  status: 'SUCCEEDED' | 'VALIDATION_FAILED' | 'PROVIDER_ERROR'
  errorCategory?: string
  validationSummary: unknown
  validationIssues: string[]
  promptTokens?: number
  completionTokens?: number
  encounterId?: string
}

export interface AuditEvent {
  id: string
  timestamp: string
  userId?: string
  patientId?: string
  encounterId?: string
  eventType: string
  entityType?: string
  entityId?: string
  metadata: Record<string, unknown>
  correlationId?: string
  outcome: 'SUCCESS' | 'FAILURE'
}

export interface UserAccount {
  id: string
  username: string
  displayName?: string
  email?: string
  roles: Role[]
  enabled: boolean
  firstSeenAt: string
  lastSeenAt: string
}

export interface SystemHealth {
  overall: 'UP' | 'DEGRADED' | 'DOWN' | 'DISABLED'
  applicationVersion: string
  uptimeSeconds: number
  javaVersion: string
  activeProfiles: string[]
  checks: { component: string; status: 'UP' | 'DEGRADED' | 'DOWN' | 'DISABLED'; detail: string; data: Record<string, unknown> }[]
}
