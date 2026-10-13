package com.medassist.knowledge.retrieval;

import java.util.List;
import java.util.UUID;

/** Port for vector storage and similarity search. */
public interface VectorRepository {

    record VectorMatch(UUID chunkId, UUID documentId, double similarity) {
    }

    void store(UUID chunkId, String modelId, float[] vector);

    /**
     * Nearest chunks by cosine similarity for one embedding model.
     *
     * @param includeInactive include disabled / unapproved / not-indexed documents (admin debugging only)
     */
    List<VectorMatch> search(float[] query, String modelId, int limit, boolean includeInactive);

    long countEmbeddings(UUID documentId, String modelId);
}
