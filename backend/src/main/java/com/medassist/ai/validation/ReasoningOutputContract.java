package com.medassist.ai.validation;

import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.terminology.domain.CodingSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Java mirror of {@code clinical-reasoning-output.v1.schema.json} - the only shape accepted from the model. */
public record ReasoningOutputContract(@NotNull @Size(max = 10) List<@Valid RedFlag> redFlags,
                                      @NotNull @Size(max = 10) List<@Valid Differential> differentialDiagnoses,
                                      @NotNull @Size(max = 15) List<@NotBlank String> uncertainties,
                                      @NotNull @Size(max = 15) List<@NotBlank String> additionalQuestions) {

    public record RedFlag(@NotBlank @Size(max = 200) String title, @NotNull ClinicalSeverity severity,
                          @NotBlank @Size(max = 1000) String reason, @NotNull List<String> evidenceIds) {
    }

    public record Statement(@NotBlank @Size(max = 1000) String statement, @NotNull List<String> evidenceIds) {
    }

    public record Investigation(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 1000) String reason,
                                List<String> evidenceIds) {
    }

    public record Differential(@NotBlank @Size(max = 200) String condition, CodingSystem codingSystem,
                               @Size(max = 50) String code, @NotNull ConfidenceLevel confidence,
                               @NotBlank @Size(max = 1000) String whyConsidered,
                               @NotNull List<@Valid Statement> supportingEvidence,
                               @NotNull List<@Valid Statement> contradictingEvidence,
                               @NotNull List<@NotBlank String> missingInformation,
                               @NotNull List<@Valid Investigation> suggestedInvestigations,
                               @Size(max = 1000) String distinguishingFeatures,
                               @NotBlank @Size(max = 2000) String reasoningSummary,
                               @NotNull List<String> citations) {
    }
}
