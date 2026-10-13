package com.medassist.audit.application;

import com.medassist.audit.domain.AuditEvent;
import com.medassist.audit.domain.AuditEventRepository;
import com.medassist.audit.domain.AuditEventType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public record AuditSearchCriteria(AuditEventType eventType, String userId, UUID patientId, UUID encounterId,
                                      String entityId, Instant from, Instant to) {
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditSearchCriteria criteria, int page, int size) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.eventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), criteria.eventType()));
            }
            if (criteria.userId() != null && !criteria.userId().isBlank()) {
                predicates.add(cb.equal(root.get("userId"), criteria.userId()));
            }
            if (criteria.patientId() != null) {
                predicates.add(cb.equal(root.get("patientId"), criteria.patientId()));
            }
            if (criteria.encounterId() != null) {
                predicates.add(cb.equal(root.get("encounterId"), criteria.encounterId()));
            }
            if (criteria.entityId() != null && !criteria.entityId().isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), criteria.entityId()));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), criteria.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "occurredAt")));
    }
}
