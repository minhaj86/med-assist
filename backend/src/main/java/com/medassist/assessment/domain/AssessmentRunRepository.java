package com.medassist.assessment.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRunRepository extends JpaRepository<AssessmentRun, UUID> {

    boolean existsByEncounterIdAndStatusInAndRequestedAtAfter(UUID encounterId, Collection<AssessmentRunStatus> statuses,
                                                              Instant after);

    Optional<AssessmentRun> findFirstByEncounterIdOrderByRequestedAtDesc(UUID encounterId);

    List<AssessmentRun> findByStatusIn(Collection<AssessmentRunStatus> statuses);
}
