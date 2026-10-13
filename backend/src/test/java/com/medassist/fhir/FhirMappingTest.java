package com.medassist.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.allergy.domain.Allergy;
import com.medassist.allergy.domain.AllergyCategory;
import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.allergy.domain.AllergyStatus;
import com.medassist.assessment.domain.FinalDiagnosis;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.encounter.domain.Encounter;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.fhir.mapping.FhirCodes;
import com.medassist.fhir.mapping.FhirResourceMapper;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.ClinicalObservation;
import com.medassist.observation.domain.LabObservation;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.patient.domain.BiologicalSex;
import com.medassist.patient.domain.Patient;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Test;

class FhirMappingTest {

    private final FhirResourceMapper mapper = new FhirResourceMapper();
    private static final RecordProvenance PROV = RecordProvenance.recorded(DataProvenance.CLINICIAN, null, "u", Instant.EPOCH);

    @Test
    void mapsPatientWithIdentifierBiologicalSexExtensionAndSyntheticTag() {
        Patient patient = Patient.register("SYN-123", "Ada", "Example", LocalDate.of(1970, 1, 2), BiologicalSex.FEMALE,
                "Woman", null, true, DataProvenance.CLINICIAN, "u");
        org.hl7.fhir.r4.model.Patient fhir = mapper.toFhir(patient);
        assertThat(fhir.getIdentifierFirstRep().getSystem()).isEqualTo(FhirCodes.PATIENT_IDENTIFIER_SYSTEM);
        assertThat(fhir.getGender()).isEqualTo(Enumerations.AdministrativeGender.FEMALE);
        assertThat(fhir.getExtensionByUrl(FhirCodes.BIOLOGICAL_SEX_EXTENSION).getValue().primitiveValue()).isEqualTo("FEMALE");
        assertThat(fhir.getMeta().getTagFirstRep().getCode()).isEqualTo(FhirCodes.SYNTHETIC_TAG);
    }

    @Test
    void mapsEncounterClassVitalsWithLoincAndLabInterpretation() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), EncounterType.EMERGENCY, "Synthetic", Instant.EPOCH, "u",
                DataProvenance.CLINICIAN);
        assertThat(mapper.toFhir(encounter).getClass_().getCode()).isEqualTo("EMER");

        ClinicalObservation hr = ClinicalObservation.numericVitalSign(encounter.getId(), encounter.getPatientId(),
                VitalSignType.HEART_RATE, new BigDecimal("101"), Instant.EPOCH, PROV);
        Observation fhirHr = mapper.toFhir(hr);
        assertThat(fhirHr.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
        assertThat(fhirHr.getCode().getCodingFirstRep().getCode()).isEqualTo("8867-4");
        assertThat(fhirHr.getValueQuantity().getCode()).isEqualTo("/min");

        LabObservation lab = LabObservation.record(encounter.getId(), encounter.getPatientId(),
                ClinicalConcept.unverified("Potassium"), new LabObservation.Result(new BigDecimal("6.8"), null, "mmol/L",
                        new BigDecimal("3.5"), new BigDecimal("5.0"), AbnormalFlag.CRITICAL_HIGH, Instant.EPOCH), PROV);
        Observation fhirLab = mapper.toFhir(lab);
        assertThat(fhirLab.getCode().hasCoding()).isFalse();
        assertThat(fhirLab.getInterpretationFirstRep().getCodingFirstRep().getCode()).isEqualTo("HH");
        assertThat(fhirLab.getReferenceRangeFirstRep().getHigh().getValue()).isEqualByComparingTo("5.0");
    }

    @Test
    void mapsFinalDiagnosisVerificationAndAllergyCriticality() {
        Condition condition = mapper.toFhir(new FinalDiagnosis("Community-acquired pneumonia", null, null,
                        ConceptVerificationStatus.UNVERIFIED, FinalDiagnosis.DiagnosisCertainty.PROVISIONAL, null),
                UUID.randomUUID(), UUID.randomUUID(), "clinician-1", new Date());
        assertThat(condition.getVerificationStatus().getCodingFirstRep().getCode()).isEqualTo("provisional");
        assertThat(condition.getCode().hasCoding()).isFalse();

        Allergy allergy = Allergy.record(UUID.randomUUID(), null, "Peanut", AllergyCategory.FOOD, "Anaphylaxis",
                AllergySeverity.SEVERE, AllergyStatus.ACTIVE, PROV);
        AllergyIntolerance fhirAllergy = mapper.toFhir(allergy);
        assertThat(fhirAllergy.getCriticality()).isEqualTo(AllergyIntolerance.AllergyIntoleranceCriticality.HIGH);
        assertThat(fhirAllergy.getReactionFirstRep().getSeverity()).isEqualTo(AllergyIntolerance.AllergyIntoleranceSeverity.SEVERE);
        assertThat(fhirAllergy.getCategory().getFirst().getValue()).isEqualTo(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
    }
}
