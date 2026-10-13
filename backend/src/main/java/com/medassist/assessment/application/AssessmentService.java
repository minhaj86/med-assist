package com.medassist.assessment.application;

import com.medassist.assessment.domain.Assessment;
import com.medassist.assessment.domain.AssessmentRepository;
import com.medassist.assessment.domain.AssessmentRun;
import com.medassist.assessment.domain.AssessmentRunRepository;
import com.medassist.assessment.domain.AssessmentRunStatus;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.ConflictException;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.web.CorrelationId;
import com.medassist.configuration.AsyncConfiguration;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Starts assessments. Execution is asynchronous; clients poll the run for stage progress. */
@Service
public class AssessmentService {

    /** Runs older than this are considered abandoned (e.g. interrupted by a restart) and do not block new runs. */
    static final Duration STALE_RUN = Duration.ofMinutes(10);

    private final AssessmentRepository assessmentRepository;
    private final AssessmentRunRepository runRepository;
    private final EncounterService encounterService;
    private final AssessmentRunTracker tracker;
    private final AssessmentPipeline pipeline;
    private final TaskExecutor executor;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public AssessmentService(AssessmentRepository assessmentRepository, AssessmentRunRepository runRepository,
                             EncounterService encounterService, AssessmentRunTracker tracker,
                             AssessmentPipeline pipeline,
                             @Qualifier(AsyncConfiguration.CLINICAL_TASK_EXECUTOR) TaskExecutor executor,
                             AuditService auditService, CurrentUserProvider currentUser, Clock clock) {
        this.assessmentRepository = assessmentRepository;
        this.runRepository = runRepository;
        this.encounterService = encounterService;
        this.tracker = tracker;
        this.pipeline = pipeline;
        this.executor = executor;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public AssessmentRun requestAssessment(UUID encounterId, boolean includeAi) {
        Encounter encounter = encounterService.require(encounterId);
        if (!encounter.isModifiable()) {
            throw new DomainRuleViolationException("Assessments can only be run while the encounter is in progress");
        }
        Assessment assessment = assessmentRepository.findByEncounterId(encounterId)
                .orElseGet(() -> assessmentRepository.save(Assessment.open(encounterId, encounter.getPatientId())));
        assessment.ensureNotFinalized();
        Instant now = Instant.now(clock);
        if (runRepository.existsByEncounterIdAndStatusInAndRequestedAtAfter(encounterId,
                EnumSet.of(AssessmentRunStatus.QUEUED, AssessmentRunStatus.RUNNING), now.minus(STALE_RUN))) {
            throw new ConflictException("An assessment is already running for this encounter");
        }
        AssessmentRun run = runRepository.save(AssessmentRun.queue(assessment.getId(), encounterId,
                encounter.getPatientId(), includeAi, currentUser.currentUserId(), now, CorrelationId.currentOrNull(),
                tracker.initialHistory()));
        auditService.record(AuditEntry.of(AuditEventType.ASSESSMENT_REQUESTED)
                .entity("AssessmentRun", run.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("includeAi", includeAi));
        UUID runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> pipeline.execute(runId));
            }
        });
        return run;
    }
}
