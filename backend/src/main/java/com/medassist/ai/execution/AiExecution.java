package com.medassist.ai.execution;

import com.medassist.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Technical record of one AI call: provider, model, prompt version, temperature, evidence supplied and validation
 * outcome. The raw response (hidden reasoning removed) is retained only for the configured retention period and is
 * not exposed through the API.
 */
@Entity
@Table(name = "ai_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiExecution extends BaseEntity {

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "assessment_run_id")
    private UUID assessmentRunId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "requested_model", length = 200)
    private String requestedModel;

    @Column(name = "reported_model", length = 200)
    private String reportedModel;

    @Column(name = "temperature", nullable = false, precision = 4, scale = 2)
    private BigDecimal temperature;

    @Column(name = "prompt_id", nullable = false, length = 100)
    private String promptId;

    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;

    @Column(name = "prompt_checksum", nullable = false, length = 64)
    private String promptChecksum;

    @Column(name = "output_schema_id", nullable = false, length = 100)
    private String outputSchemaId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AiExecutionStatus status;

    @Column(name = "error_category", length = 40)
    private String errorCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieved_evidence", nullable = false)
    private String retrievedEvidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_issues", nullable = false)
    private String validationIssues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_summary", nullable = false)
    private String validationSummary;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "raw_response_purged", nullable = false)
    private boolean rawResponsePurged;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Builder
    private AiExecution(String purpose, UUID encounterId, UUID patientId, UUID assessmentRunId, String provider,
                        String requestedModel, String reportedModel, double temperature, String promptId,
                        String promptVersion, String promptChecksum, String outputSchemaId, Instant startedAt,
                        Instant completedAt, int attempts, AiExecutionStatus status, String errorCategory,
                        String retrievedEvidence, String validationIssues, String validationSummary,
                        Integer promptTokens, Integer completionTokens, String rawResponse, String requestedBy,
                        String correlationId) {
        super(UUID.randomUUID());
        this.purpose = purpose;
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.assessmentRunId = assessmentRunId;
        this.provider = provider;
        this.requestedModel = requestedModel;
        this.reportedModel = reportedModel;
        this.temperature = BigDecimal.valueOf(temperature);
        this.promptId = promptId;
        this.promptVersion = promptVersion;
        this.promptChecksum = promptChecksum;
        this.outputSchemaId = outputSchemaId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        this.attempts = attempts;
        this.status = status;
        this.errorCategory = errorCategory;
        this.retrievedEvidence = retrievedEvidence;
        this.validationIssues = validationIssues;
        this.validationSummary = validationSummary;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.rawResponse = rawResponse != null && rawResponse.length() > 65_536 ? rawResponse.substring(0, 65_536) : rawResponse;
        this.requestedBy = requestedBy;
        this.correlationId = correlationId;
    }
}
