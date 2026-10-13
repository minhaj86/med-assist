package com.medassist.ai.assistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, UUID> {

    List<AssistantMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @Modifying
    @Query("update AssistantMessage m set m.content = '[Removed under the data retention policy]', "
            + "m.structured = null, m.contentPurged = true where m.createdAt < :before and m.contentPurged = false")
    int purgeBefore(@Param("before") Instant before);
}
