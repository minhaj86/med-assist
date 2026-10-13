package com.medassist.assessment.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentVersionRepository extends JpaRepository<AssessmentVersion, UUID> {

    List<AssessmentVersion> findByEncounterIdOrderByVersionNumberDesc(UUID encounterId);

    Optional<AssessmentVersion> findFirstByAssessmentIdOrderByVersionNumberDesc(UUID assessmentId);

    List<AssessmentVersion> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);

    List<AssessmentVersion> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AssessmentVersion> findByEncounterIdIn(Collection<UUID> encounterIds);
}
