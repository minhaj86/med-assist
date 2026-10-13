package com.medassist.support;

import com.medassist.common.domain.DataProvenance;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContext.LabFact;
import com.medassist.diagnosis.context.ClinicalContext.SymptomFact;
import com.medassist.diagnosis.context.ClinicalContext.VitalSignFact;
import com.medassist.diagnosis.context.ClinicalFact;
import com.medassist.diagnosis.context.FactCategory;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.AbnormalFlagSource;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.domain.BiologicalSex;
import com.medassist.symptom.domain.OnsetPattern;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builder for synthetic clinical contexts used by unit tests (no real patient data). */
public final class ContextFixtures {

    private final List<SymptomFact> symptoms = new ArrayList<>();
    private final List<VitalSignFact> vitals = new ArrayList<>();
    private final List<LabFact> labs = new ArrayList<>();
    private final List<ClinicalFact> facts = new ArrayList<>();
    private int key = 1;

    public static ContextFixtures context() {
        return new ContextFixtures();
    }

    private String nextKey() {
        return "F" + key++;
    }

    public ContextFixtures vital(VitalSignType type, String value) {
        String k = nextKey();
        UUID id = UUID.randomUUID();
        vitals.add(new VitalSignFact(k, id, type, new BigDecimal(value), null, type.unit(), Instant.EPOCH,
                DataProvenance.CLINICIAN));
        facts.add(new ClinicalFact(k, FactCategory.VITAL_SIGN, type.displayName() + " " + value, "Observation", id,
                DataProvenance.CLINICIAN, Instant.EPOCH, "VITAL_SIGN:" + type));
        return this;
    }

    public ContextFixtures consciousness(ConsciousnessLevel level) {
        String k = nextKey();
        UUID id = UUID.randomUUID();
        vitals.add(new VitalSignFact(k, id, VitalSignType.CONSCIOUSNESS_LEVEL, null, level, null, Instant.EPOCH,
                DataProvenance.CLINICIAN));
        facts.add(new ClinicalFact(k, FactCategory.VITAL_SIGN, "Consciousness " + level, "Observation", id,
                DataProvenance.CLINICIAN, Instant.EPOCH, "VITAL_SIGN:CONSCIOUSNESS_LEVEL"));
        return this;
    }

    public ContextFixtures symptom(String name, boolean present, OnsetPattern onset, Integer severity) {
        String k = nextKey();
        UUID id = UUID.randomUUID();
        symptoms.add(new SymptomFact(k, id, name, present, onset, severity, null, null, null, null,
                DataProvenance.CLINICIAN, ConceptVerificationStatus.UNVERIFIED));
        facts.add(new ClinicalFact(k, present ? FactCategory.SYMPTOM : FactCategory.PERTINENT_NEGATIVE,
                name + (present ? ": present" : ": reported absent"), "Symptom", id, DataProvenance.CLINICIAN, null,
                "SYMPTOM:" + id));
        return this;
    }

    public ContextFixtures lab(String name, String value, AbnormalFlag flag, AbnormalFlagSource source) {
        String k = nextKey();
        UUID id = UUID.randomUUID();
        labs.add(new LabFact(k, id, name, null, new BigDecimal(value), null, "unit", flag, source, null, null,
                Instant.EPOCH, DataProvenance.LAB_IMPORT));
        facts.add(new ClinicalFact(k, FactCategory.LAB_RESULT, name + " " + value, "LabObservation", id,
                DataProvenance.LAB_IMPORT, Instant.EPOCH, "LAB:" + name.toLowerCase()));
        return this;
    }

    public ClinicalContext build() {
        return new ClinicalContext(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-06-01T10:00:00Z"), 56,
                BiologicalSex.MALE, EncounterType.EMERGENCY, "Synthetic complaint", List.copyOf(symptoms),
                List.copyOf(vitals), List.copyOf(labs), List.of(), List.of(), List.of(), List.copyOf(facts));
    }
}
