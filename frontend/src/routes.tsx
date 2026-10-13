import { Suspense, type ReactNode } from 'react'
import { LinearProgress } from '@mui/material'
import type { RouteObject } from 'react-router'
import type { Role } from './api/types'
import { RequireRole } from './auth/RequireRole'
import { AppShell } from './components/AppShell'
import {
  AdminHome, AiConfigurationPage, AuditPage, ClinicalRulesPage, KnowledgeBasePage, KnowledgeDocumentPage, KnowledgeSearchPage,
  SystemHealthPage, UsersPage,
} from './features/admin/lazyPages'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { EncounterWorkstation } from './features/encounter/EncounterWorkstation'
import { HomeRedirect, NotFound } from './features/home/HomeRedirect'
import { PatientDetailPage } from './features/patients/PatientDetailPage'
import { PatientsPage } from './features/patients/PatientsPage'

const KNOWLEDGE: Role[] = ['ADMIN', 'KNOWLEDGE_MANAGER']
const guard = (roles: Role[], element: ReactNode) => (
  <RequireRole roles={roles}><Suspense fallback={<LinearProgress />}>{element}</Suspense></RequireRole>
)

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomeRedirect /> },
      { path: 'dashboard', element: guard(['CLINICIAN'], <DashboardPage />) },
      { path: 'patients', element: guard(['CLINICIAN'], <PatientsPage />) },
      { path: 'patients/:patientId', element: guard(['CLINICIAN'], <PatientDetailPage />) },
      { path: 'encounters/:encounterId', element: guard(['CLINICIAN'], <EncounterWorkstation />) },
      { path: 'admin', element: guard(['ADMIN'], <AdminHome />) },
      { path: 'admin/users', element: guard(['ADMIN'], <UsersPage />) },
      { path: 'admin/knowledge', element: guard(KNOWLEDGE, <KnowledgeBasePage />) },
      { path: 'admin/knowledge/search', element: guard(KNOWLEDGE, <KnowledgeSearchPage />) },
      { path: 'admin/knowledge/documents/:documentId', element: guard(KNOWLEDGE, <KnowledgeDocumentPage />) },
      { path: 'admin/rules', element: guard(['ADMIN'], <ClinicalRulesPage />) },
      { path: 'admin/ai', element: guard(['ADMIN'], <AiConfigurationPage />) },
      { path: 'admin/audit', element: guard(['ADMIN'], <AuditPage />) },
      { path: 'admin/health', element: guard(['ADMIN'], <SystemHealthPage />) },
      { path: '*', element: <NotFound /> },
    ],
  },
]

