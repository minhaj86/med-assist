package com.medassist.ai.validation;

import com.medassist.ai.prompt.OutputSchemaRegistry;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns raw model output into a typed contract. Three gates, all mandatory: (1) a single JSON object can be
 * extracted, (2) it conforms to the versioned JSON Schema, (3) the Java contract passes Bean Validation. Hidden
 * reasoning blocks (e.g. {@code <think>}) are discarded and never stored or shown.
 */
@Component
public class StructuredOutputParser {

    private static final Pattern THINK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)\\s*```");
    static final int MAX_ERRORS = 8;

    public record ParseResult<T>(T value, List<String> errors, String sanitizedOutput) {

        public boolean isValid() {
            return value != null && errors.isEmpty();
        }
    }

    private final JsonMapper mapper;
    private final Validator validator;
    private final OutputSchemaRegistry schemas;
    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private final Map<String, Schema> compiled = new ConcurrentHashMap<>();

    public StructuredOutputParser(JsonMapper mapper, Validator validator, OutputSchemaRegistry schemas) {
        this.mapper = mapper;
        this.validator = validator;
        this.schemas = schemas;
    }

    public <T> ParseResult<T> parse(String raw, String schemaId, Class<T> type) {
        String sanitized = stripHiddenReasoning(raw);
        String json = extractJsonObject(sanitized);
        if (json == null) {
            return new ParseResult<>(null, List.of("Response does not contain a JSON object"), sanitized);
        }
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JacksonException e) {
            return new ParseResult<>(null, List.of("Response is not valid JSON"), sanitized);
        }
        List<Error> schemaErrors = compiled.computeIfAbsent(schemaId, id -> schemaRegistry.getSchema(schemas.schema(id)))
                .validate(node);
        if (!schemaErrors.isEmpty()) {
            return new ParseResult<>(null, schemaErrors.stream().limit(MAX_ERRORS)
                    .map(e -> "Schema: " + e.getInstanceLocation() + " " + e.getMessage()).toList(), json);
        }
        T value;
        try {
            value = mapper.treeToValue(node, type);
        } catch (JacksonException e) {
            return new ParseResult<>(null, List.of("Response does not match the output contract"), json);
        }
        List<String> violations = new ArrayList<>();
        for (ConstraintViolation<T> v : validator.validate(value)) {
            violations.add("Contract: " + v.getPropertyPath() + " " + v.getMessage());
            if (violations.size() >= MAX_ERRORS) {
                break;
            }
        }
        return violations.isEmpty() ? new ParseResult<>(value, List.of(), json)
                : new ParseResult<>(null, List.copyOf(violations), json);
    }

    static String stripHiddenReasoning(String raw) {
        if (raw == null) {
            return "";
        }
        String text = THINK.matcher(raw).replaceAll("");
        int unterminated = text.toLowerCase().indexOf("<think>");
        if (unterminated >= 0) {
            text = text.substring(0, unterminated);
        }
        return text.strip();
    }

    static String extractJsonObject(String text) {
        var fence = FENCE.matcher(text);
        String candidate = fence.find() ? fence.group(1) : text;
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        return start >= 0 && end > start ? candidate.substring(start, end + 1) : null;
    }
}
