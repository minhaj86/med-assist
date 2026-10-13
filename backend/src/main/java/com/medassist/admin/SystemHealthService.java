package com.medassist.admin;

import com.medassist.ai.provider.ClinicalAiProvider;
import com.medassist.ai.provider.ResilientClinicalAiProvider;
import com.medassist.knowledge.application.KnowledgeQueryService;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Operational health overview for administrators (no secrets, no patient data). */
@Service
public class SystemHealthService {

    public enum Status {
        UP, DEGRADED, DOWN, DISABLED
    }

    public record Check(String component, Status status, String detail, Map<String, Object> data) {
    }

    public record SystemHealth(Status overall, String applicationVersion, long uptimeSeconds, String javaVersion,
                               List<String> activeProfiles, List<Check> checks) {
    }

    private final JdbcClient jdbc;
    private final ClinicalAiProvider aiProvider;
    private final KnowledgeQueryService knowledgeQueryService;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Environment environment;
    private final String jwkSetUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public SystemHealthService(JdbcClient jdbc, ClinicalAiProvider aiProvider,
                               KnowledgeQueryService knowledgeQueryService,
                               ObjectProvider<BuildProperties> buildProperties, Environment environment,
                               @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
        this.jdbc = jdbc;
        this.aiProvider = aiProvider;
        this.knowledgeQueryService = knowledgeQueryService;
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.jwkSetUri = jwkSetUri;
    }

    public SystemHealth health() {
        List<Check> checks = new ArrayList<>();
        checks.add(database());
        checks.add(identityProvider());
        checks.add(ai());
        checks.add(knowledge());
        checks.add(assessments());
        Status overall = checks.stream().anyMatch(c -> c.status() == Status.DOWN && !c.component().equals("AI provider"))
                ? Status.DOWN
                : checks.stream().anyMatch(c -> c.status() == Status.DOWN || c.status() == Status.DEGRADED)
                ? Status.DEGRADED : Status.UP;
        BuildProperties build = buildProperties.getIfAvailable();
        return new SystemHealth(overall, build == null ? "development" : build.getVersion(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000, System.getProperty("java.version"),
                List.of(environment.getActiveProfiles()), checks);
    }

    private Check database() {
        long start = System.nanoTime();
        try {
            jdbc.sql("select 1").query(Integer.class).single();
            String migration = jdbc.sql("select version from flyway_schema_history where success and version is not null "
                    + "order by installed_rank desc limit 1").query(String.class).optional().orElse("none");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("latencyMs", Duration.ofNanos(System.nanoTime() - start).toMillis());
            data.put("schemaVersion", migration);
            data.put("pgvector", jdbc.sql("select extversion from pg_extension where extname = 'vector'")
                    .query(String.class).optional().orElse("missing"));
            return new Check("Database", Status.UP, "PostgreSQL reachable", data);
        } catch (RuntimeException e) {
            return new Check("Database", Status.DOWN, "Database not reachable", Map.of());
        }
    }

    private Check identityProvider() {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return new Check("Identity provider", Status.DISABLED, "No JWK set URI configured", Map.of());
        }
        try {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(jwkSetUri))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200
                    ? new Check("Identity provider", Status.UP, "JWK set reachable", Map.of("jwkSetUri", jwkSetUri))
                    : new Check("Identity provider", Status.DEGRADED, "JWK set returned HTTP " + response.statusCode(),
                    Map.of("jwkSetUri", jwkSetUri));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Check("Identity provider", Status.DOWN, "JWK set not reachable (new logins may fail)",
                    Map.of("jwkSetUri", jwkSetUri));
        }
    }

    private Check ai() {
        ClinicalAiProvider.ProviderDescriptor d = aiProvider.descriptor();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", d.provider());
        data.put("model", d.model());
        if (aiProvider instanceof ResilientClinicalAiProvider resilient) {
            data.put("circuitBreaker", resilient.circuitState().name());
            boolean open = resilient.circuitState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
            return new Check("AI provider", open ? Status.DEGRADED : Status.UP, d.statusMessage(), data);
        }
        return new Check("AI provider", d.enabled() ? Status.UP : Status.DISABLED, d.statusMessage(), data);
    }

    private Check knowledge() {
        try {
            KnowledgeQueryService.KnowledgeStatus s = knowledgeQueryService.status();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("embeddingModel", s.embeddingModel());
            data.put("documents", s.documents());
            data.put("retrievableDocuments", s.retrievableDocuments());
            data.put("failedDocuments", s.failedDocuments());
            return new Check("Knowledge base", s.failedDocuments() > 0 ? Status.DEGRADED : Status.UP,
                    s.retrievableDocuments() + " document(s) available for retrieval", data);
        } catch (RuntimeException e) {
            return new Check("Knowledge base", Status.DOWN, "Knowledge base status unavailable", Map.of());
        }
    }

    private Check assessments() {
        try {
            long running = jdbc.sql("select count(*) from assessment_runs where status in ('QUEUED','RUNNING')")
                    .query(Long.class).single();
            long failed = jdbc.sql("select count(*) from assessment_runs where status = 'FAILED' "
                    + "and requested_at > now() - interval '24 hours'").query(Long.class).single();
            return new Check("Assessment pipeline", failed > 0 ? Status.DEGRADED : Status.UP,
                    running + " running, " + failed + " failed in the last 24 hours",
                    Map.of("running", running, "failedLast24h", failed));
        } catch (RuntimeException e) {
            return new Check("Assessment pipeline", Status.DOWN, "Status unavailable", Map.of());
        }
    }
}
