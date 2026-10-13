package com.medassist.knowledge.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param minSimilarity      retrieval cut-off (vector similarity; not a clinical confidence)
 * @param maxChunksPerDocument diversity cap so one document cannot crowd out other sources
 */
@ConfigurationProperties(prefix = "medassist.knowledge.retrieval")
public record RetrievalProperties(@DefaultValue("6") int topK, @DefaultValue("0.15") double minSimilarity,
                                  @DefaultValue("3") int maxChunksPerDocument) {
}
