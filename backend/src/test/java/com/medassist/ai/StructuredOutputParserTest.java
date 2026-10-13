package com.medassist.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.ai.prompt.OutputSchemaRegistry;
import com.medassist.ai.validation.ReasoningOutputContract;
import com.medassist.ai.validation.StructuredOutputParser;
import com.medassist.support.AiFixtures;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StructuredOutputParserTest {

    private final StructuredOutputParser parser = new StructuredOutputParser(JsonMapper.builder().build(),
            Validation.buildDefaultValidatorFactory().getValidator(), new OutputSchemaRegistry());

    private static final String SCHEMA = "clinical-reasoning-output.v1";

    @Test
    void acceptsValidOutputAndDiscardsHiddenReasoningAndFences() {
        String raw = "<think>internal deliberation must never be stored</think>\n```json\n" + AiFixtures.caseAResponse() + "\n```";
        StructuredOutputParser.ParseResult<ReasoningOutputContract> result = parser.parse(raw, SCHEMA, ReasoningOutputContract.class);
        assertThat(result.errors()).isEmpty();
        assertThat(result.value().differentialDiagnoses()).hasSize(3);
        assertThat(result.sanitizedOutput()).doesNotContain("internal deliberation");
    }

    @Test
    void rejectsNonJsonAndMalformedJson() {
        assertThat(parser.parse("I think it is pneumonia.", SCHEMA, ReasoningOutputContract.class).errors())
                .containsExactly("Response does not contain a JSON object");
        assertThat(parser.parse("{\"redFlags\": [", SCHEMA, ReasoningOutputContract.class).isValid()).isFalse();
    }

    @Test
    void rejectsSchemaViolations() {
        String missingField = "{\"redFlags\":[],\"differentialDiagnoses\":[],\"uncertainties\":[]}";
        assertThat(parser.parse(missingField, SCHEMA, ReasoningOutputContract.class).errors())
                .anySatisfy(e -> assertThat(e).startsWith("Schema:"));
        String percentConfidence = AiFixtures.caseAResponse().replace("\"confidence\": \"HIGH\"", "\"confidence\": \"85%\"");
        assertThat(parser.parse(percentConfidence, SCHEMA, ReasoningOutputContract.class).isValid()).isFalse();
        String extraField = AiFixtures.caseAResponse().replaceFirst("\"redFlags\"", "\"diagnosis\": \"MI\", \"redFlags\"");
        assertThat(parser.parse(extraField, SCHEMA, ReasoningOutputContract.class).isValid()).isFalse();
        String badSeverity = AiFixtures.caseAResponse().replace("\"severity\": \"URGENT\"", "\"severity\": \"SEVERE\"");
        assertThat(parser.parse(badSeverity, SCHEMA, ReasoningOutputContract.class).isValid()).isFalse();
    }
}
