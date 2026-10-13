package com.medassist.assessment.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicianFeedbackRepository extends JpaRepository<ClinicianFeedback, UUID> {

    List<ClinicianFeedback> findByAssessmentVersionIdOrderByCreatedAtAsc(UUID assessmentVersionId);
}
