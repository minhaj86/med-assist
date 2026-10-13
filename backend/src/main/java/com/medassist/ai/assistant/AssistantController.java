package com.medassist.ai.assistant;

import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Tag(name = "AI clinical assistant")
@RequireClinician
public class AssistantController {

    public record QuestionRequest(@NotBlank @Size(max = 2000) String question, UUID conversationId) {
    }

    public record MessageResponse(UUID id, UUID conversationId, String role, String content, JsonNode structured,
                                  UUID aiExecutionId, Instant createdAt) {
    }

    private final AssistantService service;
    private final com.medassist.common.json.JsonCodec json;

    public AssistantController(AssistantService service, com.medassist.common.json.JsonCodec json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/api/encounters/{encounterId}/assistant/questions")
    @Operation(summary = "Ask the AI assistant about this encounter (answers are validated; data is never modified)")
    public AssistantAnswer ask(@PathVariable UUID encounterId, @Valid @RequestBody QuestionRequest request) {
        return service.ask(encounterId, request.question(), request.conversationId());
    }

    @GetMapping("/api/encounters/{encounterId}/assistant/conversation")
    @Operation(summary = "Messages of the most recent assistant conversation for the encounter")
    public List<MessageResponse> conversation(@PathVariable UUID encounterId) {
        return service.latestConversation(encounterId).stream().map(m -> new MessageResponse(m.getId(), m.getConversationId(), m.getRole().name(),
                m.getContent(), m.getStructured() == null ? null : json.readTree(m.getStructured()), m.getAiExecutionId(),
                m.getCreatedAt())).toList();
    }
}
