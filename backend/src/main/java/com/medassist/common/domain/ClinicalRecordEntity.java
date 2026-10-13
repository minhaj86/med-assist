package com.medassist.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base for clinical records. Clinical records are never hard-deleted; incorrect entries are marked as
 * "entered in error" with a reason, which keeps the record auditable.
 */
@MappedSuperclass
public abstract class ClinicalRecordEntity extends AuditableEntity {

    @Embedded
    private RecordProvenance provenance;

    @Column(name = "entered_in_error", nullable = false)
    private boolean enteredInError;

    @Column(name = "entered_in_error_reason", length = 500)
    private String enteredInErrorReason;

    @Column(name = "entered_in_error_by", length = 100)
    private String enteredInErrorBy;

    @Column(name = "entered_in_error_at")
    private Instant enteredInErrorAt;

    protected ClinicalRecordEntity() {
    }

    protected ClinicalRecordEntity(UUID id, RecordProvenance provenance) {
        super(id);
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public void markEnteredInError(String reason, String userId, Instant at) {
        if (enteredInError) {
            throw new IllegalStateException("Record is already marked as entered in error");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to mark a record as entered in error");
        }
        this.enteredInError = true;
        this.enteredInErrorReason = reason;
        this.enteredInErrorBy = Objects.requireNonNull(userId);
        this.enteredInErrorAt = Objects.requireNonNull(at);
    }

    public RecordProvenance getProvenance() {
        return provenance;
    }

    public boolean isEnteredInError() {
        return enteredInError;
    }

    public String getEnteredInErrorReason() {
        return enteredInErrorReason;
    }

    public String getEnteredInErrorBy() {
        return enteredInErrorBy;
    }

    public Instant getEnteredInErrorAt() {
        return enteredInErrorAt;
    }
}
