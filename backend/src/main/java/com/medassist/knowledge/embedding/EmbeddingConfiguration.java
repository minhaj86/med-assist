package com.medassist.knowledge.embedding;

import com.medassist.ai.provider.AiProperties;
import com.openai.azure.AzureOpenAIServiceVersion;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the embedding adapter from configuration. No API key is ever logged or persisted. */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

    @Bean
    EmbeddingService embeddingService(EmbeddingProperties properties, AiProperties ai,
                                      ObservationRegistry observationRegistry) {
        return switch (properties.provider()) {
            case LOCAL_HASHING -> new LocalHashingEmbeddingService();
            case OPENAI -> remote(properties, "openai", "OpenAI", OpenAiEmbeddingOptions.builder()
                    .baseUrl(ai.openai().baseUrl()).apiKey(require(ai.openai().apiKey(), "OPENAI_API_KEY")),
                    observationRegistry);
            case AZURE_OPENAI -> {
                OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder();
                builder.azure(true);
                builder.baseUrl(require(ai.azureOpenai().endpoint(), "AZURE_OPENAI_ENDPOINT"));
                builder.apiKey(require(ai.azureOpenai().apiKey(), "AZURE_OPENAI_API_KEY"));
                builder.deploymentName(require(properties.model(), "EMBEDDING_MODEL (Azure deployment)"));
                if (ai.azureOpenai().apiVersion() != null && !ai.azureOpenai().apiVersion().isBlank()) {
                    builder.azureOpenAIServiceVersion(AzureOpenAIServiceVersion.Companion.fromString(ai.azureOpenai().apiVersion()));
                }
                yield remote(properties, "azure-openai", "Azure OpenAI", builder, observationRegistry);
            }
            case OPENAI_COMPATIBLE -> remote(properties, "openai-compatible", "OpenAI-compatible endpoint",
                    OpenAiEmbeddingOptions.builder().baseUrl(ai.openaiCompatible().baseUrl())
                            .apiKey(blankToDefault(ai.openaiCompatible().apiKey())), observationRegistry);
        };
    }

    private static EmbeddingService remote(EmbeddingProperties properties, String providerKey, String providerName,
                                           OpenAiEmbeddingOptions.Builder builder,
                                           ObservationRegistry observationRegistry) {
        String model = require(properties.model(), "EMBEDDING_MODEL");
        builder.model(model);
        builder.timeout(Duration.ofSeconds(60));
        builder.maxRetries(2);
        if (properties.dimensions() > 0) {
            builder.dimensions(properties.dimensions());
        }
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder().options(builder.build())
                .observationRegistry(observationRegistry).build();
        return new SpringAiEmbeddingService(embeddingModel, providerKey, model, properties.dimensions(),
                properties.batchSize(), providerName + " (" + model + ")");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Embedding provider configuration incomplete: " + name + " is not set");
        }
        return value;
    }

    /** Local OpenAI-compatible servers (e.g. Ollama) usually ignore the key but the client requires one. */
    private static String blankToDefault(String apiKey) {
        return apiKey == null || apiKey.isBlank() ? "not-required" : apiKey;
    }
}
