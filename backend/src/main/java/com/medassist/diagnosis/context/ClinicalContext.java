package com.medassist.diagnosis.context;

import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.common.domain.DataProvenance;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.history.domain.HistoryCategory;
import com.medassist.history.domain.HistoryStatus;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.AbnormalFlagSource;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.domain.BiologicalSex;
import com.medassist.symptom.domain.OnsetPattern;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, pseudonymized snapshot of the clinical information for one encounter. Contains no names, external
 * identifiers or dates of birth. Rules, scores and AI reasoning all operate on this object.
 */
public record ClinicalContext(UUID encounterId, UUID patientId, Instant assembledAt, int ageYears,
                              BiologicalSex biologicalSex, EncounterType encounterType, String chiefComplaint,
                              List<SymptomFact> symptoms, List<VitalSignFact> vitalSigns, List<LabFact> labs,
                              List<MedicationFact> medications, List<AllergyFact> allergies,
                              List<HistoryFact> history, List<ClinicalFact> facts) {

    public record SymptomFact(String factKey, UUID symptomId, String displayName, boolean present,
                              OnsetPattern onsetPattern, Integer severity, String character, String location,
                              String radiation, String duration, DataProvenance provenance,
                              ConceptVerificationStatus conceptVerification) {

        public boolean is(String name) {
            return displayName.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT));
        }
    }

    public record VitalSignFact(String factKey, UUID observationId, VitalSignType type, BigDecimal value,
                                ConsciousnessLevel consciousnessLevel, String unit, Instant measuredAt,
                                DataProvenance provenance) {

        public String displayValue() {
            if (type == VitalSignType.CONSCIOUSNESS_LEVEL) {
                return consciousnessLevel == null ? "unknown" : consciousnessLevel.displayName();
            }
            return value.stripTrailingZeros().toPlainString() + " " + unit;
        }
    }

    public record LabFact(String factKey, UUID labId, String testName, String testCode, BigDecimal numericValue,
                          String textValue, String unit, AbnormalFlag abnormalFlag, AbnormalFlagSource flagSource,
                          BigDecimal referenceRangeLow, BigDecimal referenceRangeHigh, Instant collectedAt,
                          DataProvenance provenance) {
    }

    public record MedicationFact(String factKey, UUID medicationId, String name, String dosage, String frequency,
                                 String route) {
    }

    public record AllergyFact(String factKey, UUID allergyId, String allergen, String reaction,
                              AllergySeverity severity) {
    }

    public record HistoryFact(String factKey, UUID historyId, HistoryCategory category, String description,
                              String relationship, HistoryStatus status) {
    }

    public Optional<VitalSignFact> latestVital(VitalSignType type) {
        return vitalSigns.stream().filter(v -> v.type() == type).findFirst();
    }

    public List<SymptomFact> presentSymptoms() {
        return symptoms.stream().filter(SymptomFact::present).toList();
    }

    public List<SymptomFact> absentSymptoms() {
        return symptoms.stream().filter(s -> !s.present()).toList();
    }

    public Optional<SymptomFact> presentSymptom(String displayName) {
        return presentSymptoms().stream().filter(s -> s.is(displayName)).findFirst();
    }

    public Optional<ClinicalFact> fact(String key) {
        return facts.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    public boolean hasFact(String key) {
        return fact(key).isPresent();
    }
}
