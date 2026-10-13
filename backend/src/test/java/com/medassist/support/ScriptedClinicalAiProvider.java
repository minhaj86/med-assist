package com.medassist.support;

import com.medassist.ai.provider.AiProviderException;
import com.medassist.ai.provider.ClinicalAiProvider;
import com.medassist.ai.provider.ClinicalReasoningRequest;
import com.medassist.ai.provider.ClinicalReasoningResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic test double for the LLM provider: replays scripted outputs (or failures) in order and records the
 * requests it received. No external AI service is called in automated tests.
 */
public class ScriptedClinicalAiProvider implements ClinicalAiProvider {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<ClinicalReasoningRequest> requests = new ArrayList<>();

    public synchronized void enqueue(String content) {
        script.add(content);
    }

    public synchronized void enqueueFailure(AiProviderException.Category category) {
        script.add(new AiProviderException(category, "Scripted failure: " + category));
    }

    public synchronized void reset() {
        script.clear();
        requests.clear();
    }

    public synchronized List<ClinicalReasoningRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public synchronized ClinicalReasoningResponse reason(ClinicalReasoningRequest request) {
        requests.add(request);
        Object next = script.poll();
        if (next == null) {
            throw new AiProviderException(AiProviderException.Category.UNAVAILABLE, "No scripted response available");
        }
        if (next instanceof AiProviderException e) {
            throw e;
        }
        return new ClinicalReasoningResponse("scripted-test", "scripted-model-v1", "scripted-model-v1-2026",
                (String) next, 1000, 500, Duration.ofMillis(5));
    }

    @Override
    public ProviderDescriptor descriptor() {
        return new ProviderDescriptor("scripted-test", "scripted-model-v1", "in-memory", true, "Scripted test provider");
    }
}
