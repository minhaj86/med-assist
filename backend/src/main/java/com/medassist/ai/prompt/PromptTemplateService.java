package com.medassist.ai.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Central access to prompts. Prompts are never assembled in controllers or scattered in Java code. */
@Service
public class PromptTemplateService {

    private final PromptTemplateRepository repository;
    private final OutputSchemaRegistry schemas;

    public PromptTemplateService(PromptTemplateRepository repository, OutputSchemaRegistry schemas) {
        this.repository = repository;
        this.schemas = schemas;
    }

    @Transactional(readOnly = true)
    public RenderedPrompt render(String promptId, Map<String, String> variables) {
        PromptTemplate template = repository.findByPromptIdAndStatus(promptId, PromptStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active prompt '" + promptId + "'"));
        Map<String, String> all = new HashMap<>(variables);
        all.put("outputSchema", schemas.schema(template.getOutputSchemaId()));
        return new RenderedPrompt(template.getPromptId(), template.getVersion(), template.getChecksum(),
                template.getOutputSchemaId(), PromptRenderer.render(template.getSystemTemplate(), all),
                PromptRenderer.render(template.getUserTemplate(), all), template.getRepairTemplate());
    }

    @Transactional(readOnly = true)
    public List<PromptTemplate> listAll() {
        return repository.findAllByOrderByPromptIdAscCreatedAtDesc();
    }

    public String outputSchema(String schemaId) {
        return schemas.schema(schemaId);
    }
}
