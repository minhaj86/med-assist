package com.medassist.fhir.mapping;

import com.medassist.allergy.domain.Allergy;

import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.assessment.domain.FinalDiagnosis;
import com.medassist.common.domain.DataProvenance;
import com.medassist.encounter.domain.Encounter;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.medication.domain.Medication;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.ClinicalObservation;
import com.medassist.observation.domain.LabObservation;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.domain.Patient;
import com.medassist.terminology.domain.CodingSystem;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

/** Maps MedAssist domain objects to HL7 FHIR R4 resources (export direction). */
@Component
public class FhirResourceMapper {

    public org.hl7.fhir.r4.model.Patient toFhir(Patient p) {
        org.hl7.fhir.r4.model.Patient fhir = new org.hl7.fhir.r4.model.Patient();
        fhir.setId(p.getId().toString());
        fhir.addIdentifier().setSystem(FhirCodes.PATIENT_IDENTIFIER_SYSTEM).setValue(p.getExternalPatientId());
        fhir.addName(new HumanName().setFamily(p.getLastName()).addGiven(p.getFirstName()));
        fhir.setBirthDate(java.sql.Date.valueOf(p.getDateOfBirth()));
        fhir.setGender(administrativeGender(p.getGender()));
        fhir.addExtension(FhirCodes.BIOLOGICAL_SEX_EXTENSION, new CodeType(p.getBiologicalSex().name()));
        if (p.getContactPhone() != null) {
            fhir.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(p.getContactPhone());
        }
        if (p.isSynthetic()) {
            fhir.getMeta().addTag(FhirCodes.MEDASSIST_TAG_SYSTEM, FhirCodes.SYNTHETIC_TAG, "SYNTHETIC TEST DATA");
        }
        return fhir;
    }

    public org.hl7.fhir.r4.model.Encounter toFhir(Encounter e) {
        org.hl7.fhir.r4.model.Encounter fhir = new org.hl7.fhir.r4.model.Encounter();
        fhir.setId(e.getId().toString());
        fhir.setStatus(switch (e.getStatus()) {
            case IN_PROGRESS -> org.hl7.fhir.r4.model.Encounter.EncounterStatus.INPROGRESS;
            case COMPLETED -> org.hl7.fhir.r4.model.Encounter.EncounterStatus.FINISHED;
            case CANCELLED -> org.hl7.fhir.r4.model.Encounter.EncounterStatus.CANCELLED;
        });
        fhir.setClass_(new Coding(FhirCodes.ACT_CODE_SYSTEM, actCode(e.getEncounterType()), null));
        fhir.addType(new CodeableConcept().setText(e.getEncounterType().name()));
        fhir.setSubject(patientRef(e.getPatientId()));
        fhir.setPeriod(new Period().setStart(Date.from(e.getStartedAt()))
                .setEnd(e.getCompletedAt() == null ? null : Date.from(e.getCompletedAt())));
        if (e.getChiefComplaint() != null) {
            fhir.addReasonCode(new CodeableConcept().setText(e.getChiefComplaint()));
        }
        return fhir;
    }

    public Observation toFhir(ClinicalObservation o) {
        Observation fhir = baseObservation(o.getId(), o.getPatientId(), o.getEncounterId(), "vital-signs", "Vital Signs");
        CodeableConcept code = new CodeableConcept().setText(o.getVitalSignType().displayName());
        if (o.getConcept().code() != null) {
            code.addCoding(new Coding(CodingSystem.LOINC.uri(), o.getConcept().code(), o.getVitalSignType().displayName()));
        }
        fhir.setCode(code);
        fhir.setEffective(new DateTimeType(Date.from(o.getMeasuredAt())));
        if (o.getVitalSignType() == VitalSignType.CONSCIOUSNESS_LEVEL) {
            fhir.setValue(new CodeableConcept().setText(o.displayValue()));
        } else {
            fhir.setValue(new Quantity().setValue(o.getNumericValue()).setUnit(o.getUnit())
                    .setSystem(FhirCodes.UCUM_SYSTEM).setCode(o.getUnit()));
        }
        return fhir;
    }

    public Observation toFhir(LabObservation l) {
        Observation fhir = baseObservation(l.getId(), l.getPatientId(), l.getEncounterId(), "laboratory", "Laboratory");
        CodeableConcept code = new CodeableConcept().setText(l.getTest().displayName());
        if (l.getTest().code() != null) {
            code.addCoding(new Coding(l.getTest().system().uri(), l.getTest().code(), l.getTest().displayName()));
        }
        fhir.setCode(code);
        fhir.setEffective(new DateTimeType(Date.from(l.getCollectedAt())));
        if (l.getNumericValue() != null) {
            fhir.setValue(new Quantity().setValue(l.getNumericValue()).setUnit(l.getUnit()));
        } else {
            fhir.setValue(new StringType(l.getTextValue()));
        }
        if (l.getReferenceRangeLow() != null || l.getReferenceRangeHigh() != null) {
            Observation.ObservationReferenceRangeComponent range = fhir.addReferenceRange();
            if (l.getReferenceRangeLow() != null) {
                range.setLow(new Quantity().setValue(l.getReferenceRangeLow()).setUnit(l.getUnit()));
            }
            if (l.getReferenceRangeHigh() != null) {
                range.setHigh(new Quantity().setValue(l.getReferenceRangeHigh()).setUnit(l.getUnit()));
            }
        }
        if (l.getAbnormalFlag() != null) {
            fhir.addInterpretation(new CodeableConcept().addCoding(new Coding(FhirCodes.INTERPRETATION_SYSTEM,
                    interpretationCode(l.getAbnormalFlag()), l.getAbnormalFlag().name())));
        }
        return fhir;
    }

    public Condition toFhir(FinalDiagnosis d, UUID patientId, UUID encounterId, String recorder, Date recordedDate) {
        Condition fhir = new Condition();
        fhir.setId(UUID.nameUUIDFromBytes((encounterId + d.conditionName()).getBytes()).toString());
        fhir.setClinicalStatus(new CodeableConcept().addCoding(new Coding(FhirCodes.CONDITION_CLINICAL_SYSTEM, "active", "Active")));
        String verification = switch (d.certainty()) {
            case PROVISIONAL -> "provisional";
            case DIFFERENTIAL -> "differential";
            case CONFIRMED -> "confirmed";
        };
        fhir.setVerificationStatus(new CodeableConcept().addCoding(new Coding(FhirCodes.CONDITION_VERIFICATION_SYSTEM,
                verification, null)));
        CodeableConcept code = new CodeableConcept().setText(d.conditionName());
        if (d.code() != null && d.codingSystem() != null) {
            code.addCoding(new Coding(d.codingSystem().uri(), d.code(), d.conditionName()));
        }
        fhir.setCode(code);
        fhir.setSubject(patientRef(patientId));
        fhir.setEncounter(new Reference("Encounter/" + encounterId));
        fhir.setRecordedDate(recordedDate);
        fhir.setRecorder(new Reference().setDisplay(recorder));
        return fhir;
    }

    public MedicationRequest toFhir(Medication m) {
        MedicationRequest fhir = new MedicationRequest();
        fhir.setId(m.getId().toString());
        fhir.setStatus(switch (m.getStatus()) {
            case ACTIVE -> MedicationRequest.MedicationRequestStatus.ACTIVE;
            case ON_HOLD -> MedicationRequest.MedicationRequestStatus.ONHOLD;
            case COMPLETED -> MedicationRequest.MedicationRequestStatus.COMPLETED;
            case STOPPED -> MedicationRequest.MedicationRequestStatus.STOPPED;
            case UNKNOWN -> MedicationRequest.MedicationRequestStatus.UNKNOWN;
        });
        // A medication-list entry, not an order placed by MedAssist.
        fhir.setIntent(MedicationRequest.MedicationRequestIntent.PLAN);
        fhir.setReported(new org.hl7.fhir.r4.model.BooleanType(
                m.getProvenance().getSource() == DataProvenance.PATIENT_REPORTED));
        CodeableConcept medication = new CodeableConcept().setText(m.getMedicationName());
        if (m.getCode() != null && m.getCodingSystem() != null) {
            medication.addCoding(new Coding(m.getCodingSystem().uri(), m.getCode(), m.getMedicationName()));
        }
        fhir.setMedication(medication);
        fhir.setSubject(patientRef(m.getPatientId()));
        String dosage = String.join(" ", java.util.stream.Stream.of(m.getDosage(), m.getFrequency(), m.getRoute())
                .filter(s -> s != null && !s.isBlank()).toList());
        if (!dosage.isBlank()) {
            fhir.addDosageInstruction().setText(dosage);
        }
        return fhir;
    }

    public AllergyIntolerance toFhir(Allergy a) {
        AllergyIntolerance fhir = new AllergyIntolerance();
        fhir.setId(a.getId().toString());
        fhir.setClinicalStatus(new CodeableConcept().addCoding(new Coding(FhirCodes.ALLERGY_CLINICAL_SYSTEM,
                a.getStatus().name().toLowerCase(Locale.ROOT), null)));
        fhir.setCode(new CodeableConcept().setText(a.getAllergen()));
        fhir.setPatient(patientRef(a.getPatientId()));
        if (a.getCategory() != null) {
            switch (a.getCategory()) {
                case MEDICATION -> fhir.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
                case FOOD -> fhir.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
                case ENVIRONMENT -> fhir.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT);
                case BIOLOGIC -> fhir.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.BIOLOGIC);
                case OTHER -> {
                    // no FHIR category
                }
            }
        }
        fhir.setCriticality(switch (a.getSeverity()) {
            case SEVERE -> AllergyIntolerance.AllergyIntoleranceCriticality.HIGH;
            case MILD, MODERATE -> AllergyIntolerance.AllergyIntoleranceCriticality.LOW;
            case UNKNOWN -> AllergyIntolerance.AllergyIntoleranceCriticality.UNABLETOASSESS;
        });
        if (a.getReaction() != null) {
            AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = fhir.addReaction();
            reaction.addManifestation(new CodeableConcept().setText(a.getReaction()));
            if (a.getSeverity() != AllergySeverity.UNKNOWN) {
                reaction.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.fromCode(
                        a.getSeverity().name().toLowerCase(Locale.ROOT)));
            }
        }
        return fhir;
    }

    static String actCode(EncounterType type) {
        return switch (type) {
            case EMERGENCY -> "EMER";
            case INPATIENT -> "IMP";
            case TELEMEDICINE -> "VR";
            case PRIMARY_CARE, OUTPATIENT -> "AMB";
        };
    }

    static String interpretationCode(AbnormalFlag flag) {
        return switch (flag) {
            case NORMAL -> "N";
            case LOW -> "L";
            case HIGH -> "H";
            case CRITICAL_LOW -> "LL";
            case CRITICAL_HIGH -> "HH";
            case ABNORMAL -> "A";
        };
    }

    static Enumerations.AdministrativeGender administrativeGender(String gender) {
        if (gender == null) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        return switch (gender.trim().toLowerCase(Locale.ROOT)) {
            case "male", "man" -> Enumerations.AdministrativeGender.MALE;
            case "female", "woman" -> Enumerations.AdministrativeGender.FEMALE;
            case "other", "non-binary", "nonbinary" -> Enumerations.AdministrativeGender.OTHER;
            default -> Enumerations.AdministrativeGender.UNKNOWN;
        };
    }

    private static Observation baseObservation(UUID id, UUID patientId, UUID encounterId, String category, String display) {
        Observation fhir = new Observation();
        fhir.setId(id.toString());
        fhir.setStatus(Observation.ObservationStatus.FINAL);
        fhir.addCategory(new CodeableConcept().addCoding(new Coding(FhirCodes.OBSERVATION_CATEGORY_SYSTEM, category, display)));
        fhir.setSubject(patientRef(patientId));
        fhir.setEncounter(new Reference("Encounter/" + encounterId));
        return fhir;
    }

    private static Reference patientRef(UUID patientId) {
        return new Reference("Patient/" + patientId);
    }

}
