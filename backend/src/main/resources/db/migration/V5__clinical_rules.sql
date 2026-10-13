-- Versioned clinical rule definitions. A rule version is immutable once published; only its operational
-- "enabled" switch and the end of its effective period may change (enforced below).
CREATE TABLE clinical_rule_definitions (
    id                 UUID PRIMARY KEY,
    rule_id            VARCHAR(60)   NOT NULL,
    version            VARCHAR(30)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    category           VARCHAR(30)   NOT NULL,
    severity           VARCHAR(20)   NOT NULL,
    logic_key          VARCHAR(80)   NOT NULL,
    parameters         JSONB         NOT NULL,
    description        VARCHAR(2000) NOT NULL,
    recommended_action VARCHAR(1000) NOT NULL,
    rule_references    JSONB         NOT NULL DEFAULT '[]'::jsonb,
    source             VARCHAR(500)  NOT NULL,
    approved_by        VARCHAR(200)  NOT NULL,
    demonstration_only BOOLEAN       NOT NULL,
    effective_from     TIMESTAMPTZ   NOT NULL,
    effective_until    TIMESTAMPTZ,
    enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
    supersedes_id      UUID REFERENCES clinical_rule_definitions (id),
    change_reason      VARCHAR(1000),
    created_at         TIMESTAMPTZ   NOT NULL,
    created_by         VARCHAR(100)  NOT NULL,
    CONSTRAINT uq_rule_version UNIQUE (rule_id, version),
    CONSTRAINT ck_rule_period CHECK (effective_until IS NULL OR effective_until > effective_from)
);
CREATE INDEX ix_rule_effective ON clinical_rule_definitions (rule_id, effective_from DESC);

CREATE OR REPLACE FUNCTION medassist_protect_rule_definition() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Clinical rule definitions cannot be deleted' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.rule_id IS DISTINCT FROM OLD.rule_id OR NEW.version IS DISTINCT FROM OLD.version
        OR NEW.name IS DISTINCT FROM OLD.name OR NEW.category IS DISTINCT FROM OLD.category
        OR NEW.severity IS DISTINCT FROM OLD.severity OR NEW.logic_key IS DISTINCT FROM OLD.logic_key
        OR NEW.parameters IS DISTINCT FROM OLD.parameters OR NEW.description IS DISTINCT FROM OLD.description
        OR NEW.recommended_action IS DISTINCT FROM OLD.recommended_action
        OR NEW.rule_references IS DISTINCT FROM OLD.rule_references OR NEW.source IS DISTINCT FROM OLD.source
        OR NEW.approved_by IS DISTINCT FROM OLD.approved_by
        OR NEW.demonstration_only IS DISTINCT FROM OLD.demonstration_only
        OR NEW.effective_from IS DISTINCT FROM OLD.effective_from OR NEW.supersedes_id IS DISTINCT FROM OLD.supersedes_id
        OR NEW.change_reason IS DISTINCT FROM OLD.change_reason
        OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'Clinical rule definitions are immutable; publish a new rule version instead'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.effective_until IS NOT NULL AND (NEW.effective_until IS NULL OR NEW.effective_until > OLD.effective_until) THEN
        RAISE EXCEPTION 'The effective period of a rule version cannot be extended' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER clinical_rule_definitions_protect
    BEFORE UPDATE OR DELETE ON clinical_rule_definitions
    FOR EACH ROW EXECUTE FUNCTION medassist_protect_rule_definition();

-- -------------------------------------------------------------------------------------------------
-- DEMONSTRATION RULE SET. All thresholds below are ILLUSTRATIVE EXAMPLES chosen to exercise the software.
-- They are NOT validated clinical guidance, have NOT been clinically approved and carry no references.
-- Replace with rules approved by your clinical governance process before any real-world use.
-- -------------------------------------------------------------------------------------------------
INSERT INTO clinical_rule_definitions (id, rule_id, version, name, category, severity, logic_key, parameters,
    description, recommended_action, rule_references, source, approved_by, demonstration_only, effective_from,
    enabled, created_at, created_by)
SELECT v.id::uuid, v.rule_id, '1.0.0-demo', v.name, v.category, v.severity, v.logic_key, v.parameters::jsonb,
       v.description,
       'Demonstration alert: review promptly according to local clinical protocols. Clinical evaluation is required.',
       '[]'::jsonb,
       'MedAssist demonstration rule set - illustrative example thresholds, NOT validated clinical guidance',
       'NOT CLINICALLY APPROVED (demonstration only)', TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00', TRUE, now(),
       'flyway-migration'
FROM (VALUES
    ('7f0c2a1e-0001-4a00-9000-000000000001', 'DEMO-RESP-001', 'Low oxygen saturation (demonstration threshold)',
     'RESPIRATORY', 'CRITICAL', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"OXYGEN_SATURATION","operator":"LT","threshold":90}]}',
     'Example rule: most recent SpO2 below an illustrative threshold.'),
    ('7f0c2a1e-0001-4a00-9000-000000000002', 'DEMO-RESP-002', 'High respiratory rate (demonstration threshold)',
     'RESPIRATORY', 'URGENT', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"RESPIRATORY_RATE","operator":"GE","threshold":25}]}',
     'Example rule: most recent respiratory rate at or above an illustrative threshold.'),
    ('7f0c2a1e-0001-4a00-9000-000000000003', 'DEMO-CV-001', 'Very high blood pressure (demonstration threshold)',
     'CARDIOVASCULAR', 'URGENT', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"SYSTOLIC_BLOOD_PRESSURE","operator":"GE","threshold":180},{"vitalSign":"DIASTOLIC_BLOOD_PRESSURE","operator":"GE","threshold":120}]}',
     'Example rule: systolic or diastolic blood pressure at or above illustrative thresholds.'),
    ('7f0c2a1e-0001-4a00-9000-000000000004', 'DEMO-CV-002', 'Low blood pressure (demonstration threshold)',
     'CARDIOVASCULAR', 'CRITICAL', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"SYSTOLIC_BLOOD_PRESSURE","operator":"LT","threshold":90}]}',
     'Example rule: systolic blood pressure below an illustrative threshold.'),
    ('7f0c2a1e-0001-4a00-9000-000000000005', 'DEMO-CV-003', 'Very high heart rate (demonstration threshold)',
     'CARDIOVASCULAR', 'URGENT', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"HEART_RATE","operator":"GT","threshold":130}]}',
     'Example rule: heart rate above an illustrative threshold.'),
    ('7f0c2a1e-0001-4a00-9000-000000000006', 'DEMO-CV-004', 'Very low heart rate (demonstration threshold)',
     'CARDIOVASCULAR', 'URGENT', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"HEART_RATE","operator":"LT","threshold":40}]}',
     'Example rule: heart rate below an illustrative threshold.'),
    ('7f0c2a1e-0001-4a00-9000-000000000007', 'DEMO-INF-001', 'Very high temperature (demonstration threshold)',
     'INFECTION', 'URGENT', 'vital-sign-threshold',
     '{"match":"ANY","conditions":[{"vitalSign":"BODY_TEMPERATURE","operator":"GE","threshold":40.0}]}',
     'Example rule: body temperature at or above an illustrative threshold.'),
    ('7f0c2a1e-0001-4a00-9000-000000000008', 'DEMO-INF-002', 'Fever with low blood pressure (demonstration screen)',
     'INFECTION', 'CRITICAL', 'vital-sign-threshold',
     '{"match":"ALL","conditions":[{"vitalSign":"BODY_TEMPERATURE","operator":"GE","threshold":38.0},{"vitalSign":"SYSTOLIC_BLOOD_PRESSURE","operator":"LT","threshold":100}]}',
     'Example combined rule: raised temperature together with low systolic blood pressure (illustrative thresholds).'),
    ('7f0c2a1e-0001-4a00-9000-000000000009', 'DEMO-NEURO-001', 'Reduced or altered consciousness (demonstration rule)',
     'NEUROLOGICAL', 'CRITICAL', 'consciousness-level',
     '{"abnormalLevels":["NEW_CONFUSION","VOICE","PAIN","UNRESPONSIVE"],"includeConfusionSymptom":true}',
     'Example rule: level of consciousness other than Alert, or confusion recorded as present.'),
    ('7f0c2a1e-0001-4a00-9000-000000000010', 'DEMO-NEURO-002', 'Sudden severe headache (demonstration rule)',
     'NEUROLOGICAL', 'URGENT', 'symptom-pattern',
     '{"symptom":"Headache","onsetPattern":"SUDDEN","minSeverity":8}',
     'Example rule: headache recorded with sudden onset and high self-reported severity (illustrative cut-off).'),
    ('7f0c2a1e-0001-4a00-9000-000000000011', 'DEMO-LAB-001', 'Laboratory-reported critical value (demonstration rule)',
     'LABORATORY', 'URGENT', 'critical-lab-flag',
     '{"flags":["CRITICAL_LOW","CRITICAL_HIGH"]}',
     'Example rule: surfaces results that the reporting laboratory flagged as critical. MedAssist applies no thresholds.')
) AS v(id, rule_id, name, category, severity, logic_key, parameters, description);
