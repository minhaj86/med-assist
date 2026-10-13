import { createContext, useContext } from 'react'
import type { AssessmentVersion, EncounterChart } from '../../api/types'

export type RightTab = 'explain' | 'knowledge' | 'assistant'

export interface Proposal {
  category: 'SYMPTOM' | 'HISTORY'
  description: string
  reference: string
}

export interface WorkstationState {
  encounterId: string
  chart: EncounterChart
  readOnly: boolean
  version?: AssessmentVersion
  selectedDifferentialId?: string
  selectDifferential: (id?: string) => void
  highlightedEvidence?: string
  showEvidence: (key: string) => void
  rightTab: RightTab
  setRightTab: (tab: RightTab) => void
  proposal?: Proposal
  reviewProposal: (proposal?: Proposal) => void
}

export const WorkstationContext = createContext<WorkstationState | null>(null)

export function useWorkstation(): WorkstationState {
  const ctx = useContext(WorkstationContext)
  if (!ctx) throw new Error('useWorkstation must be used inside the encounter workstation')
  return ctx
}
