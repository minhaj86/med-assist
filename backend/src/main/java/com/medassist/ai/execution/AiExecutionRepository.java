package com.medassist.ai.execution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiExecutionRepository extends JpaRepository<AiExecution, UUID> {

    List<AiExecution> findAllByOrderByStartedAtDesc(Pageable pageable);

    Optional<AiExecution> findFirstByStatusOrderByCompletedAtDesc(AiExecutionStatus status);

    Optional<AiExecution> findFirstByStatusNotOrderByCompletedAtDesc(AiExecutionStatus status);

    @Modifying
    @Query("update AiExecution e set e.rawResponse = null, e.rawResponsePurged = true "
            + "where e.completedAt < :before and e.rawResponse is not null")
    int purgeRawResponsesBefore(@Param("before") Instant before);
}
