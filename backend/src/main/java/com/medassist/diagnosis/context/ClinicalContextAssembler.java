package com.medassist.diagnosis.context;

import com.medassist.allergy.application.AllergyService;
import com.medassist.allergy.domain.Allergy;
import com.medassist.diagnosis.context.ClinicalContext.AllergyFact;
import com.medassist.diagnosis.context.ClinicalContext.HistoryFact;
import com.medassist.diagnosis.context.ClinicalContext.LabFact;
import com.medassist.diagnosis.context.ClinicalContext.MedicationFact;
import com.medassist.diagnosis.context.ClinicalContext.SymptomFact;
import com.medassist.diagnosis.context.ClinicalContext.VitalSignFact;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.history.application.MedicalHistoryService;
import com.medassist.history.domain.HistoryCategory;
import com.medassist.history.domain.MedicalHistoryEntry;
import com.medassist.medication.application.MedicationService;
import com.medassist.medication.domain.Medication;
import com.medassist.observation.application.LabObservationService;
import com.medassist.observation.application.VitalSignService;
import com.medassist.observation.domain.ClinicalObservation;
import com.medassist.observation.domain.LabObservation;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.application.PatientService;
import com.medassist.patient.domain.Patient;
import com.medassist.symptom.application.SymptomService;
import com.medassist.symptom.domain.Symptom;
import com.medassist.symptom.domain.SymptomPresence;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Builds the {@link ClinicalContext} for an encounter from the clinical data modules. Only active, non-erroneous,
 * clinician-recorded or clinician-confirmed data is included. The most recent value of each vital sign and of each
 * laboratory test is used.
 */
@Service
public class ClinicalContextAssembler {

    private final EncounterService encounterService;
    private final PatientService patientService;
    private final SymptomService symptomService;
    private final VitalSignService vitalSignService;
    private final LabObservationService labService;
    private final MedicationService medicationService;
    private final AllergyService allergyService;
    private final MedicalHistoryService historyService;
    private final Clock clock;

    public ClinicalContextAssembler(EncounterService encounterService, PatientService patientService,
                                    SymptomService symptomService, VitalSignService vitalSignService,
                                    LabObservationService labService, MedicationService medicationService,
                                    AllergyService allergyService, MedicalHistoryService historyService, Clock clock) {
        this.encounterService = encounterService;
        this.patientService = patientService;
        this.symptomService = symptomService;
        this.vitalSignService = vitalSignService;
        this.labService = labService;
        this.medicationService = medicationService;
        this.allergyService = allergyService;
        this.historyService = historyService;
        this.clock = clock;
    }

    public ClinicalContext assemble(UUID encounterId) {
        Encounter encounter = encounterService.require(encounterId);
        Patient patient = patientService.require(encounter.getPatientId());
        Instant now = Instant.now(clock);
        FreeTextPseudonymizer scrubber = new FreeTextPseudonymizer(List.of(patient.getFirstName(),
                patient.getLastName(), patient.getExternalPatientId()));
        KeySequence keys = new KeySequence();
        List<ClinicalFact> facts = new ArrayList<>();

        int age = patient.ageOn(LocalDate.ofInstant(now, ZoneOffset.UTC));
        facts.add(new ClinicalFact(keys.next(), FactCategory.DEMOGRAPHICS,
                age + "-year-old, biological sex " + patient.getBiologicalSex().name().toLowerCase(Locale.ROOT),
                "Patient", patient.getId(), patient.getProvenance(), null, "DEMOGRAPHICS"));
        String chiefComplaint = scrubber.scrub(encounter.getChiefComplaint());
        if (chiefComplaint != null) {
            facts.add(new ClinicalFact(keys.next(), FactCategory.CHIEF_COMPLAINT, "Chief complaint: " + chiefComplaint,
                    "Encounter", encounter.getId(), encounter.getProvenance(), encounter.getStartedAt(),
                    "CHIEF_COMPLAINT"));
        }

        List<SymptomFact> symptoms = new ArrayList<>();
        List<Symptom> recorded = symptomService.list(encounterId, false).stream()
                .sorted(Comparator.comparing((Symptom s) -> s.getPresence() == SymptomPresence.ABSENT)
                        .thenComparing(Symptom::getCreatedAt))
                .toList();
        for (Symptom s : recorded) {
            String key = keys.next();
            boolean present = s.getPresence() == SymptomPresence.PRESENT;
            symptoms.add(new SymptomFact(key, s.getId(), s.getConcept().displayName(), present, s.getOnsetPattern(),
                    s.getSeverity(), scrubber.scrub(s.getCharacter()), scrubber.scrub(s.getLocation()),
                    scrubber.scrub(s.getRadiation()), scrubber.scrub(s.getDuration()), s.getProvenance().getSource(),
                    s.getConcept().verificationStatus()));
            facts.add(new ClinicalFact(key, present ? FactCategory.SYMPTOM : FactCategory.PERTINENT_NEGATIVE,
                    describeSymptom(s, scrubber), "Symptom", s.getId(), s.getProvenance().getSource(),
                    s.getOnsetAt(), "SYMPTOM:" + s.getId()));
        }

        Map<VitalSignType, ClinicalObservation> latestVitals = new EnumMap<>(VitalSignType.class);
        for (ClinicalObservation o : vitalSignService.list(encounterId, false)) {
            latestVitals.putIfAbsent(o.getVitalSignType(), o);
        }
        List<VitalSignFact> vitals = new ArrayList<>();
        for (ClinicalObservation o : latestVitals.values()) {
            String key = keys.next();
            vitals.add(new VitalSignFact(key, o.getId(), o.getVitalSignType(), o.getNumericValue(),
                    o.consciousnessLevel(), o.getUnit(), o.getMeasuredAt(), o.getProvenance().getSource()));
            facts.add(new ClinicalFact(key, FactCategory.VITAL_SIGN,
                    o.getVitalSignType().displayName() + ": " + o.displayValue() + " (measured " + o.getMeasuredAt() + ")",
                    "Observation", o.getId(), o.getProvenance().getSource(), o.getMeasuredAt(),
                    "VITAL_SIGN:" + o.getVitalSignType()));
        }

        Map<String, LabObservation> latestLabs = new LinkedHashMap<>();
        for (LabObservation l : labService.list(encounterId, false)) {
            latestLabs.putIfAbsent(l.getTest().displayName().toLowerCase(Locale.ROOT), l);
        }
        List<LabFact> labs = new ArrayList<>();
        for (LabObservation l : latestLabs.values()) {
            String key = keys.next();
            labs.add(new LabFact(key, l.getId(), l.getTest().displayName(), l.getTest().code(), l.getNumericValue(),
                    l.getTextValue(), l.getUnit(), l.getAbnormalFlag(), l.getAbnormalFlagSource(),
                    l.getReferenceRangeLow(), l.getReferenceRangeHigh(), l.getCollectedAt(),
                    l.getProvenance().getSource()));
            facts.add(new ClinicalFact(key, FactCategory.LAB_RESULT, describeLab(l), "LabObservation", l.getId(),
                    l.getProvenance().getSource(), l.getCollectedAt(),
                    "LAB:" + l.getTest().displayName().toLowerCase(Locale.ROOT)));
        }

        List<MedicationFact> medications = new ArrayList<>();
        for (Medication m : medicationService.listForPatient(patient.getId(), false)) {
            if (!m.isActive()) {
                continue;
            }
            String key = keys.next();
            medications.add(new MedicationFact(key, m.getId(), m.getMedicationName(), m.getDosage(), m.getFrequency(),
                    m.getRoute()));
            facts.add(new ClinicalFact(key, FactCategory.MEDICATION, "Active medication: " + join(m.getMedicationName(),
                    m.getDosage(), m.getFrequency(), m.getRoute()), "Medication", m.getId(),
                    m.getProvenance().getSource(), null, "MEDICATION:" + m.getId()));
        }

        List<AllergyFact> allergies = new ArrayList<>();
        for (Allergy a : allergyService.listForPatient(patient.getId(), false)) {
            if (!a.isActive()) {
                continue;
            }
            String key = keys.next();
            allergies.add(new AllergyFact(key, a.getId(), a.getAllergen(), a.getReaction(), a.getSeverity()));
            facts.add(new ClinicalFact(key, FactCategory.ALLERGY, "Allergy/intolerance: " + a.getAllergen()
                    + (a.getReaction() == null ? "" : " - reaction: " + scrubber.scrub(a.getReaction()))
                    + " (severity " + a.getSeverity().name().toLowerCase(Locale.ROOT) + ")", "Allergy", a.getId(),
                    a.getProvenance().getSource(), null, "ALLERGY:" + a.getId()));
        }

        List<HistoryFact> history = new ArrayList<>();
        for (MedicalHistoryEntry h : historyService.listForPatient(patient.getId(), false)) {
            String key = keys.next();
            String description = scrubber.scrub(h.getDescription());
            history.add(new HistoryFact(key, h.getId(), h.getCategory(), description, h.getRelationship(),
                    h.getStatus()));
            facts.add(new ClinicalFact(key, FactCategory.HISTORY, describeHistory(h, description), "MedicalHistoryEntry",
                    h.getId(), h.getProvenance().getSource(), null, "HISTORY:" + h.getId()));
        }

        return new ClinicalContext(encounterId, patient.getId(), now, age, patient.getBiologicalSex(),
                encounter.getEncounterType(), chiefComplaint, List.copyOf(symptoms), List.copyOf(vitals),
                List.copyOf(labs), List.copyOf(medications), List.copyOf(allergies), List.copyOf(history),
                List.copyOf(facts));
    }

    private static String describeSymptom(Symptom s, FreeTextPseudonymizer scrubber) {
        if (s.getPresence() == SymptomPresence.ABSENT) {
            return s.getConcept().displayName() + ": reported absent";
        }
        StringJoiner details = new StringJoiner("; ");
        if (s.getOnsetPattern() != null) {
            details.add("onset " + s.getOnsetPattern().name().toLowerCase(Locale.ROOT));
        }
        if (s.getDuration() != null) {
            details.add("duration " + scrubber.scrub(s.getDuration()));
        }
        if (s.getSeverity() != null) {
            details.add("severity " + s.getSeverity() + "/10");
        }
        addIfPresent(details, "location", scrubber.scrub(s.getLocation()));
        addIfPresent(details, "character", scrubber.scrub(s.getCharacter()));
        addIfPresent(details, "radiation", scrubber.scrub(s.getRadiation()));
        addIfPresent(details, "aggravated by", scrubber.scrub(s.getAggravatingFactors()));
        addIfPresent(details, "relieved by", scrubber.scrub(s.getRelievingFactors()));
        addIfPresent(details, "associated", scrubber.scrub(s.getAssociatedSymptoms()));
        addIfPresent(details, "notes", scrubber.scrub(s.getNotes()));
        String suffix = details.length() == 0 ? "" : " (" + details + ")";
        return s.getConcept().displayName() + ": present" + suffix;
    }

    private static String describeLab(LabObservation l) {
        StringBuilder sb = new StringBuilder(l.getTest().displayName()).append(": ").append(l.displayValue());
        if (l.getReferenceRangeLow() != null || l.getReferenceRangeHigh() != null) {
            sb.append(" (reference ")
                    .append(l.getReferenceRangeLow() == null ? "" : l.getReferenceRangeLow().stripTrailingZeros().toPlainString())
                    .append("-")
                    .append(l.getReferenceRangeHigh() == null ? "" : l.getReferenceRangeHigh().stripTrailingZeros().toPlainString())
                    .append(")");
        }
        if (l.getAbnormalFlag() != null) {
            sb.append("; flag ").append(l.getAbnormalFlag())
                    .append(" (").append(l.getAbnormalFlagSource().name().toLowerCase(Locale.ROOT).replace('_', ' ')).append(")");
        }
        return sb.append("; collected ").append(l.getCollectedAt()).toString();
    }

    private static String describeHistory(MedicalHistoryEntry h, String description) {
        String label = switch (h.getCategory()) {
            case PAST_DIAGNOSIS -> "Past diagnosis";
            case FAMILY_HISTORY -> "Family history" + (h.getRelationship() == null ? "" : " (" + h.getRelationship() + ")");
            case SURGICAL_HISTORY -> "Surgical history";
            case SMOKING -> "Smoking";
            case ALCOHOL -> "Alcohol";
            case SOCIAL_HISTORY -> "Social history";
            case HOSPITALIZATION -> "Previous hospitalization";
        };
        String status = h.getCategory() == HistoryCategory.FAMILY_HISTORY ? ""
                : " [" + h.getStatus().name().toLowerCase(Locale.ROOT) + "]";
        return label + ": " + description + status;
    }

    private static void addIfPresent(StringJoiner joiner, String label, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(label + " " + value);
        }
    }

    private static String join(String... parts) {
        StringJoiner joiner = new StringJoiner(" ");
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                joiner.add(p);
            }
        }
        return joiner.toString();
    }

    private static final class KeySequence {
        private int next = 1;

        String next() {
            return "F" + next++;
        }
    }
}
