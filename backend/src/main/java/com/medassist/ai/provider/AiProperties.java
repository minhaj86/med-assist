package com.medassist.ai.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI provider configuration (externalized; credentials only from the environment, never persisted).
 *
 * @param provider       NONE disables AI reasoning entirely (deterministic features keep working)
 * @param temperature    low by design for clinical reasoning
 * @param responseFormat JSON_OBJECT works with OpenAI and most OpenAI-compatible servers; JSON_SCHEMA sends the
 *                       output schema to providers that support it
 */
@ConfigurationProperties(prefix = "medassist.ai")
public record AiProperties(@DefaultValue("NONE") ProviderKind provider,
                           @DefaultValue("0.1") double temperature,
                           @DefaultValue("4096") int maxOutputTokens,
                           @DefaultValue("90s") Duration timeout,
                           @DefaultValue("JSON_OBJECT") ResponseFormatMode responseFormat,
                           @DefaultValue("2") int maxAttempts,
                           @DefaultValue OpenAi openai,
                           @DefaultValue AzureOpenAi azureOpenai,
                           @DefaultValue OpenAiCompatible openaiCompatible,
                           @DefaultValue Resilience resilience) {

    public enum ProviderKind {
        NONE, OPENAI, AZURE_OPENAI, OPENAI_COMPATIBLE
    }

    public enum ResponseFormatMode {
        JSON_OBJECT, JSON_SCHEMA, TEXT
    }

    public record OpenAi(String apiKey, @DefaultValue("https://api.openai.com/v1") String baseUrl, String model) {
    }

    public record AzureOpenAi(String endpoint, String apiKey, String deployment, String apiVersion) {
    }

    public record OpenAiCompatible(@DefaultValue("http://localhost:11434/v1") String baseUrl, String apiKey,
                                   String model) {
    }

    public record Resilience(@DefaultValue("50") float failureRateThreshold,
                             @DefaultValue("10") int slidingWindowSize,
                             @DefaultValue("30s") Duration openStateWait,
                             @DefaultValue("4") int maxConcurrentCalls) {
    }

    /** Model name that will be requested from the configured provider (null when not configured). */
    public String effectiveModel() {
        return switch (provider) {
            case NONE -> null;
            case OPENAI -> openai.model();
            case AZURE_OPENAI -> azureOpenai.deployment();
            case OPENAI_COMPATIBLE -> openaiCompatible.model();
        };
    }

    public String effectiveEndpoint() {
        return switch (provider) {
            case NONE -> null;
            case OPENAI -> openai.baseUrl();
            case AZURE_OPENAI -> azureOpenai.endpoint();
            case OPENAI_COMPATIBLE -> openaiCompatible.baseUrl();
        };
    }
}
