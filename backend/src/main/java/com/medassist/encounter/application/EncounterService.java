package com.medassist.encounter.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.domain.Encounter;
import com.medassist.encounter.domain.EncounterRepository;
import com.medassist.encounter.domain.EncounterType;
import com.medassist.patient.application.PatientService;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncounterService {

    private final EncounterRepository repository;
    private final PatientService patientService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public EncounterService(EncounterRepository repository, PatientService patientService, AuditService auditService,
                            CurrentUserProvider currentUser, Clock clock) {
        this.repository = repository;
        this.patientService = patientService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public Encounter start(UUID patientId, EncounterType type, String chiefComplaint, Instant startedAt,
                           DataProvenance provenance) {
        patientService.require(patientId);
        Instant now = Instant.now(clock);
        Instant start = startedAt == null ? now : startedAt;
        if (start.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new DomainRuleViolationException("Encounter start time cannot be in the future");
        }
        Encounter encounter = Encounter.start(patientId, type, chiefComplaint, start, currentUser.currentUserId(),
                provenance);
        repository.save(encounter);
        auditService.record(AuditEntry.of(AuditEventType.ENCOUNTER_CREATED)
                .entity("Encounter", encounter.getId())
                .patient(patientId)
                .encounter(encounter.getId())
                .with("encounterType", type)
                .with("provenance", provenance));
        return encounter;
    }

    @Transactional
    public Encounter view(UUID encounterId) {
        Encounter encounter = require(encounterId);
        auditService.record(AuditEntry.of(AuditEventType.ENCOUNTER_VIEWED)
                .entity("Encounter", encounterId)
                .patient(encounter.getPatientId())
                .encounter(encounterId));
        return encounter;
    }

    @Transactional(readOnly = true)
    public Encounter require(UUID encounterId) {
        return repository.findById(encounterId).orElseThrow(() -> NotFoundException.of("Encounter", encounterId));
    }

    /** Loads an encounter that must still accept clinical data. */
    @Transactional(readOnly = true)
    public Encounter requireModifiable(UUID encounterId) {
        Encounter encounter = require(encounterId);
        encounter.ensureModifiable();
        return encounter;
    }

    @Transactional(readOnly = true)
    public List<Encounter> listForPatient(UUID patientId) {
        patientService.require(patientId);
        return repository.findByPatientIdOrderByStartedAtDesc(patientId);
    }

    @Transactional
    public Encounter updateChiefComplaint(UUID encounterId, String chiefComplaint) {
        Encounter encounter = require(encounterId);
        encounter.updateChiefComplaint(chiefComplaint);
        auditService.record(AuditEntry.of(AuditEventType.ENCOUNTER_UPDATED)
                .entity("Encounter", encounterId)
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("fields", List.of("chiefComplaint")));
        return encounter;
    }

    @Transactional
    public Encounter complete(UUID encounterId) {
        Encounter encounter = require(encounterId);
        encounter.complete(Instant.now(clock));
        auditService.record(AuditEntry.of(AuditEventType.ENCOUNTER_COMPLETED)
                .entity("Encounter", encounterId)
                .patient(encounter.getPatientId())
                .encounter(encounterId));
        return encounter;
    }
}
