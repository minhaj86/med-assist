package com.medassist.encounter.domain;

import com.medassist.common.domain.AuditableEntity;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A clinical encounter. References its patient by identifier (aggregate boundary). */
@Entity
@Table(name = "encounters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encounter extends AuditableEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_type", nullable = false, length = 30)
    private EncounterType encounterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EncounterStatus status;

    @Column(name = "chief_complaint", length = 500)
    private String chiefComplaint;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "clinician_id", nullable = false, length = 100)
    private String clinicianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 30)
    private DataProvenance provenance;

    private Encounter(UUID id, UUID patientId, EncounterType encounterType, String chiefComplaint,
                      Instant startedAt, String clinicianId, DataProvenance provenance) {
        super(id);
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.encounterType = Objects.requireNonNull(encounterType, "encounterType");
        this.status = EncounterStatus.IN_PROGRESS;
        this.chiefComplaint = chiefComplaint == null || chiefComplaint.isBlank() ? null : chiefComplaint.trim();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.clinicianId = Objects.requireNonNull(clinicianId, "clinicianId");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public static Encounter start(UUID patientId, EncounterType type, String chiefComplaint, Instant startedAt,
                                  String clinicianId, DataProvenance provenance) {
        return new Encounter(UUID.randomUUID(), patientId, type, chiefComplaint, startedAt, clinicianId, provenance);
    }

    public void updateChiefComplaint(String chiefComplaint) {
        ensureModifiable();
        this.chiefComplaint = chiefComplaint == null || chiefComplaint.isBlank() ? null : chiefComplaint.trim();
    }

    public void complete(Instant at) {
        ensureModifiable();
        if (at.isBefore(startedAt)) {
            throw new DomainRuleViolationException("Completion time cannot precede the encounter start");
        }
        this.status = EncounterStatus.COMPLETED;
        this.completedAt = at;
    }

    public boolean isModifiable() {
        return status == EncounterStatus.IN_PROGRESS;
    }

    public void ensureModifiable() {
        if (!isModifiable()) {
            throw new DomainRuleViolationException("Encounter is " + status + " and can no longer be modified");
        }
    }
}
