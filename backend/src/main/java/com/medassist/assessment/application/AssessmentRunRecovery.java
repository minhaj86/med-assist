package com.medassist.assessment.application;

import com.medassist.assessment.domain.AssessmentRun;
import com.medassist.assessment.domain.AssessmentRunRepository;
import com.medassist.assessment.domain.AssessmentRunStatus;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Marks runs interrupted by a restart as failed so they are visible and retryable. */
@Component
public class AssessmentRunRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AssessmentRunRecovery.class);

    private final AssessmentRunRepository repository;
    private final AssessmentRunTracker tracker;

    public AssessmentRunRecovery(AssessmentRunRepository repository, AssessmentRunTracker tracker) {
        this.repository = repository;
        this.tracker = tracker;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (AssessmentRun run : repository.findByStatusIn(EnumSet.of(AssessmentRunStatus.QUEUED, AssessmentRunStatus.RUNNING))) {
            log.warn("Marking interrupted assessment run {} as failed", run.getId());
            tracker.fail(run.getId(), "Interrupted by a service restart. Please run the assessment again.");
        }
    }
}
