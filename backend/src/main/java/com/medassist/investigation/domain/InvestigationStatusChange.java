package com.medassist.investigation.domain;

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

@Entity
@Immutable
@Table(name = "investigation_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestigationStatusChange extends BaseEntity {

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private InvestigationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private InvestigationStatus toStatus;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "note", length = 1000)
    private String note;

    public InvestigationStatusChange(UUID investigationId, InvestigationStatus fromStatus, InvestigationStatus toStatus,
                                     String changedBy, Instant changedAt, String note) {
        super(UUID.randomUUID());
        this.investigationId = investigationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.note = note;
    }
}
