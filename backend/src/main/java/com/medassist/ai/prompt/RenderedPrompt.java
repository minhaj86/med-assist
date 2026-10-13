package com.medassist.ai.prompt;

/** A prompt version rendered with request variables; carries the identifiers recorded with every AI execution. */
public record RenderedPrompt(String promptId, String version, String checksum, String outputSchemaId,
                             String systemText, String userText, String repairTemplate) {

    /** User text for a retry after the previous response failed validation. */
    public String userTextWithRepair(String validationErrors) {
        return userText + "\n\n" + PromptRenderer.render(repairTemplate, java.util.Map.of("errors", validationErrors));
    }
}
