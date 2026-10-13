package com.medassist.ai.prompt;

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

/** A registered prompt version. Template text is immutable (database-enforced); only the status may change. */
@Entity
@Table(name = "prompt_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromptTemplate extends BaseEntity {

    @Column(name = "prompt_id", nullable = false, updatable = false, length = 100)
    private String promptId;

    @Column(name = "version", nullable = false, updatable = false, length = 30)
    private String version;

    @Column(name = "description", updatable = false, length = 1000)
    private String description;

    @Column(name = "system_template", nullable = false, updatable = false, columnDefinition = "text")
    private String systemTemplate;

    @Column(name = "user_template", nullable = false, updatable = false, columnDefinition = "text")
    private String userTemplate;

    /** Instruction appended when a response failed validation and the model is asked once more. */
    @Column(name = "repair_template", nullable = false, updatable = false, columnDefinition = "text")
    private String repairTemplate;

    @Column(name = "intended_model", updatable = false, length = 300)
    private String intendedModel;

    @Column(name = "output_schema_id", nullable = false, updatable = false, length = 100)
    private String outputSchemaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PromptStatus status;

    @Column(name = "checksum", nullable = false, updatable = false, length = 64)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    public PromptTemplate(String promptId, String version, String description, String systemTemplate,
                          String userTemplate, String repairTemplate, String intendedModel, String outputSchemaId,
                          PromptStatus status,
                          String checksum, Instant createdAt, String createdBy) {
        super(UUID.randomUUID());
        this.promptId = promptId;
        this.version = version;
        this.description = description;
        this.systemTemplate = systemTemplate;
        this.userTemplate = userTemplate;
        this.repairTemplate = repairTemplate;
        this.intendedModel = intendedModel;
        this.outputSchemaId = outputSchemaId;
        this.status = status;
        this.checksum = checksum;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void changeStatus(PromptStatus status) {
        this.status = status;
    }
}
