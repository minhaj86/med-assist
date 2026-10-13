package com.medassist.assessment.application;

import com.medassist.assessment.domain.AssessmentRun;
import com.medassist.assessment.domain.AssessmentRunRepository;
import com.medassist.assessment.domain.AssessmentStage;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/** Persists pipeline progress in short, independent transactions so clients can poll real stage changes. */
@Component
public class AssessmentRunTracker {

    public record StageEntry(AssessmentStage stage, Instant at) {
    }

    private final AssessmentRunRepository repository;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    public AssessmentRunTracker(AssessmentRunRepository repository, JsonCodec jsonCodec, Clock clock) {
        this.repository = repository;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    public String initialHistory() {
        return jsonCodec.write(List.of(new StageEntry(AssessmentStage.QUEUED, Instant.now(clock))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssessmentRun advance(UUID runId, AssessmentStage stage) {
        AssessmentRun run = require(runId);
        run.advance(stage, append(run, stage));
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID runId, UUID versionId) {
        AssessmentRun run = require(runId);
        run.complete(versionId, Instant.now(clock), append(run, AssessmentStage.COMPLETED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, String reason) {
        AssessmentRun run = require(runId);
        run.fail(reason, Instant.now(clock), append(run, AssessmentStage.FAILED));
    }

    public List<StageEntry> history(AssessmentRun run) {
        return jsonCodec.read(run.getStageHistory(), new TypeReference<List<StageEntry>>() {
        });
    }

    private String append(AssessmentRun run, AssessmentStage stage) {
        List<StageEntry> entries = new ArrayList<>(history(run));
        entries.add(new StageEntry(stage, Instant.now(clock)));
        return jsonCodec.write(entries);
    }

    private AssessmentRun require(UUID runId) {
        return repository.findById(runId).orElseThrow(() -> NotFoundException.of("Assessment run", runId));
    }
}
