package com.medassist.investigation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {

    List<Investigation> findByEncounterIdOrderByCreatedAtAsc(UUID encounterId);

    Optional<Investigation> findByEncounterIdAndSourceSuggestionId(UUID encounterId, UUID sourceSuggestionId);
}
