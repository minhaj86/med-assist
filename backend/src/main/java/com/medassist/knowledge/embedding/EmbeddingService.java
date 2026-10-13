package com.medassist.knowledge.embedding;

import java.util.List;

/** Port for text embeddings. The model id is stored with every vector so models are never mixed in search. */
public interface EmbeddingService {

    /** Stable identifier of provider + model (+ dimensions), e.g. "openai:text-embedding-3-small:1536". */
    String modelId();

    int dimensions();

    List<float[]> embed(List<String> texts);

    /** Human readable provider name for status pages. */
    String providerName();
}
