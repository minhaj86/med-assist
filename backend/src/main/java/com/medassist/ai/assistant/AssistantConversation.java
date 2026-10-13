package com.medassist.ai.assistant;

import com.medassist.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assistant_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantConversation extends BaseEntity {

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    public AssistantConversation(UUID encounterId, UUID patientId, String startedBy, Instant startedAt) {
        super(UUID.randomUUID());
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
    }
}
