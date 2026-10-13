package com.medassist.assessment.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByEncounterId(UUID encounterId);

    List<Assessment> findByEncounterIdIn(Collection<UUID> encounterIds);
}
