package com.medassist.knowledge.embedding;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

/** Adapter over a Spring AI {@link EmbeddingModel} (OpenAI, Azure OpenAI or OpenAI-compatible servers). */
public class SpringAiEmbeddingService implements EmbeddingService {

    private final EmbeddingModel model;
    private final String modelId;
    private final String providerName;
    private final int batchSize;
    private volatile int dimensions;

    public SpringAiEmbeddingService(EmbeddingModel model, String providerKey, String modelName, int configuredDimensions,
                                    int batchSize, String providerName) {
        this.model = model;
        this.dimensions = configuredDimensions;
        this.modelId = providerKey + ":" + modelName + (configuredDimensions > 0 ? ":" + configuredDimensions : "");
        this.providerName = providerName;
        this.batchSize = Math.max(1, batchSize);
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dimensions() {
        if (dimensions <= 0) {
            dimensions = model.dimensions();
        }
        return dimensions;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<float[]> batch = model.embed(texts.subList(i, Math.min(texts.size(), i + batchSize)));
            result.addAll(batch);
        }
        if (!result.isEmpty() && dimensions <= 0) {
            dimensions = result.getFirst().length;
        }
        return result;
    }
}
