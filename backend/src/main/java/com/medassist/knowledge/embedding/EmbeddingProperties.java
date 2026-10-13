package com.medassist.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Embedding configuration. Credentials/endpoints are reused from {@code medassist.ai.*} for the chosen provider.
 *
 * @param provider   LOCAL_HASHING is a deterministic, offline lexical embedding for development and tests only
 * @param model      embedding model (or Azure deployment) name for remote providers
 * @param dimensions optional output dimensions (supported by some models); 0 = model default
 */
@ConfigurationProperties(prefix = "medassist.embedding")
public record EmbeddingProperties(@DefaultValue("LOCAL_HASHING") Provider provider, String model,
                                  @DefaultValue("0") int dimensions, @DefaultValue("32") int batchSize) {

    public enum Provider {
        LOCAL_HASHING, OPENAI, AZURE_OPENAI, OPENAI_COMPATIBLE
    }
}
