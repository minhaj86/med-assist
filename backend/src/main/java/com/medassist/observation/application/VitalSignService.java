package com.medassist.observation.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.ClinicalDataRecorder;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.observation.domain.ClinicalObservation;
import com.medassist.observation.domain.ClinicalObservationRepository;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VitalSignService {

    public record Measurement(VitalSignType type, BigDecimal value, ConsciousnessLevel consciousnessLevel) {
    }

    private final ClinicalObservationRepository repository;
    private final EncounterService encounterService;
    private final ClinicalDataRecorder recorder;
    private final AuditService auditService;

    public VitalSignService(ClinicalObservationRepository repository, EncounterService encounterService,
                            ClinicalDataRecorder recorder, AuditService auditService) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.recorder = recorder;
        this.auditService = auditService;
    }

    /** Records a set of vital signs measured at the same time. */
    @Transactional
    public List<ClinicalObservation> record(UUID encounterId, Instant measuredAt, List<Measurement> measurements,
                                            DataProvenance source, String sourceDetail) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        if (measurements == null || measurements.isEmpty()) {
            throw new DomainRuleViolationException("At least one measurement is required");
        }
        Instant at = measuredAt == null ? recorder.now() : measuredAt;
        if (at.isAfter(recorder.now().plus(Duration.ofMinutes(5)))) {
            throw new DomainRuleViolationException("Measurement time cannot be in the future");
        }
        Set<VitalSignType> seen = EnumSet.noneOf(VitalSignType.class);
        RecordProvenance provenance = recorder.provenance(source, sourceDetail, null);
        List<ClinicalObservation> created = new ArrayList<>();
        for (Measurement m : measurements) {
            if (!seen.add(m.type())) {
                throw new DomainRuleViolationException(m.type().displayName() + " appears more than once in the set");
            }
            ClinicalObservation observation = m.type() == VitalSignType.CONSCIOUSNESS_LEVEL
                    ? ClinicalObservation.consciousnessLevel(encounterId, encounter.getPatientId(),
                    requireLevel(m), at, provenance)
                    : ClinicalObservation.numericVitalSign(encounterId, encounter.getPatientId(), m.type(),
                    requireValue(m), at, provenance);
            created.add(observation);
        }
        repository.saveAll(created);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_CREATED)
                .entity("VitalSignSet", created.getFirst().getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("observationCount", created.size())
                .with("types", seen.stream().map(Enum::name).sorted().toList())
                .with("provenance", provenance.getSource()));
        return created;
    }

    @Transactional
    public ClinicalObservation markEnteredInError(UUID encounterId, UUID observationId, String reason) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        ClinicalObservation observation = repository.findById(observationId)
                .filter(o -> o.getEncounterId().equals(encounterId))
                .orElseThrow(() -> NotFoundException.of("Observation", observationId));
        observation.markEnteredInError(reason, recorder.currentUserId(), recorder.now());
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_ENTERED_IN_ERROR)
                .entity("Observation", observationId)
                .patient(encounter.getPatientId())
                .encounter(encounterId));
        return observation;
    }

    @Transactional(readOnly = true)
    public List<ClinicalObservation> list(UUID encounterId, boolean includeEnteredInError) {
        encounterService.require(encounterId);
        return repository.findByEncounterIdOrderByMeasuredAtDesc(encounterId).stream()
                .filter(o -> includeEnteredInError || !o.isEnteredInError())
                .toList();
    }

    private static BigDecimal requireValue(Measurement m) {
        if (m.value() == null) {
            throw new DomainRuleViolationException(m.type().displayName() + " requires a numeric value");
        }
        return m.value();
    }

    private static ConsciousnessLevel requireLevel(Measurement m) {
        if (m.consciousnessLevel() == null) {
            throw new DomainRuleViolationException("Level of consciousness requires an ACVPU value");
        }
        return m.consciousnessLevel();
    }
}
