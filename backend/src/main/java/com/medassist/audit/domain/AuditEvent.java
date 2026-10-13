package com.medassist.audit.domain;

import com.medassist.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit record. The database rejects UPDATE and DELETE on this table. */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent extends BaseEntity {

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "user_id", length = 100, updatable = false)
    private String userId;

    @Column(name = "patient_id", updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private AuditEventType eventType;

    @Column(name = "entity_type", length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 100, updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private String metadata;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20, updatable = false)
    private AuditOutcome outcome;

    public AuditEvent(Instant occurredAt, String userId, UUID patientId, UUID encounterId,
                      AuditEventType eventType, String entityType, String entityId, String metadata,
                      String correlationId, AuditOutcome outcome) {
        super(UUID.randomUUID());
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata;
        this.correlationId = correlationId;
        this.outcome = outcome;
    }
}
