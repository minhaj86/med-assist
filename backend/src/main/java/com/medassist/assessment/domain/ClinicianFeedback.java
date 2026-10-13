package com.medassist.assessment.domain;

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

/**
 * Append-only clinician feedback on an AI suggestion. Stored for evaluation, product analytics and controlled
 * future research; it is never fed back into model training automatically.
 */
@Entity
@Immutable
@Table(name = "clinician_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicianFeedback extends BaseEntity {

    @Column(name = "assessment_version_id", nullable = false)
    private UUID assessmentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type", nullable = false, length = 30)
    private SuggestionType suggestionType;

    @Column(name = "suggestion_id", nullable = false)
    private UUID suggestionId;

    @Column(name = "clinician_id", nullable = false, length = 100)
    private String clinicianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private FeedbackAction action;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "modified_text", length = 2000)
    private String modifiedText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ClinicianFeedback(UUID assessmentVersionId, SuggestionType suggestionType, UUID suggestionId,
                             String clinicianId, FeedbackAction action, String reason, String modifiedText,
                             Instant createdAt) {
        super(UUID.randomUUID());
        this.assessmentVersionId = assessmentVersionId;
        this.suggestionType = suggestionType;
        this.suggestionId = suggestionId;
        this.clinicianId = clinicianId;
        this.action = action;
        this.reason = reason;
        this.modifiedText = modifiedText;
        this.createdAt = createdAt;
    }
}
