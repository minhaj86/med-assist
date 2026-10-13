/*
 * SYNTHETIC TEST DATA - fictional patient and content for automated tests only.
 * Clinical content is illustrative and asserts UI structure, not medical truth.
 */
import type {
  AssessmentRun, AssessmentVersion, EncounterChart, KnowledgeDocumentSummary, KnowledgeStatus, Provenance, SafetyCheck,
  VersionSummary,
} from '../api/types'

const T0 = '2026-01-15T09:00:00Z'
export const ENCOUNTER_ID = '11111111-1111-4111-8111-111111111111'
export const PATIENT_ID = '22222222-2222-4222-8222-222222222222'
export const VERSION_ID = '33333333-3333-4333-8333-333333333333'
export const RUN_ID = '44444444-4444-4444-8444-444444444444'
export const DIFFERENTIAL_ID = '55555555-5555-4555-8555-555555555555'
export const RED_FLAG_ID = '66666666-6666-4666-8666-666666666666'

const provenance: Provenance = { source: 'CLINICIAN', recordedBy: 'dr.demo', recordedAt: T0 }

export const chartFixture: EncounterChart = {
  patient: {
    id: PATIENT_ID, externalPatientId: 'SYN-TEST-01', firstName: 'Testa', lastName: 'Synthetic', dateOfBirth: '1970-03-01',
    ageYears: 56, biologicalSex: 'FEMALE', synthetic: true, provenance: 'CLINICIAN', createdAt: T0, updatedAt: T0,
  },
  encounter: {
    id: ENCOUNTER_ID, patientId: PATIENT_ID, encounterType: 'EMERGENCY', status: 'IN_PROGRESS', chiefComplaint: 'Chest pain (synthetic case)',
    startedAt: T0, clinicianId: 'dr.demo', provenance: 'CLINICIAN', createdAt: T0, updatedAt: T0,
  },
  symptoms: [{
    id: 's1', encounterId: ENCOUNTER_ID, concept: { system: 'LOCAL', displayName: 'Chest pain', verificationStatus: 'UNVERIFIED' },
    presence: 'PRESENT', onsetPattern: 'SUDDEN', severity: 7, provenance, enteredInError: false, createdAt: T0, updatedAt: T0,
  }],
  vitalSigns: [{
    id: 'v1', encounterId: ENCOUNTER_ID, category: 'VITAL_SIGN', vitalSignType: 'HEART_RATE',
    concept: { system: 'LOINC', code: '8867-4', displayName: 'Heart rate', verificationStatus: 'CURATED_REFERENCE' },
    numericValue: 128, unit: '/min', displayValue: '128 /min', measuredAt: T0, provenance, enteredInError: false,
  }],
  labs: [],
  medications: [],
  allergies: [],
  history: [],
}

export const safetyCheckFixture: SafetyCheck = {
  evaluatedAt: T0,
  rulesEvaluated: 9,
  disabledRules: [],
  ruleResults: [],
  scores: [],
  alerts: [{
    origin: 'RULE', severity: 'URGENT', title: 'Very high heart rate (demonstration rule)', explanation: 'Heart rate 128 /min exceeds the example threshold.',
    recommendedAction: 'Clinical review required.', sourceReference: 'demo.vitals.tachycardia@1.0.0', supportingFactKeys: ['F3'], demonstrationOnly: true,
  }],
}

export const runningRun: AssessmentRun = {
  runId: RUN_ID, assessmentId: 'a1', encounterId: ENCOUNTER_ID, status: 'RUNNING', currentStage: 'RUNNING_RULES', includeAi: true, requestedAt: T0,
  stages: [{ stage: 'QUEUED', at: T0 }, { stage: 'RUNNING_RULES', at: T0 }],
}

export const completedRun: AssessmentRun = {
  ...runningRun, status: 'COMPLETED', currentStage: 'COMPLETED', versionId: VERSION_ID, completedAt: T0,
  stages: [...runningRun.stages, { stage: 'GENERATING_REASONING', at: T0 }, { stage: 'COMPLETED', at: T0 }],
}

export const versionSummary: VersionSummary = {
  id: VERSION_ID, encounterId: ENCOUNTER_ID, versionNumber: 1, createdAt: T0, createdBy: 'dr.demo', aiStatus: 'COMPLETED',
  criticalAlerts: 0, urgentAlerts: 1, alertCount: 1, differentialCount: 2, leadingConsideration: 'Condition Alpha (synthetic)', inputChangeCount: 0,
}

export const versionFixture: AssessmentVersion = {
  id: VERSION_ID, assessmentId: 'a1', encounterId: ENCOUNTER_ID, patientId: PATIENT_ID, versionNumber: 1, createdAt: T0, createdBy: 'dr.demo',
  workflowStatus: 'IN_PROGRESS', inputHash: 'abcdef0123456789abcdef',
  ai: { status: 'COMPLETED', message: 'AI reasoning validated against recorded facts and retrieved knowledge.' },
  facts: [
    { key: 'F1', category: 'DEMOGRAPHICS', description: '56-year-old female' },
    { key: 'F2', category: 'SYMPTOM', description: 'Chest pain present, sudden onset', provenance: 'CLINICIAN' },
    { key: 'F3', category: 'VITAL_SIGN', description: 'Heart rate 128 /min', provenance: 'CLINICIAN' },
  ],
  changes: { inputChanges: [], outputChanges: [] },
  safetyAlerts: [{
    id: 'sa1', origin: 'RULE', severity: 'URGENT', title: 'Very high heart rate (demonstration rule)', supportingFactKeys: ['F3'], evidenceKeys: [],
    supportStatus: 'NOT_APPLICABLE', demonstrationOnly: true,
  }],
  aiRedFlags: [{
    id: RED_FLAG_ID, origin: 'AI', severity: 'URGENT', title: 'Possible time-critical cause of chest pain', explanation: 'Synthetic red flag text.',
    supportingFactKeys: ['F2', 'F3'], evidenceKeys: ['K1'], supportStatus: 'SUPPORTED', demonstrationOnly: false, reviewState: 'AI_GENERATED',
  }],
  ruleResults: [],
  scores: [{
    scoreId: 'demo-vital-sign-score', name: 'Demonstration vital-sign score', version: '1.0.0', score: 1, maxScore: 9,
    calculationDetails: [{ variable: 'Heart rate', value: '128 /min', points: 1, rationale: 'Above example band', factKey: 'F3' }],
    inputVariables: {}, missingVariables: ['Respiratory rate'], interpretation: 'Demonstration only.', reference: 'Synthetic demonstration calculator',
    demonstrationOnly: true,
  }],
  evidence: [{
    evidenceKey: 'K1', chunkId: 'c1', documentId: 'd1', documentTitle: 'Synthetic chest pain note', documentVersion: '1.0',
    sourceOrganization: 'MedAssist demo content', section: 'Assessment', retrievalSimilarity: 0.42, excerpt: 'Synthetic knowledge excerpt.',
    syntheticSource: true,
  }],
  differentials: [
    {
      id: DIFFERENTIAL_ID, rank: 1, conditionName: 'Condition Alpha (synthetic)', codeVerification: 'UNVERIFIED', confidence: 'MODERATE',
      method: 'AI_REASONED', grounding: 'GROUNDED', whyConsidered: 'Synthetic rationale citing recorded facts.',
      supportingEvidence: [{ id: 'st1', type: 'SUPPORTING', text: 'Sudden-onset chest pain', factKeys: ['F2'], evidenceKeys: ['K1'], support: 'SUPPORTED' }],
      contradictingEvidence: [],
      missingInformation: [{ id: 'st2', type: 'MISSING_INFORMATION', text: 'ECG not yet recorded', factKeys: [], evidenceKeys: [], support: 'UNSUPPORTED' }],
      suggestedInvestigations: [{ id: 'inv1', name: 'ECG', reason: 'Helps distinguish', proposedBy: 'AI', evidenceKeys: ['K1'], support: 'SUPPORTED', reviewState: 'AI_GENERATED' }],
      distinguishingFeatures: 'Synthetic distinguishing features.', reasoningSummary: 'Synthetic reasoning summary.',
      citations: ['K1'], validationNotes: [], reviewState: 'AI_GENERATED',
    },
    {
      id: 'd2', rank: 2, conditionName: 'Condition Beta (synthetic)', codeVerification: 'UNVERIFIED', method: 'KNOWLEDGE_LINKED',
      grounding: 'PARTIALLY_GROUNDED', supportingEvidence: [], contradictingEvidence: [], missingInformation: [], suggestedInvestigations: [],
      citations: ['K1'], validationNotes: [], reviewState: 'AI_GENERATED',
    },
  ],
  uncertainties: ['Insufficient information to reliably assess Condition Beta.'],
  additionalQuestions: ['Has an ECG been performed?'],
  dataQualityWarnings: [],
  validationIssues: [],
  executedRules: [{ definitionId: 'r1', ruleId: 'demo.vitals.tachycardia', version: '1.0.0' }],
  disabledRules: [],
}

export const knowledgeDocs: KnowledgeDocumentSummary[] = [
  {
    id: 'doc-1', seriesId: 'series-1', title: 'Synthetic chest pain note', documentType: 'SYNTHETIC_DEMO', sourceOrganization: 'MedAssist demo content',
    version: '1.0', publicationDate: '2025-06-01', approvalStatus: 'APPROVED', enabled: true, ingestionStatus: 'INDEXED', chunkCount: 6,
    lastIndexedAt: T0, embeddingModel: 'local-hashing-v1:384', synthetic: true, uploadedAt: T0, uploadedBy: 'km.demo', usableForClinicalRetrieval: true,
  },
  {
    id: 'doc-2', seriesId: 'series-2', title: 'Draft local protocol (synthetic)', documentType: 'LOCAL_POLICY', sourceOrganization: 'Demo hospital',
    version: '0.1', approvalStatus: 'DRAFT', enabled: true, ingestionStatus: 'FAILED', ingestionError: 'No extractable text', chunkCount: 0,
    synthetic: true, uploadedAt: T0, uploadedBy: 'km.demo', usableForClinicalRetrieval: false,
  },
]

export const knowledgeStatus: KnowledgeStatus = {
  embeddingProvider: 'local-hashing', embeddingModel: 'local-hashing-v1', embeddingDimensions: 384,
  chunking: { maxChars: 1200, overlapChars: 150, minChars: 80 }, retrieval: { topK: 8, minSimilarity: 0.15, maxChunksPerDocument: 3 },
  documents: 2, retrievableDocuments: 1, failedDocuments: 1,
}

export const problem = (status: number, title: string, detail: string) => ({ type: 'about:blank', title, status, detail, correlationId: 'test-correlation' })
