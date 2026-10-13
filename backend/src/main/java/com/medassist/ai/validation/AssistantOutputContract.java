package com.medassist.ai.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Java mirror of {@code clinical-assistant-output.v1.schema.json}. */
public record AssistantOutputContract(@NotBlank @Size(max = 3000) String answer,
                                      @NotNull @Size(max = 15) List<@Valid Statement> statements,
                                      @NotNull Boolean insufficientInformation,
                                      @NotNull @Size(max = 5) List<@Valid ProposedUpdate> proposedUpdates,
                                      @NotNull @Size(max = 8) List<@NotBlank String> followUpQuestions) {

    public record Statement(@NotBlank @Size(max = 1000) String statement, @NotNull List<String> evidenceIds) {
    }

    public enum UpdateCategory {
        SYMPTOM, HISTORY
    }

    public record ProposedUpdate(@NotNull UpdateCategory category, @NotBlank @Size(max = 300) String description,
                                 @NotBlank @Size(max = 500) String rationale) {
    }
}
