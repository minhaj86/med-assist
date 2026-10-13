package com.medassist.audit.application;

import com.medassist.audit.domain.AuditEventType;
import com.medassist.audit.domain.AuditOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for audit events. Metadata must contain identifiers, versions, counts and technical facts only -
 * never names, contact details, free-text clinical notes, prompts or secrets.
 */
public final class AuditEntry {

    private final AuditEventType type;
    private String entityType;
    private String entityId;
    private UUID patientId;
    private UUID encounterId;
    private AuditOutcome outcome = AuditOutcome.SUCCESS;
    private String userIdOverride;
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    private AuditEntry(AuditEventType type) {
        this.type = type;
    }

    public static AuditEntry of(AuditEventType type) {
        return new AuditEntry(type);
    }

    public AuditEntry entity(String entityType, Object entityId) {
        this.entityType = entityType;
        this.entityId = entityId == null ? null : entityId.toString();
        return this;
    }

    public AuditEntry patient(UUID patientId) {
        this.patientId = patientId;
        return this;
    }

    public AuditEntry encounter(UUID encounterId) {
        this.encounterId = encounterId;
        return this;
    }

    public AuditEntry with(String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
        return this;
    }

    public AuditEntry failure() {
        this.outcome = AuditOutcome.FAILURE;
        return this;
    }

    /** Explicit actor for background work that has no security context. */
    public AuditEntry actor(String userId) {
        this.userIdOverride = userId;
        return this;
    }

    public AuditEventType type() {
        return type;
    }

    public String entityType() {
        return entityType;
    }

    public String entityId() {
        return entityId;
    }

    public UUID patientId() {
        return patientId;
    }

    public UUID encounterId() {
        return encounterId;
    }

    public AuditOutcome outcome() {
        return outcome;
    }

    public String userIdOverride() {
        return userIdOverride;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}
