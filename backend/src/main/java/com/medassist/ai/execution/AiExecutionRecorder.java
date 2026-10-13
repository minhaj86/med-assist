package com.medassist.ai.execution;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists AI execution records independently of the caller's transaction (failures are recorded too). */
@Component
public class AiExecutionRecorder {

    private final AiExecutionRepository repository;

    public AiExecutionRecorder(AiExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiExecution record(AiExecution execution) {
        return repository.save(execution);
    }
}
