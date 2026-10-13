package com.medassist.ai.provider;

import com.openai.azure.AzureOpenAIServiceVersion;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the configured provider. An incomplete configuration never prevents startup: the application runs with AI
 * disabled and reports the reason on the admin AI-configuration page.
 */
@Configuration(proxyBeanMethods = false)
public class AiProviderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfiguration.class);

    @Bean
    ClinicalAiProvider clinicalAiProvider(AiProperties properties, ObservationRegistry observationRegistry,
                                          MeterRegistry meterRegistry) {
        if (properties.provider() == AiProperties.ProviderKind.NONE) {
            return new DisabledClinicalAiProvider("none", "AI reasoning disabled (AI_PROVIDER=none)");
        }
        try {
            ClinicalAiProvider provider = create(properties, observationRegistry);
            log.info("AI provider configured: {} model={}", provider.descriptor().provider(), provider.descriptor().model());
            return new ResilientClinicalAiProvider(provider, properties.resilience(), meterRegistry);
        } catch (IllegalStateException e) {
            log.error("AI provider misconfigured - AI reasoning disabled: {}", e.getMessage());
            return new DisabledClinicalAiProvider(properties.provider().name().toLowerCase(),
                    "Misconfigured: " + e.getMessage());
        }
    }

    private static ClinicalAiProvider create(AiProperties p, ObservationRegistry observationRegistry) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        options.temperature(p.temperature());
        options.timeout(p.timeout());
        options.maxRetries(0);
        String providerName;
        String endpoint;
        boolean maxCompletionTokens;
        switch (p.provider()) {
            case OPENAI -> {
                providerName = "openai";
                endpoint = p.openai().baseUrl();
                options.baseUrl(endpoint);
                options.apiKey(require(p.openai().apiKey(), "OPENAI_API_KEY"));
                options.model(require(p.openai().model(), "OPENAI_MODEL"));
                maxCompletionTokens = true;
            }
            case AZURE_OPENAI -> {
                providerName = "azure-openai";
                endpoint = require(p.azureOpenai().endpoint(), "AZURE_OPENAI_ENDPOINT");
                options.azure(true);
                options.baseUrl(endpoint);
                options.apiKey(require(p.azureOpenai().apiKey(), "AZURE_OPENAI_API_KEY"));
                String deployment = require(p.azureOpenai().deployment(), "AZURE_OPENAI_DEPLOYMENT");
                options.deploymentName(deployment);
                options.model(deployment);
                if (p.azureOpenai().apiVersion() != null && !p.azureOpenai().apiVersion().isBlank()) {
                    options.azureOpenAIServiceVersion(AzureOpenAIServiceVersion.Companion.fromString(p.azureOpenai().apiVersion()));
                }
                maxCompletionTokens = true;
            }
            case OPENAI_COMPATIBLE -> {
                providerName = "openai-compatible";
                endpoint = require(p.openaiCompatible().baseUrl(), "LOCAL_LLM_BASE_URL");
                options.baseUrl(endpoint);
                String key = p.openaiCompatible().apiKey();
                options.apiKey(key == null || key.isBlank() ? "not-required" : key);
                options.model(require(p.openaiCompatible().model(), "LOCAL_LLM_MODEL"));
                maxCompletionTokens = false;
            }
            default -> throw new IllegalStateException("Unsupported provider " + p.provider());
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder().options(options.build())
                .observationRegistry(observationRegistry).build();
        return new SpringAiClinicalProvider(chatModel, new ClinicalAiProvider.ProviderDescriptor(providerName,
                p.effectiveModel(), endpoint, true, "Configured"), p.responseFormat(), maxCompletionTokens);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
