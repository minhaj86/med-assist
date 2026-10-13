package com.medassist.knowledge.chunking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Chunk size and overlap in characters (roughly 4 characters per token for English clinical text). */
@ConfigurationProperties(prefix = "medassist.knowledge.chunking")
public record ChunkingProperties(@DefaultValue("1200") int maxChars, @DefaultValue("200") int overlapChars,
                                 @DefaultValue("80") int minChars) {

    public ChunkingProperties {
        if (maxChars < 200 || overlapChars < 0 || overlapChars >= maxChars / 2 || minChars < 0) {
            throw new IllegalArgumentException("Invalid chunking configuration");
        }
    }
}
