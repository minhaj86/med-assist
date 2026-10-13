package com.medassist.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Versioned JSON schemas for AI output contracts ({@code ai/schemas/<id>.schema.json}). */
@Component
public class OutputSchemaRegistry {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String schema(String schemaId) {
        return cache.computeIfAbsent(schemaId, id -> {
            if (!id.matches("^[a-z0-9.-]+$")) {
                throw new IllegalArgumentException("Invalid schema id");
            }
            try (InputStream in = new ClassPathResource("ai/schemas/" + id + ".schema.json").getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Output schema " + id + " not found", e);
            }
        });
    }
}
