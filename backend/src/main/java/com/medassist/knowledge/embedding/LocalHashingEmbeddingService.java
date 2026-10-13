package com.medassist.knowledge.embedding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic feature-hashing embedding (word unigrams, bigrams and character 4-grams, L2-normalized).
 *
 * <p>Lexical, not semantic: synonyms are only matched when they share character n-grams. It exists so the RAG
 * pipeline works offline, in CI and in demos without sending any text to an external service. Configure a real
 * embedding model for meaningful clinical retrieval.
 */
public class LocalHashingEmbeddingService implements EmbeddingService {

    public static final int DIMENSIONS = 384;
    private static final Set<String> STOPWORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "for",
            "from", "has", "have", "in", "is", "it", "its", "may", "of", "on", "or", "that", "the", "this", "to",
            "was", "were", "with", "which", "can", "should", "not", "no", "but", "if", "than", "then", "these", "those");

    @Override
    public String modelId() {
        return "local-hashing-v1:" + DIMENSIONS;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String providerName() {
        return "Local deterministic hashing (development/test only - lexical, not semantic)";
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(LocalHashingEmbeddingService::embedOne).toList();
    }

    static float[] embedOne(String text) {
        float[] vector = new float[DIMENSIONS];
        List<String> tokens = tokenize(text);
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            add(vector, "w:" + token, 1.0f);
            if (i + 1 < tokens.size()) {
                add(vector, "b:" + token + "_" + tokens.get(i + 1), 0.7f);
            }
            if (token.length() >= 6) {
                for (int j = 0; j + 4 <= token.length(); j++) {
                    add(vector, "c:" + token.substring(j, j + 4), 0.15f);
                }
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f; // valid unit vector for empty text; will not be similar to real content
            return vector;
        }
        float inv = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= inv;
        }
        return vector;
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }
            tokens.add(stem(raw));
        }
        return tokens;
    }

    private static String stem(String word) {
        if (word.length() > 5 && word.endsWith("ing")) {
            return word.substring(0, word.length() - 3);
        }
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 4 && word.endsWith("es") && !word.endsWith("ses")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static void add(float[] vector, String feature, float weight) {
        long hash = fnv1a64(feature);
        int index = (int) Math.floorMod(hash, (long) DIMENSIONS);
        float sign = ((hash >>> 40) & 1L) == 0 ? 1f : -1f;
        vector[index] += sign * weight;
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
