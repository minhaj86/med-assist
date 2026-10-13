package com.medassist.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Scripted AI outputs for structure tests (they assert structure and validation, not medical truth). */
public final class AiFixtures {

    private AiFixtures() {
    }

    public static String caseAResponse() {
        return read("ai/case-a-response.json");
    }

    public static String read(String path) {
        try (InputStream in = AiFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
