package com.medassist.admin;

import com.medassist.ai.provider.AiProperties;
import com.medassist.configuration.RetentionProperties;
import com.medassist.knowledge.chunking.ChunkingProperties;
import com.medassist.knowledge.embedding.EmbeddingService;
import com.medassist.knowledge.retrieval.RetrievalProperties;
import com.medassist.security.SecurityProperties;
import com.medassist.security.annotation.RequireAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@Tag(name = "Admin - System")
@RequireAdmin
public class SystemAdminController {

    private final SystemHealthService healthService;
    private final Environment environment;
    private final AiProperties ai;
    private final EmbeddingService embeddingService;
    private final ChunkingProperties chunking;
    private final RetrievalProperties retrieval;
    private final RetentionProperties retention;
    private final SecurityProperties security;

    public SystemAdminController(SystemHealthService healthService, Environment environment, AiProperties ai,
                                 EmbeddingService embeddingService, ChunkingProperties chunking,
                                 RetrievalProperties retrieval, RetentionProperties retention,
                                 SecurityProperties security) {
        this.healthService = healthService;
        this.environment = environment;
        this.ai = ai;
        this.embeddingService = embeddingService;
        this.chunking = chunking;
        this.retrieval = retrieval;
        this.retention = retention;
        this.security = security;
    }

    @GetMapping("/health")
    @Operation(summary = "Component health overview")
    public SystemHealthService.SystemHealth health() {
        return healthService.health();
    }

    @GetMapping("/config")
    @Operation(summary = "Effective non-secret configuration (credentials are never returned)")
    public Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("activeProfiles", List.of(environment.getActiveProfiles()));
        config.put("ai", Map.of("provider", ai.provider().name(), "model", String.valueOf(ai.effectiveModel()),
                "endpoint", String.valueOf(ai.effectiveEndpoint()), "temperature", ai.temperature(),
                "responseFormat", ai.responseFormat().name(), "timeout", ai.timeout().toString(),
                "maxAttempts", ai.maxAttempts()));
        config.put("embedding", Map.of("provider", embeddingService.providerName(), "model", embeddingService.modelId()));
        config.put("chunking", chunking);
        config.put("retrieval", retrieval);
        config.put("retention", retention);
        config.put("security", Map.of("allowedOrigins", security.allowedOrigins(), "openApiPublic", security.openApiPublic(),
                "hstsEnabled", security.hstsEnabled(),
                "issuer", environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", ""),
                "audience", environment.getProperty("spring.security.oauth2.resourceserver.jwt.audiences", "")));
        config.put("tracingExportEnabled", environment.getProperty("management.tracing.export.enabled", "false"));
        config.put("demoSeedEnabled", environment.getProperty("medassist.demo.seed-enabled", "false"));
        return config;
    }
}
