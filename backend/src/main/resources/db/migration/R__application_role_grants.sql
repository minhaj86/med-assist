-- Least-privilege runtime role. When the database role "${app_db_role}" exists (container/production setups), it
-- receives DML only - no DDL - and append-only tables are insert/select only. In single-user setups (tests) this
-- script is a no-op. Repeatable: re-applied whenever this file changes.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_db_role}') AND current_user <> '${app_db_role}' THEN
        EXECUTE 'GRANT USAGE ON SCHEMA public TO "${app_db_role}"';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "${app_db_role}"';
        EXECUTE 'REVOKE UPDATE, DELETE ON audit_events, assessment_versions, clinical_rule_results, '
             || 'clinical_score_results, safety_alerts, retrieved_evidence, differential_diagnoses, diagnostic_evidence, '
             || 'suggested_investigations, clinician_feedback, investigation_status_history FROM "${app_db_role}"';
        EXECUTE 'REVOKE DELETE ON clinical_rule_definitions, prompt_templates, patients, encounters, symptoms, '
             || 'observations, lab_observations, medications, allergies, medical_history FROM "${app_db_role}"';
        EXECUTE 'REVOKE ALL ON flyway_schema_history FROM "${app_db_role}"';
        EXECUTE 'GRANT SELECT ON flyway_schema_history TO "${app_db_role}"';
    END IF;
END
$$;
