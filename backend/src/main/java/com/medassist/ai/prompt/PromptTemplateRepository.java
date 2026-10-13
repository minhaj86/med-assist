package com.medassist.ai.prompt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, UUID> {

    Optional<PromptTemplate> findByPromptIdAndVersion(String promptId, String version);

    Optional<PromptTemplate> findByPromptIdAndStatus(String promptId, PromptStatus status);

    List<PromptTemplate> findAllByOrderByPromptIdAscCreatedAtDesc();
}
