package com.medassist.demo;

import com.medassist.allergy.application.AllergyService;
import com.medassist.allergy.domain.AllergyCategory;
import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.allergy.domain.AllergyStatus;
import com.medassist.common.domain.DataProvenance;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.history.application.MedicalHistoryService;
import com.medassist.history.domain.HistoryCategory;
import com.medassist.history.domain.HistoryStatus;
import com.medassist.history.domain.MedicalHistoryEntry;
import com.medassist.medication.application.MedicationService;
import com.medassist.medication.domain.Medication;
import com.medassist.medication.domain.MedicationStatus;
import com.medassist.observation.application.LabObservationService;
import com.medassist.observation.application.VitalSignService;
import com.medassist.observation.application.VitalSignService.Measurement;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.LabObservation;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.application.PatientCommands;
import com.medassist.patient.application.PatientService;
import com.medassist.patient.domain.BiologicalSex;
import com.medassist.patient.domain.PatientRepository;
import com.medassist.symptom.application.SymptomService;
import com.medassist.symptom.domain.OnsetPattern;
import com.medassist.symptom.domain.Symptom;
import com.medassist.symptom.domain.SymptomPresence;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Seeds COMPLETELY FICTIONAL patients for software demonstration ("SYNTHETIC TEST DATA"). Values are
 * illustrative only and do not describe real people or validated clinical scenarios. Never enabled in prod.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "medassist.demo.seed-enabled", havingValue = "true")
public class SyntheticDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataSeeder.class);
    static final String SEEDER_USER = "synthetic-data-seeder";

    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final EncounterService encounterService;
    private final SymptomService symptomService;
    private final VitalSignService vitalSignService;
    private final LabObservationService labService;
    private final MedicationService medicationService;
    private final AllergyService allergyService;
    private final MedicalHistoryService historyService;
    private final Clock clock;

    public SyntheticDataSeeder(PatientRepository patientRepository, PatientService patientService,
                               EncounterService encounterService, SymptomService symptomService,
                               VitalSignService vitalSignService, LabObservationService labService,
                               MedicationService medicationService, AllergyService allergyService,
                               MedicalHistoryService historyService, Clock clock) {
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.encounterService = encounterService;
        this.symptomService = symptomService;
        this.vitalSignService = vitalSignService;
        this.labService = labService;
        this.medicationService = medicationService;
        this.allergyService = allergyService;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (patientRepository.existsByExternalPatientId("SYN-0001")) {
            log.info("Synthetic demo patients already present - skipping seed");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(SEEDER_USER, null, List.of()));
        try {
            seedChestPain();
            seedFeverCough();
            seedHeadache();
            seedAbdominalPain();
            seedConfusionHypotension();
            log.info("Seeded 5 synthetic demonstration patients (SYNTHETIC TEST DATA)");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID patient(String id, String first, String last, int ageYears, BiologicalSex sex) {
        LocalDate dob = LocalDate.now(clock).minusYears(ageYears).minusDays(37);
        return patientService.register(new PatientCommands.RegisterPatient(id, first, last, dob, sex, null, null,
                true, DataProvenance.CLINICIAN)).getId();
    }

    private UUID encounter(UUID patientId, EncounterType type, String complaint, Duration ago) {
        return encounterService.start(patientId, type, complaint, Instant.now(clock).minus(ago),
                DataProvenance.CLINICIAN).getId();
    }

    private void symptom(UUID encounterId, String name, SymptomPresence presence, OnsetPattern onset, String duration,
                         Integer severity, String character, String radiation, String notes) {
        symptomService.record(encounterId, name, new Symptom.Details(presence, onset, null, duration, severity, null,
                character, radiation, null, null, null, notes), DataProvenance.CLINICIAN, null, null);
    }

    private void vitals(UUID encounterId, Duration ago, Object... typeValuePairs) {
        List<Measurement> measurements = new ArrayList<>();
        for (int i = 0; i < typeValuePairs.length; i += 2) {
            VitalSignType type = (VitalSignType) typeValuePairs[i];
            Object value = typeValuePairs[i + 1];
            measurements.add(value instanceof ConsciousnessLevel level
                    ? new Measurement(type, null, level)
                    : new Measurement(type, new BigDecimal(value.toString()), null));
        }
        vitalSignService.record(encounterId, Instant.now(clock).minus(ago), measurements, DataProvenance.DEVICE,
                "Synthetic monitor");
    }

    private void lab(UUID encounterId, String test, String value, String unit, String low, String high,
                     AbnormalFlag reportedFlag, Duration ago) {
        labService.record(encounterId, test, null, null, new LabObservation.Result(new BigDecimal(value), null, unit,
                        low == null ? null : new BigDecimal(low), high == null ? null : new BigDecimal(high), reportedFlag,
                        Instant.now(clock).minus(ago)),
                DataProvenance.LAB_IMPORT, "Synthetic laboratory feed");
    }

    private void history(UUID encounterId, HistoryCategory category, String description, String relationship,
                         HistoryStatus status) {
        historyService.record(encounterId, new MedicalHistoryEntry.Details(category, description, relationship, status,
                null, null, null), DataProvenance.PATIENT_REPORTED, null, null);
    }

    private void seedChestPain() {
        UUID p = patient("SYN-0001", "Alex", "Example", 56, BiologicalSex.MALE);
        UUID e = encounter(p, EncounterType.EMERGENCY, "Central chest pain for 2 hours", Duration.ofMinutes(50));
        symptom(e, "Chest pain", SymptomPresence.PRESENT, OnsetPattern.SUDDEN, "2 hours", 8, "pressure-like",
                "left arm", "Synthetic case: began at rest");
        symptom(e, "Shortness of breath", SymptomPresence.PRESENT, OnsetPattern.SUDDEN, "2 hours", 5, null, null, null);
        symptom(e, "Sweating", SymptomPresence.PRESENT, null, null, null, null, null, null);
        symptom(e, "Fever", SymptomPresence.ABSENT, null, null, null, null, null, null);
        vitals(e, Duration.ofMinutes(45), VitalSignType.HEART_RATE, 112, VitalSignType.RESPIRATORY_RATE, 22,
                VitalSignType.OXYGEN_SATURATION, 94, VitalSignType.SYSTOLIC_BLOOD_PRESSURE, 148,
                VitalSignType.DIASTOLIC_BLOOD_PRESSURE, 92, VitalSignType.BODY_TEMPERATURE, "37.1",
                VitalSignType.CONSCIOUSNESS_LEVEL, ConsciousnessLevel.ALERT);
        history(e, HistoryCategory.SMOKING, "Former smoker (synthetic)", null, HistoryStatus.FORMER);
        history(e, HistoryCategory.FAMILY_HISTORY, "Myocardial infarction", "Father", HistoryStatus.UNKNOWN);
        history(e, HistoryCategory.PAST_DIAGNOSIS, "Hypertension", null, HistoryStatus.ACTIVE);
        medicationService.record(e, new Medication.Details("Amlodipine", null, null, "5 mg", "once daily", "oral",
                null, null, MedicationStatus.ACTIVE), DataProvenance.PATIENT_REPORTED, null);
    }

    private void seedFeverCough() {
        UUID p = patient("SYN-0002", "Jordan", "Sample", 34, BiologicalSex.FEMALE);
        UUID e = encounter(p, EncounterType.PRIMARY_CARE, "Fever and productive cough for 4 days", Duration.ofHours(2));
        symptom(e, "Cough", SymptomPresence.PRESENT, OnsetPattern.GRADUAL, "4 days", 6, "productive", null, null);
        symptom(e, "Fever", SymptomPresence.PRESENT, OnsetPattern.GRADUAL, "3 days", null, null, null, null);
        symptom(e, "Shortness of breath", SymptomPresence.PRESENT, OnsetPattern.GRADUAL, "2 days", 4, null, null,
                "On exertion");
        symptom(e, "Chest pain", SymptomPresence.PRESENT, OnsetPattern.GRADUAL, "2 days", 4, "pleuritic", null, null);
        vitals(e, Duration.ofMinutes(100), VitalSignType.BODY_TEMPERATURE, "38.9", VitalSignType.HEART_RATE, 104,
                VitalSignType.RESPIRATORY_RATE, 24, VitalSignType.OXYGEN_SATURATION, 92,
                VitalSignType.SYSTOLIC_BLOOD_PRESSURE, 118, VitalSignType.DIASTOLIC_BLOOD_PRESSURE, 74,
                VitalSignType.CONSCIOUSNESS_LEVEL, ConsciousnessLevel.ALERT);
        lab(e, "C-reactive protein", "86", "mg/L", null, "5", null, Duration.ofMinutes(60));
        lab(e, "White cell count", "14.2", "10*9/L", "4.0", "11.0", null, Duration.ofMinutes(60));
        allergyService.record(e, "Penicillin", AllergyCategory.MEDICATION, "Urticarial rash (synthetic)",
                AllergySeverity.MODERATE, AllergyStatus.ACTIVE, DataProvenance.PATIENT_REPORTED, null);
    }

    private void seedHeadache() {
        UUID p = patient("SYN-0003", "Sam", "Placeholder", 41, BiologicalSex.FEMALE);
        UUID e = encounter(p, EncounterType.EMERGENCY, "Sudden severe headache", Duration.ofMinutes(30));
        symptom(e, "Headache", SymptomPresence.PRESENT, OnsetPattern.SUDDEN, "1 hour", 10, "thunderclap",
                null, "Described as worst headache ever (synthetic)");
        symptom(e, "Neck stiffness", SymptomPresence.PRESENT, OnsetPattern.SUDDEN, "1 hour", null, null, null, null);
        symptom(e, "Photophobia", SymptomPresence.PRESENT, null, null, null, null, null, null);
        symptom(e, "Fever", SymptomPresence.ABSENT, null, null, null, null, null, null);
        vitals(e, Duration.ofMinutes(25), VitalSignType.BODY_TEMPERATURE, "37.4", VitalSignType.HEART_RATE, 88,
                VitalSignType.SYSTOLIC_BLOOD_PRESSURE, 182, VitalSignType.DIASTOLIC_BLOOD_PRESSURE, 104,
                VitalSignType.RESPIRATORY_RATE, 16, VitalSignType.OXYGEN_SATURATION, 98,
                VitalSignType.CONSCIOUSNESS_LEVEL, ConsciousnessLevel.ALERT);
    }

    private void seedAbdominalPain() {
        UUID p = patient("SYN-0004", "Riley", "Demo", 27, BiologicalSex.MALE);
        UUID e = encounter(p, EncounterType.EMERGENCY, "Abdominal pain, now right lower quadrant", Duration.ofHours(3));
        symptomService.record(e, "Abdominal pain", new Symptom.Details(SymptomPresence.PRESENT, OnsetPattern.GRADUAL,
                null, "18 hours", 7, "right lower quadrant", "constant", null, "movement", null,
                "nausea, reduced appetite", "Started periumbilical (synthetic)"), DataProvenance.CLINICIAN, null, null);
        symptom(e, "Nausea", SymptomPresence.PRESENT, null, null, null, null, null, null);
        symptom(e, "Loss of appetite", SymptomPresence.PRESENT, null, null, null, null, null, null);
        symptom(e, "Diarrhoea", SymptomPresence.ABSENT, null, null, null, null, null, null);
        vitals(e, Duration.ofHours(2), VitalSignType.BODY_TEMPERATURE, "38.0", VitalSignType.HEART_RATE, 98,
                VitalSignType.SYSTOLIC_BLOOD_PRESSURE, 126, VitalSignType.RESPIRATORY_RATE, 18,
                VitalSignType.OXYGEN_SATURATION, 99);
        lab(e, "White cell count", "13.1", "10*9/L", "4.0", "11.0", null, Duration.ofMinutes(90));
        lab(e, "C-reactive protein", "40", "mg/L", null, "5", null, Duration.ofMinutes(90));
    }

    private void seedConfusionHypotension() {
        UUID p = patient("SYN-0005", "Morgan", "Fictional", 78, BiologicalSex.FEMALE);
        UUID e = encounter(p, EncounterType.EMERGENCY, "New confusion and dizziness", Duration.ofMinutes(20));
        symptom(e, "Confusion", SymptomPresence.PRESENT, OnsetPattern.GRADUAL, "1 day", null, null, null,
                "Reported by family member (synthetic)");
        symptom(e, "Dizziness", SymptomPresence.PRESENT, null, "1 day", 5, null, null, null);
        symptom(e, "Dysuria", SymptomPresence.PRESENT, null, "3 days", null, null, null, null);
        vitals(e, Duration.ofMinutes(15), VitalSignType.SYSTOLIC_BLOOD_PRESSURE, 85,
                VitalSignType.DIASTOLIC_BLOOD_PRESSURE, 50, VitalSignType.HEART_RATE, 125,
                VitalSignType.RESPIRATORY_RATE, 26, VitalSignType.BODY_TEMPERATURE, "38.6",
                VitalSignType.OXYGEN_SATURATION, 93, VitalSignType.CONSCIOUSNESS_LEVEL, ConsciousnessLevel.NEW_CONFUSION);
        lab(e, "Lactate", "4.1", "mmol/L", "0.5", "2.0", AbnormalFlag.CRITICAL_HIGH, Duration.ofMinutes(10));
        history(e, HistoryCategory.PAST_DIAGNOSIS, "Type 2 diabetes", null, HistoryStatus.ACTIVE);
        medicationService.record(e, new Medication.Details("Metformin", null, null, "500 mg", "twice daily", "oral",
                null, null, MedicationStatus.ACTIVE), DataProvenance.PATIENT_REPORTED, null);
    }
}
