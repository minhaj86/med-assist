package com.medassist.clinicalrules.logic;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Converts JSON rule parameters into typed parameter records, rejecting invalid configurations. */
@Component
public class RuleParameters {

    private final JsonMapper mapper;

    public RuleParameters(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T read(JsonNode parameters, Class<T> type) {
        try {
            T value = mapper.treeToValue(parameters, type);
            if (value == null) {
                throw new IllegalArgumentException("Rule parameters are missing");
            }
            return value;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid rule parameters for " + type.getSimpleName() + ": "
                    + e.getOriginalMessage(), e);
        }
    }
}
