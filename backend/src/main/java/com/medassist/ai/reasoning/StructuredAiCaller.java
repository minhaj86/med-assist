package com.medassist.ai.reasoning;

import com.medassist.ai.execution.AiExecution;
import com.medassist.ai.execution.AiExecutionRecorder;
import com.medassist.ai.execution.AiExecutionStatus;
import com.medassist.ai.prompt.PromptTemplateService;
import com.medassist.ai.prompt.RenderedPrompt;
import com.medassist.ai.provider.AiProperties;
import com.medassist.ai.provider.AiProviderException;
import com.medassist.ai.provider.ClinicalAiProvider;
import com.medassist.ai.provider.ClinicalReasoningRequest;
import com.medassist.ai.provider.ClinicalReasoningResponse;
import com.medassist.ai.validation.StructuredOutputParser;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.json.JsonCodec;
import com.medassist.common.web.CorrelationId;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Executes a catalog prompt against the configured provider and enforces the structured-output contract, with one
 * bounded "repair" retry when the output is malformed. Malformed output is never returned to callers.
 */
@Component
public class StructuredAiCaller {

    public enum Outcome {
        SUCCEEDED, VALIDATION_FAILED, PROVIDER_ERROR, NOT_CONFIGURED
    }

    public record CallContext(String purpose, UUID encounterId, UUID patientId, UUID assessmentRunId,
                              String requestedBy, List<RetrievedClinicalEvidence> evidence) {
    }

    public record CallResult<T>(Outcome outcome, T value, RenderedPrompt prompt, ClinicalReasoningResponse response,
                                String sanitizedOutput, int attempts, Instant startedAt, List<String> errors,
                                AiProviderException.Category errorCategory, UUID executionId,
                                ClinicalAiProvider.ProviderDescriptor provider) {
    }

    private final ClinicalAiProvider provider;
    private final PromptTemplateService prompts;
    private final StructuredOutputParser parser;
    private final AiExecutionRecorder recorder;
    private final AuditService auditService;
    private final AiProperties properties;
    private final JsonCodec json;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public StructuredAiCaller(ClinicalAiProvider provider, PromptTemplateService prompts, StructuredOutputParser parser,
                              AiExecutionRecorder recorder, AuditService auditService, AiProperties properties,
                              JsonCodec json, MeterRegistry meterRegistry, Clock clock) {
        this.provider = provider;
        this.prompts = prompts;
        this.parser = parser;
        this.recorder = recorder;
        this.auditService = auditService;
        this.properties = properties;
        this.json = json;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public ClinicalAiProvider.ProviderDescriptor providerDescriptor() {
        return provider.descriptor();
    }

    public <T> CallResult<T> call(String promptId, Map<String, String> variables, Class<T> contract, CallContext ctx) {
        ClinicalAiProvider.ProviderDescriptor descriptor = provider.descriptor();
        if (!descriptor.enabled()) {
            return new CallResult<>(Outcome.NOT_CONFIGURED, null, null, null, null, 0, Instant.now(clock),
                    List.of(descriptor.statusMessage()), AiProviderException.Category.NOT_CONFIGURED, null, descriptor);
        }
        RenderedPrompt prompt = prompts.render(promptId, variables);
        String schema = prompts.outputSchema(prompt.outputSchemaId());
        Instant started = Instant.now(clock);
        List<String> errors = List.of();
        ClinicalReasoningResponse response = null;
        String sanitized = null;
        int maxAttempts = Math.max(1, properties.maxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String userText = attempt == 1 ? prompt.userText()
                    : prompt.userTextWithRepair(String.join("; ", errors));
            try {
                response = provider.reason(new ClinicalReasoningRequest(ctx.purpose(), prompt.promptId(),
                        prompt.version(), prompt.systemText(), userText, properties.temperature(),
                        properties.maxOutputTokens(), schema));
            } catch (AiProviderException e) {
                UUID executionId = recordFailure(ctx, descriptor, prompt, started, attempt, AiExecutionStatus.PROVIDER_ERROR,
                        e.category().name(), List.of(e.getMessage()), null, null);
                auditService.recordIndependently(AuditEntry.of(AuditEventType.AI_PROVIDER_FAILURE)
                        .actor(ctx.requestedBy())
                        .entity("AiExecution", executionId)
                        .patient(ctx.patientId())
                        .encounter(ctx.encounterId())
                        .with("purpose", ctx.purpose())
                        .with("provider", descriptor.provider())
                        .with("model", descriptor.model())
                        .with("category", e.category())
                        .failure());
                return new CallResult<>(Outcome.PROVIDER_ERROR, null, prompt, null, null, attempt, started,
                        List.of(e.getMessage()), e.category(), executionId, descriptor);
            }
            StructuredOutputParser.ParseResult<T> parsed = parser.parse(response.content(), prompt.outputSchemaId(), contract);
            sanitized = parsed.sanitizedOutput();
            if (parsed.isValid()) {
                return new CallResult<>(Outcome.SUCCEEDED, parsed.value(), prompt, response, sanitized, attempt, started,
                        List.of(), null, null, descriptor);
            }
            errors = parsed.errors();
            Counter.builder("llm_validation_failures").description("AI outputs rejected by structured-output validation")
                    .tag("purpose", ctx.purpose()).tag("provider", descriptor.provider())
                    .register(meterRegistry).increment();
        }
        UUID executionId = recordFailure(ctx, descriptor, prompt, started, maxAttempts, AiExecutionStatus.VALIDATION_FAILED,
                "OUTPUT_VALIDATION", errors, response, sanitized);
        auditService.recordIndependently(AuditEntry.of(AuditEventType.AI_RESPONSE_REJECTED)
                .actor(ctx.requestedBy())
                .entity("AiExecution", executionId)
                .patient(ctx.patientId())
                .encounter(ctx.encounterId())
                .with("purpose", ctx.purpose())
                .with("provider", descriptor.provider())
                .with("model", descriptor.model())
                .with("promptId", prompt.promptId())
                .with("promptVersion", prompt.version())
                .with("attempts", maxAttempts)
                .with("errorCount", errors.size())
                .failure());
        return new CallResult<>(Outcome.VALIDATION_FAILED, null, prompt, response, sanitized, maxAttempts, started,
                errors, null, executionId, descriptor);
    }

    /** Records a successful, semantically validated execution and its audit events; returns the execution id. */
    public UUID recordSuccess(CallResult<?> result, CallContext ctx, Object validationSummary, List<String> issues) {
        ClinicalReasoningResponse r = result.response();
        AiExecution execution = recorder.record(base(ctx, result.provider(), result.prompt(), result.startedAt(),
                result.attempts(), AiExecutionStatus.SUCCEEDED, null, issues, validationSummary, r, result.sanitizedOutput()));
        auditService.record(AuditEntry.of(AuditEventType.AI_PROMPT_EXECUTED)
                .actor(ctx.requestedBy())
                .entity("AiExecution", execution.getId())
                .patient(ctx.patientId())
                .encounter(ctx.encounterId())
                .with("purpose", ctx.purpose())
                .with("provider", result.provider().provider())
                .with("requestedModel", result.provider().model())
                .with("reportedModel", r.reportedModel())
                .with("promptId", result.prompt().promptId())
                .with("promptVersion", result.prompt().version())
                .with("temperature", properties.temperature())
                .with("attempts", result.attempts())
                .with("evidence", ctx.evidence().stream().map(e -> e.evidenceKey() + "=" + e.documentId() + "@"
                        + e.documentVersion() + "#" + e.chunkId()).toList()));
        auditService.record(AuditEntry.of(AuditEventType.AI_RESPONSE_VALIDATED)
                .actor(ctx.requestedBy())
                .entity("AiExecution", execution.getId())
                .patient(ctx.patientId())
                .encounter(ctx.encounterId())
                .with("summary", validationSummary)
                .with("issueCount", issues.size()));
        return execution.getId();
    }

    private UUID recordFailure(CallContext ctx, ClinicalAiProvider.ProviderDescriptor descriptor, RenderedPrompt prompt,
                               Instant started, int attempts, AiExecutionStatus status, String category,
                               List<String> issues, ClinicalReasoningResponse response, String sanitized) {
        return recorder.record(base(ctx, descriptor, prompt, started, attempts, status, category, issues, Map.of(),
                response, sanitized)).getId();
    }

    private AiExecution base(CallContext ctx, ClinicalAiProvider.ProviderDescriptor descriptor, RenderedPrompt prompt,
                             Instant started, int attempts, AiExecutionStatus status, String category,
                             List<String> issues, Object summary, ClinicalReasoningResponse response,
                             String sanitizedOutput) {
        return AiExecution.builder()
                .purpose(ctx.purpose())
                .encounterId(ctx.encounterId())
                .patientId(ctx.patientId())
                .assessmentRunId(ctx.assessmentRunId())
                .provider(descriptor.provider())
                .requestedModel(descriptor.model())
                .reportedModel(response == null ? null : response.reportedModel())
                .temperature(properties.temperature())
                .promptId(prompt.promptId())
                .promptVersion(prompt.version())
                .promptChecksum(prompt.checksum())
                .outputSchemaId(prompt.outputSchemaId())
                .startedAt(started)
                .completedAt(Instant.now(clock))
                .attempts(attempts)
                .status(status)
                .errorCategory(category)
                .retrievedEvidence(json.write(ctx.evidence().stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("evidenceKey", e.evidenceKey());
                    m.put("chunkId", e.chunkId());
                    m.put("documentId", e.documentId());
                    m.put("documentVersion", e.documentVersion());
                    m.put("similarity", e.similarity());
                    return m;
                }).toList()))
                .validationIssues(json.write(issues))
                .validationSummary(json.write(summary))
                .promptTokens(response == null ? null : response.promptTokens())
                .completionTokens(response == null ? null : response.completionTokens())
                .rawResponse(sanitizedOutput)
                .requestedBy(ctx.requestedBy())
                .correlationId(CorrelationId.currentOrNull())
                .build();
    }
}
