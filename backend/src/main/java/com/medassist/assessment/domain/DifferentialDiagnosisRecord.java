package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
import com.medassist.diagnosis.differential.AssessmentMethod;
import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A possible differential diagnosis for clinician consideration - never a diagnosis. */
@Entity
@Immutable
@Table(name = "differential_diagnoses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DifferentialDiagnosisRecord extends BaseEntity {

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "condition_name", nullable = false, length = 300)
    private String conditionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "coding_system", length = 30)
    private CodingSystem codingSystem;

    @Column(name = "condition_code", length = 50)
    private String conditionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_verification", nullable = false, length = 40)
    private ConceptVerificationStatus codeVerification;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 20)
    private ConfidenceLevel confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_confidence", length = 20)
    private ConfidenceLevel originalConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_method", nullable = false, length = 30)
    private AssessmentMethod assessmentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "grounding_status", nullable = false, length = 30)
    private GroundingStatus groundingStatus;

    @Column(name = "why_considered", length = 2000)
    private String whyConsidered;

    @Column(name = "distinguishing_features", length = 2000)
    private String distinguishingFeatures;

    @Column(name = "reasoning_summary", length = 4000)
    private String reasoningSummary;

    @Column(name = "rank_basis", length = 300)
    private String rankBasis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", nullable = false)
    private String citations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_notes", nullable = false)
    private String validationNotes;

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "differential_id", nullable = false, updatable = false)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<DiagnosticEvidenceRecord> evidence = new ArrayList<>();

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "differential_id", nullable = false, updatable = false)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<SuggestedInvestigationRecord> investigations = new ArrayList<>();

    public record Values(int rank, String conditionName, CodingSystem codingSystem, String conditionCode,
                         ConceptVerificationStatus codeVerification, ConfidenceLevel confidenceLevel,
                         ConfidenceLevel originalConfidence, AssessmentMethod assessmentMethod,
                         GroundingStatus groundingStatus, String whyConsidered, String distinguishingFeatures,
                         String reasoningSummary, String rankBasis, String citationsJson,
                         String validationNotesJson) {
    }

    public DifferentialDiagnosisRecord(Values v, List<DiagnosticEvidenceRecord> evidence,
                                       List<SuggestedInvestigationRecord> investigations) {
        super(UUID.randomUUID());
        this.rank = v.rank();
        this.conditionName = v.conditionName();
        this.codingSystem = v.codingSystem();
        this.conditionCode = v.conditionCode();
        this.codeVerification = v.codeVerification();
        this.confidenceLevel = v.confidenceLevel();
        this.originalConfidence = v.originalConfidence();
        this.assessmentMethod = v.assessmentMethod();
        this.groundingStatus = v.groundingStatus();
        this.whyConsidered = v.whyConsidered();
        this.distinguishingFeatures = v.distinguishingFeatures();
        this.reasoningSummary = v.reasoningSummary();
        this.rankBasis = v.rankBasis();
        this.citations = v.citationsJson();
        this.validationNotes = v.validationNotesJson();
        this.evidence = new ArrayList<>(evidence);
        this.investigations = new ArrayList<>(investigations);
    }
}
