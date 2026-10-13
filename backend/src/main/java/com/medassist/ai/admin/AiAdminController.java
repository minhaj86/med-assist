package com.medassist.ai.admin;

import com.medassist.ai.execution.AiExecution;
import com.medassist.ai.execution.AiExecutionRepository;
import com.medassist.ai.execution.AiExecutionStatus;
import com.medassist.ai.prompt.PromptStatus;
import com.medassist.ai.prompt.PromptTemplate;
import com.medassist.ai.prompt.PromptTemplateService;
import com.medassist.ai.provider.AiProperties;
import com.medassist.ai.provider.ClinicalAiProvider;
import com.medassist.common.json.JsonCodec;
import com.medassist.knowledge.embedding.EmbeddingService;
import com.medassist.security.annotation.RequireAdmin;
import com.medassist.terminology.domain.TerminologyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** AI configuration status for administrators. Secrets are never returned; configuration is environment-driven. */
@RestController
@RequestMapping("/api/admin/ai")
@Tag(name = "Admin - AI configuration")
@RequireAdmin
public class AiAdminController {

    public record PromptSummary(String promptId, String version, PromptStatus status, String checksum,
                                String outputSchemaId, String intendedModel, String description, Instant createdAt) {
    }

    public record AiStatusResponse(String provider, String model, String endpoint, boolean enabled, String status,
                                   double temperature, String responseFormat, int maxAttempts,
                                   Instant lastSuccessfulRequestAt, Instant lastFailedRequestAt,
                                   List<PromptSummary> activePrompts, String embeddingProvider, String embeddingModel,
                                   int embeddingDimensions, String terminologySources, boolean apiKeyConfigured) {
    }

    public record ExecutionSummary(UUID id, String purpose, String provider, String requestedModel,
                                   String reportedModel, String promptId, String promptVersion, double temperature,
                                   Instant startedAt, long durationMs, int attempts, AiExecutionStatus status,
                                   String errorCategory, JsonNode validationSummary, JsonNode validationIssues,
                                   Integer promptTokens, Integer completionTokens, UUID encounterId) {
    }

    private final ClinicalAiProvider provider;
    private final AiProperties properties;
    private final PromptTemplateService prompts;
    private final AiExecutionRepository executions;
    private final EmbeddingService embeddingService;
    private final TerminologyService terminologyService;
    private final JsonCodec json;

    public AiAdminController(ClinicalAiProvider provider, AiProperties properties, PromptTemplateService prompts,
                             AiExecutionRepository executions, EmbeddingService embeddingService,
                             TerminologyService terminologyService, JsonCodec json) {
        this.provider = provider;
        this.properties = properties;
        this.prompts = prompts;
        this.executions = executions;
        this.embeddingService = embeddingService;
        this.terminologyService = terminologyService;
        this.json = json;
    }

    @GetMapping("/status")
    @Operation(summary = "Provider, model, endpoint, status, prompt versions and embedding configuration (no secrets)")
    @Transactional(readOnly = true)
    public AiStatusResponse status() {
        ClinicalAiProvider.ProviderDescriptor d = provider.descriptor();
        return new AiStatusResponse(d.provider(), d.model(), d.endpoint(), d.enabled(), d.statusMessage(),
                properties.temperature(), properties.responseFormat().name(), properties.maxAttempts(),
                executions.findFirstByStatusOrderByCompletedAtDesc(AiExecutionStatus.SUCCEEDED)
                        .map(AiExecution::getCompletedAt).orElse(null),
                executions.findFirstByStatusNotOrderByCompletedAtDesc(AiExecutionStatus.SUCCEEDED)
                        .map(AiExecution::getCompletedAt).orElse(null),
                prompts.listAll().stream().filter(p -> p.getStatus() == PromptStatus.ACTIVE).map(this::toPrompt).toList(),
                embeddingService.providerName(), embeddingService.modelId(), embeddingService.dimensions(),
                terminologyService.describeSources(), apiKeyConfigured());
    }

    @GetMapping("/prompts")
    @Operation(summary = "Prompt registry (all versions)")
    public List<PromptSummary> prompts() {
        return prompts.listAll().stream().map(this::toPrompt).toList();
    }

    @GetMapping("/executions")
    @Operation(summary = "Recent AI executions (technical metadata only; responses are not exposed)")
    @Transactional(readOnly = true)
    public List<ExecutionSummary> executions(@RequestParam(defaultValue = "50") int limit) {
        return executions.findAllByOrderByStartedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200))).stream()
                .map(e -> new ExecutionSummary(e.getId(), e.getPurpose(), e.getProvider(), e.getRequestedModel(),
                        e.getReportedModel(), e.getPromptId(), e.getPromptVersion(), e.getTemperature().doubleValue(),
                        e.getStartedAt(), e.getDurationMs(), e.getAttempts(), e.getStatus(), e.getErrorCategory(),
                        json.readTree(e.getValidationSummary()), json.readTree(e.getValidationIssues()),
                        e.getPromptTokens(), e.getCompletionTokens(), e.getEncounterId()))
                .toList();
    }

    private boolean apiKeyConfigured() {
        return switch (properties.provider()) {
            case NONE -> false;
            case OPENAI -> notBlank(properties.openai().apiKey());
            case AZURE_OPENAI -> notBlank(properties.azureOpenai().apiKey());
            case OPENAI_COMPATIBLE -> notBlank(properties.openaiCompatible().apiKey());
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private PromptSummary toPrompt(PromptTemplate p) {
        return new PromptSummary(p.getPromptId(), p.getVersion(), p.getStatus(), p.getChecksum(), p.getOutputSchemaId(),
                p.getIntendedModel(), p.getDescription(), p.getCreatedAt());
    }
}
