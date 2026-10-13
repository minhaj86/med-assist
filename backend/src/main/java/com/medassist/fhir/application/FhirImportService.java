package com.medassist.fhir.application;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import com.medassist.allergy.application.AllergyService;
import com.medassist.allergy.domain.AllergyCategory;
import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.allergy.domain.AllergyStatus;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.fhir.mapping.FhirCodes;
import com.medassist.history.application.MedicalHistoryService;
import com.medassist.history.domain.HistoryCategory;
import com.medassist.history.domain.HistoryStatus;
import com.medassist.history.domain.MedicalHistoryEntry;
import com.medassist.medication.application.MedicationService;
import com.medassist.medication.domain.Medication;
import com.medassist.medication.domain.MedicationStatus;
import com.medassist.observation.application.LabObservationService;
import com.medassist.observation.application.VitalSignService;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.LabObservation;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.application.PatientCommands;
import com.medassist.patient.application.PatientService;
import com.medassist.patient.domain.BiologicalSex;
import com.medassist.patient.domain.PatientRepository;
import com.medassist.terminology.domain.CodingSystem;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a FHIR R4 Bundle (or single Patient) for one patient. All-or-nothing: any invalid clinical value rolls back
 * the whole import. Values are recorded with provenance FHIR; units are never converted (non-canonical units are
 * skipped with a warning).
 */
@Service
public class FhirImportService {

    public record ImportResult(UUID patientId, boolean patientCreated, UUID encounterId, Map<String, Integer> imported,
                               List<String> warnings) {
    }

    static final int MAX_ENTRIES = 500;
    static final String SOURCE_DETAIL = "FHIR import";

    private final FhirContext fhirContext;
    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final EncounterService encounterService;
    private final VitalSignService vitalSignService;
    private final LabObservationService labService;
    private final MedicationService medicationService;
    private final AllergyService allergyService;
    private final MedicalHistoryService historyService;
    private final AuditService auditService;
    private final Clock clock;

    public FhirImportService(FhirContext fhirContext, PatientRepository patientRepository, PatientService patientService,
                             EncounterService encounterService, VitalSignService vitalSignService,
                             LabObservationService labService, MedicationService medicationService,
                             AllergyService allergyService, MedicalHistoryService historyService,
                             AuditService auditService, Clock clock) {
        this.fhirContext = fhirContext;
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.encounterService = encounterService;
        this.vitalSignService = vitalSignService;
        this.labService = labService;
        this.medicationService = medicationService;
        this.allergyService = allergyService;
        this.historyService = historyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importResource(String json) {
        IBaseResource parsed;
        try {
            parsed = fhirContext.newJsonParser().parseResource(json);
        } catch (DataFormatException e) {
            throw new DomainRuleViolationException("The request body is not a valid FHIR R4 JSON resource");
        }
        List<Resource> resources = new ArrayList<>();
        if (parsed instanceof Bundle bundle) {
            if (bundle.getEntry().size() > MAX_ENTRIES) {
                throw new DomainRuleViolationException("Bundle exceeds " + MAX_ENTRIES + " entries");
            }
            bundle.getEntry().forEach(e -> {
                if (e.getResource() != null) {
                    resources.add(e.getResource());
                }
            });
        } else if (parsed instanceof Resource resource) {
            resources.add(resource);
        }
        List<org.hl7.fhir.r4.model.Patient> patients = resources.stream()
                .filter(org.hl7.fhir.r4.model.Patient.class::isInstance).map(org.hl7.fhir.r4.model.Patient.class::cast).toList();
        if (patients.size() != 1) {
            throw new DomainRuleViolationException("The import must contain exactly one Patient resource");
        }
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        PatientRef patient = importPatient(patients.getFirst(), warnings);

        Optional<org.hl7.fhir.r4.model.Encounter> fhirEncounter = resources.stream()
                .filter(org.hl7.fhir.r4.model.Encounter.class::isInstance)
                .map(org.hl7.fhir.r4.model.Encounter.class::cast).findFirst();
        boolean hasClinicalData = resources.stream().anyMatch(r -> r instanceof Observation || r instanceof Condition
                || r instanceof MedicationRequest || r instanceof AllergyIntolerance);
        UUID encounterId = null;
        boolean completeAfterImport = false;
        if (fhirEncounter.isPresent() || hasClinicalData) {
            org.hl7.fhir.r4.model.Encounter e = fhirEncounter.orElse(null);
            Instant start = e != null && e.getPeriod().getStart() != null ? e.getPeriod().getStart().toInstant() : clock.instant();
            String reason = e != null && !e.getReasonCode().isEmpty() ? e.getReasonCodeFirstRep().getText() : "Imported from FHIR";
            Encounter encounter = encounterService.start(patient.id(), e == null ? EncounterType.OUTPATIENT
                    : encounterType(e.getClass_().getCode()), reason, start, DataProvenance.FHIR);
            encounterId = encounter.getId();
            counts.merge("Encounter", 1, Integer::sum);
            completeAfterImport = e != null && e.getStatus() == org.hl7.fhir.r4.model.Encounter.EncounterStatus.FINISHED;
        }
        for (Resource resource : resources) {
            switch (resource) {
                case Observation o -> importObservation(o, encounterId, warnings, counts);
                case Condition c -> {
                    importCondition(c, encounterId);
                    counts.merge("Condition", 1, Integer::sum);
                }
                case MedicationRequest m -> {
                    importMedication(m, encounterId);
                    counts.merge("MedicationRequest", 1, Integer::sum);
                }
                case AllergyIntolerance a -> {
                    importAllergy(a, encounterId);
                    counts.merge("AllergyIntolerance", 1, Integer::sum);
                }
                case org.hl7.fhir.r4.model.Patient p -> counts.merge("Patient", 1, Integer::sum);
                case org.hl7.fhir.r4.model.Encounter enc -> {
                    // already imported
                }
                default -> warnings.add("Unsupported resource type ignored: " + resource.fhirType());
            }
        }
        if (completeAfterImport) {
            encounterService.complete(encounterId);
        }
        auditService.record(AuditEntry.of(AuditEventType.FHIR_IMPORT)
                .entity("Patient", patient.id())
                .patient(patient.id())
                .encounter(encounterId)
                .with("patientCreated", patient.created())
                .with("counts", counts)
                .with("warnings", warnings.size()));
        return new ImportResult(patient.id(), patient.created(), encounterId, counts, List.copyOf(warnings));
    }

    private record PatientRef(UUID id, boolean created) {
    }

    private PatientRef importPatient(org.hl7.fhir.r4.model.Patient p, List<String> warnings) {
        String identifier = p.getIdentifier().stream()
                .filter(i -> FhirCodes.PATIENT_IDENTIFIER_SYSTEM.equals(i.getSystem())).findFirst()
                .or(() -> p.getIdentifier().stream().findFirst())
                .map(org.hl7.fhir.r4.model.Identifier::getValue)
                .orElseThrow(() -> new DomainRuleViolationException("Patient.identifier is required for import"));
        String externalId = identifier.replaceAll("[^A-Za-z0-9-]", "-");
        externalId = externalId.substring(0, Math.min(64, externalId.length()));
        if (externalId.length() < 3) {
            throw new DomainRuleViolationException("Patient identifier is too short");
        }
        var existing = patientRepository.findByExternalPatientId(externalId);
        if (existing.isPresent()) {
            warnings.add("Patient " + externalId + " already exists; demographics were not changed");
            return new PatientRef(existing.get().getId(), false);
        }
        if (p.getName().isEmpty() || !p.getBirthDateElement().hasValue()) {
            throw new DomainRuleViolationException("Patient.name and Patient.birthDate are required for import");
        }
        String family = p.getNameFirstRep().getFamily();
        String given = p.getNameFirstRep().getGivenAsSingleString();
        BiologicalSex sex = p.getExtension().stream()
                .filter(x -> FhirCodes.BIOLOGICAL_SEX_EXTENSION.equals(x.getUrl()))
                .map(x -> x.getValue().primitiveValue())
                .filter(v -> Arrays.stream(BiologicalSex.values()).anyMatch(b -> b.name().equals(v)))
                .map(BiologicalSex::valueOf).findFirst()
                .orElseGet(() -> {
                    warnings.add("Biological sex not provided (administrative gender is not used as a substitute)");
                    return BiologicalSex.UNKNOWN;
                });
        boolean synthetic = p.getMeta().getTag().stream()
                .anyMatch(t -> FhirCodes.SYNTHETIC_TAG.equals(t.getCode()));
        String gender = p.hasGender() ? p.getGender().getDisplay() : null;
        String phone = p.getTelecom().stream().filter(t -> t.getSystem() == org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.PHONE)
                .map(org.hl7.fhir.r4.model.ContactPoint::getValue).findFirst().orElse(null);
        UUID id = patientService.register(new PatientCommands.RegisterPatient(externalId,
                given == null || given.isBlank() ? "Unknown" : given, family == null || family.isBlank() ? "Unknown" : family,
                LocalDate.parse(p.getBirthDateElement().getValueAsString()), sex, gender, phone, synthetic,
                DataProvenance.FHIR)).getId();
        return new PatientRef(id, true);
    }

    private void importObservation(Observation o, UUID encounterId, List<String> warnings, Map<String, Integer> counts) {
        Instant effective = o.hasEffectiveDateTimeType() ? o.getEffectiveDateTimeType().getValue().toInstant() : clock.instant();
        Optional<VitalSignType> vital = o.getCode().getCoding().stream()
                .filter(c -> CodingSystem.LOINC.uri().equals(c.getSystem()))
                .map(Coding::getCode)
                .flatMap(code -> Arrays.stream(VitalSignType.values()).filter(v -> code.equals(v.loincCode())))
                .findFirst();
        if (vital.isPresent()) {
            if (!o.hasValueQuantity()) {
                warnings.add("Vital sign observation without a numeric value skipped");
                return;
            }
            Quantity q = o.getValueQuantity();
            String unit = q.hasCode() ? q.getCode() : q.getUnit();
            if (!vital.get().unit().equals(unit)) {
                warnings.add(vital.get().displayName() + " skipped: unit '" + unit + "' is not the canonical "
                        + vital.get().unit() + " (units are never converted automatically)");
                return;
            }
            vitalSignService.record(encounterId, effective, List.of(new VitalSignService.Measurement(vital.get(),
                    q.getValue(), null)), DataProvenance.FHIR, SOURCE_DETAIL);
            counts.merge("Observation (vital sign)", 1, Integer::sum);
            return;
        }
        Coding coding = o.getCode().getCodingFirstRep();
        String name = o.getCode().hasText() ? o.getCode().getText()
                : coding.hasDisplay() ? coding.getDisplay() : coding.getCode();
        if (name == null || name.isBlank()) {
            warnings.add("Observation without a name or code skipped");
            return;
        }
        BigDecimal value = o.hasValueQuantity() ? o.getValueQuantity().getValue() : null;
        String text = o.hasValueStringType() ? o.getValueStringType().getValue()
                : o.hasValueCodeableConcept() ? o.getValueCodeableConcept().getText() : null;
        String unit = o.hasValueQuantity() ? o.getValueQuantity().getUnit() : null;
        BigDecimal low = o.getReferenceRangeFirstRep().hasLow() ? o.getReferenceRangeFirstRep().getLow().getValue() : null;
        BigDecimal high = o.getReferenceRangeFirstRep().hasHigh() ? o.getReferenceRangeFirstRep().getHigh().getValue() : null;
        AbnormalFlag flag = o.getInterpretation().stream().flatMap(c -> c.getCoding().stream())
                .map(c -> interpretation(c.getCode())).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        CodingSystem system = coding.hasSystem() ? CodingSystem.fromUri(coding.getSystem()) : null;
        labService.record(encounterId, name, system, system == null ? null : coding.getCode(),
                new LabObservation.Result(value, text, unit, low, high, flag, effective), DataProvenance.FHIR, SOURCE_DETAIL);
        counts.merge("Observation (laboratory)", 1, Integer::sum);
    }

    private void importCondition(Condition c, UUID encounterId) {
        String name = text(c.getCode());
        String clinical = c.getClinicalStatus().getCodingFirstRep().getCode();
        HistoryStatus status = "resolved".equals(clinical) || "inactive".equals(clinical) || "remission".equals(clinical)
                ? HistoryStatus.RESOLVED : HistoryStatus.ACTIVE;
        LocalDate onset = c.hasOnsetDateTimeType() ? LocalDate.parse(c.getOnsetDateTimeType().getValueAsString().substring(0, 10)) : null;
        historyService.record(encounterId, new MedicalHistoryEntry.Details(HistoryCategory.PAST_DIAGNOSIS, name, null,
                status, onset, null, null), DataProvenance.FHIR, SOURCE_DETAIL, null);
    }

    private void importMedication(MedicationRequest m, UUID encounterId) {
        CodeableConcept med = m.hasMedicationCodeableConcept() ? m.getMedicationCodeableConcept() : new CodeableConcept();
        Coding coding = med.getCodingFirstRep();
        CodingSystem system = coding.hasSystem() ? CodingSystem.fromUri(coding.getSystem()) : null;
        MedicationStatus status = switch (m.getStatus() == null ? "unknown" : m.getStatus().toCode()) {
            case "active" -> MedicationStatus.ACTIVE;
            case "on-hold" -> MedicationStatus.ON_HOLD;
            case "completed" -> MedicationStatus.COMPLETED;
            case "stopped", "cancelled" -> MedicationStatus.STOPPED;
            default -> MedicationStatus.UNKNOWN;
        };
        String dosage = m.getDosageInstructionFirstRep().getText();
        medicationService.record(encounterId, new Medication.Details(text(med), system, system == null ? null : coding.getCode(),
                dosage, null, null, null, null, status), DataProvenance.FHIR, SOURCE_DETAIL);
    }

    private void importAllergy(AllergyIntolerance a, UUID encounterId) {
        String clinical = a.getClinicalStatus().getCodingFirstRep().getCode();
        AllergyStatus status = clinical == null ? AllergyStatus.ACTIVE : switch (clinical) {
            case "inactive" -> AllergyStatus.INACTIVE;
            case "resolved" -> AllergyStatus.RESOLVED;
            default -> AllergyStatus.ACTIVE;
        };
        AllergyCategory category = a.getCategory().isEmpty() ? null : switch (a.getCategory().getFirst().getValue()) {
            case MEDICATION -> AllergyCategory.MEDICATION;
            case FOOD -> AllergyCategory.FOOD;
            case ENVIRONMENT -> AllergyCategory.ENVIRONMENT;
            case BIOLOGIC -> AllergyCategory.BIOLOGIC;
            default -> AllergyCategory.OTHER;
        };
        AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = a.getReactionFirstRep();
        AllergySeverity severity = reaction.hasSeverity()
                ? AllergySeverity.valueOf(reaction.getSeverity().toCode().toUpperCase(Locale.ROOT)) : AllergySeverity.UNKNOWN;
        String manifestation = reaction.getManifestation().isEmpty() ? null : text(reaction.getManifestationFirstRep());
        allergyService.record(encounterId, text(a.getCode()), category, manifestation, severity, status,
                DataProvenance.FHIR, SOURCE_DETAIL);
    }

    private static String text(CodeableConcept concept) {
        if (concept.hasText()) {
            return concept.getText();
        }
        Coding coding = concept.getCodingFirstRep();
        if (coding.hasDisplay()) {
            return coding.getDisplay();
        }
        if (coding.hasCode()) {
            return coding.getCode();
        }
        throw new DomainRuleViolationException("A coded element has neither text nor a display/code");
    }

    static EncounterType encounterType(String actCode) {
        if (actCode == null) {
            return EncounterType.OUTPATIENT;
        }
        return switch (actCode) {
            case "EMER" -> EncounterType.EMERGENCY;
            case "IMP", "ACUTE", "NONAC" -> EncounterType.INPATIENT;
            case "VR" -> EncounterType.TELEMEDICINE;
            default -> EncounterType.OUTPATIENT;
        };
    }

    static AbnormalFlag interpretation(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "N" -> AbnormalFlag.NORMAL;
            case "L" -> AbnormalFlag.LOW;
            case "H" -> AbnormalFlag.HIGH;
            case "LL" -> AbnormalFlag.CRITICAL_LOW;
            case "HH" -> AbnormalFlag.CRITICAL_HIGH;
            case "A", "AA" -> AbnormalFlag.ABNORMAL;
            default -> null;
        };
    }
}
