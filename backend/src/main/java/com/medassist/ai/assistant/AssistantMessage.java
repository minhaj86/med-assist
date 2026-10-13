package com.medassist.ai.assistant;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A message in an assistant conversation. Assistant messages store only validated output. */
@Entity
@Table(name = "assistant_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantMessage extends BaseEntity {

    public enum Role {
        CLINICIAN, ASSISTANT
    }

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured")
    private String structured;

    @Column(name = "ai_execution_id")
    private UUID aiExecutionId;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "content_purged", nullable = false)
    private boolean contentPurged;

    public AssistantMessage(UUID conversationId, Role role, String content, String structured, UUID aiExecutionId,
                            String createdBy, Instant createdAt) {
        super(UUID.randomUUID());
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.structured = structured;
        this.aiExecutionId = aiExecutionId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
