package com.medassist.audit.api;

import com.medassist.audit.application.AuditQueryService;
import com.medassist.audit.application.AuditQueryService.AuditSearchCriteria;
import com.medassist.audit.domain.AuditEvent;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.audit.domain.AuditOutcome;
import com.medassist.common.json.JsonCodec;
import com.medassist.common.web.PageResponse;
import com.medassist.security.annotation.RequireAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/admin/audit-events")
@Tag(name = "Admin - Audit")
@RequireAdmin
public class AuditController {

    private final AuditQueryService queryService;
    private final JsonCodec jsonCodec;

    public AuditController(AuditQueryService queryService, JsonCodec jsonCodec) {
        this.queryService = queryService;
        this.jsonCodec = jsonCodec;
    }

    public record AuditEventResponse(UUID id, Instant timestamp, String userId, UUID patientId, UUID encounterId,
                                     AuditEventType eventType, String entityType, String entityId,
                                     Map<String, Object> metadata, String correlationId, AuditOutcome outcome) {
    }

    @GetMapping
    @Operation(summary = "Search the audit trail")
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID encounterId,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var criteria = new AuditSearchCriteria(eventType, userId, patientId, encounterId, entityId, from, to);
        return PageResponse.from(queryService.search(criteria, page, size), this::toResponse);
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        Map<String, Object> metadata = jsonCodec.read(event.getMetadata(), new TypeReference<Map<String, Object>>() {
        });
        return new AuditEventResponse(event.getId(), event.getOccurredAt(), event.getUserId(), event.getPatientId(),
                event.getEncounterId(), event.getEventType(), event.getEntityType(), event.getEntityId(), metadata,
                event.getCorrelationId(), event.getOutcome());
    }
}
