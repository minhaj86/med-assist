import { lazy } from 'react'

// Administration screens are code-split so clinical users never download them.
export const AdminHome = lazy(() => import('./AdminHome').then((m) => ({ default: m.AdminHome })))
export const AiConfigurationPage = lazy(() => import('./AiConfigurationPage').then((m) => ({ default: m.AiConfigurationPage })))
export const AuditPage = lazy(() => import('./AuditPage').then((m) => ({ default: m.AuditPage })))
export const KnowledgeBasePage = lazy(() => import('./knowledge/KnowledgeBasePage').then((m) => ({ default: m.KnowledgeBasePage })))
export const KnowledgeDocumentPage = lazy(() => import('./knowledge/KnowledgeDocumentPage').then((m) => ({ default: m.KnowledgeDocumentPage })))
export const KnowledgeSearchPage = lazy(() => import('./knowledge/KnowledgeSearchPage').then((m) => ({ default: m.KnowledgeSearchPage })))
export const ClinicalRulesPage = lazy(() => import('./rules/ClinicalRulesPage').then((m) => ({ default: m.ClinicalRulesPage })))
export const SystemHealthPage = lazy(() => import('./SystemHealthPage').then((m) => ({ default: m.SystemHealthPage })))
export const UsersPage = lazy(() => import('./UsersPage').then((m) => ({ default: m.UsersPage })))
