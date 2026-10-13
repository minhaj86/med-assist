package com.medassist.ai.provider;

/**
 * A fully rendered, versioned prompt plus generation settings.
 *
 * @param responseJsonSchema the output contract, sent to providers in JSON_SCHEMA mode
 */
public record ClinicalReasoningRequest(String purpose, String promptId, String promptVersion, String systemPrompt,
                                       String userPrompt, double temperature, int maxOutputTokens,
                                       String responseJsonSchema) {
}
