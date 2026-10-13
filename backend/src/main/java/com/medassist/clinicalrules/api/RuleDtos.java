package com.medassist.clinicalrules.api;

import com.medassist.clinicalrules.domain.RuleCategory;
import com.medassist.common.domain.ClinicalSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class RuleDtos {

    private RuleDtos() {
    }

    public enum LifecycleStatus {
        ACTIVE, SCHEDULED, SUPERSEDED
    }

    public record RuleDefinitionResponse(UUID id, String ruleId, String version, String name, RuleCategory category,
                                         ClinicalSeverity severity, String logicKey, JsonNode parameters,
                                         String description, String recommendedAction, List<String> references,
                                         String source, String approvedBy, boolean demonstrationOnly,
                                         Instant effectiveFrom, Instant effectiveUntil, boolean enabled,
                                         LifecycleStatus lifecycleStatus, UUID supersedesId, String changeReason,
                                         Instant createdAt, String createdBy) {
    }

    public record SetEnabledRequest(@NotNull Boolean enabled, @Size(max = 500) String reason) {
    }

    public record NewVersionRequest(@NotBlank @Pattern(regexp = "^[0-9A-Za-z.+-]{1,30}$") String version,
                                    @Size(max = 200) String name,
                                    @NotNull ClinicalSeverity severity,
                                    @NotNull JsonNode parameters,
                                    @NotBlank @Size(max = 2000) String description,
                                    @NotBlank @Size(max = 1000) String recommendedAction,
                                    List<@NotBlank @Size(max = 500) String> references,
                                    @NotBlank @Size(max = 500) String source,
                                    @NotBlank @Size(max = 200) String approvedBy,
                                    @NotNull Boolean demonstrationOnly,
                                    Instant effectiveFrom,
                                    @NotBlank @Size(max = 1000) String changeReason,
                                    @NotNull Boolean enabled) {
    }
}
