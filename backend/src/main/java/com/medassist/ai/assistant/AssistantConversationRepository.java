package com.medassist.ai.assistant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {

    Optional<AssistantConversation> findFirstByEncounterIdOrderByStartedAtDesc(UUID encounterId);
}
