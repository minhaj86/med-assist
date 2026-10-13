package com.medassist.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Objects;

/**
 * Provenance of a clinical record: where the value came from, who recorded it and (for AI-proposed
 * values) which clinician confirmed it.
 */
@Embeddable
public class RecordProvenance {

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 30)
    private DataProvenance source;

    @Column(name = "source_detail", length = 200)
    private String sourceDetail;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected RecordProvenance() {
        // for JPA
    }

    private RecordProvenance(DataProvenance source, String sourceDetail, String recordedBy, Instant recordedAt,
                             String confirmedBy, Instant confirmedAt) {
        this.source = Objects.requireNonNull(source, "source");
        this.sourceDetail = sourceDetail;
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
        if (source == DataProvenance.AI_INFERRED && (confirmedBy == null || confirmedAt == null)) {
            throw new IllegalArgumentException("AI-inferred data requires explicit clinician confirmation");
        }
    }

    public static RecordProvenance recorded(DataProvenance source, String sourceDetail, String recordedBy,
                                            Instant recordedAt) {
        if (source == DataProvenance.AI_INFERRED) {
            throw new IllegalArgumentException("AI-inferred data must be recorded via confirmedAiProposal");
        }
        return new RecordProvenance(source, sourceDetail, recordedBy, recordedAt, null, null);
    }

    /** A value proposed by the AI assistant and explicitly accepted by the given clinician. */
    public static RecordProvenance confirmedAiProposal(String sourceDetail, String clinicianId, Instant at) {
        return new RecordProvenance(DataProvenance.AI_INFERRED, sourceDetail, clinicianId, at, clinicianId, at);
    }

    public DataProvenance getSource() {
        return source;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
