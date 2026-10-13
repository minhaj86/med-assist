package com.medassist.audit.application;

import com.medassist.audit.domain.AuditEvent;
import com.medassist.audit.domain.AuditEventRepository;
import com.medassist.common.json.JsonCodec;
import com.medassist.common.web.CorrelationId;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit events. Events recorded with {@link #record} commit or roll back with the business change. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Keys that must never appear in audit metadata (direct identifiers / free text). */
    private static final Set<String> FORBIDDEN_KEYS = Set.of("firstname", "lastname", "fullname", "patientname",
            "dateofbirth", "dob", "email", "phone", "address", "notes", "prompt", "apikey", "password", "secret",
            "token");

    private final AuditEventRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, CurrentUserProvider currentUserProvider,
                        JsonCodec jsonCodec, Clock clock) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEntry entry) {
        repository.save(toEvent(entry));
    }

    /** Records an event in its own transaction so it survives a rollback of the surrounding work. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEntry entry) {
        repository.save(toEvent(entry));
    }

    private AuditEvent toEvent(AuditEntry entry) {
        String userId = entry.userIdOverride() != null ? entry.userIdOverride() : currentUserProvider.currentUserId();
        return new AuditEvent(Instant.now(clock), userId, entry.patientId(), entry.encounterId(), entry.type(),
                entry.entityType(), entry.entityId(), jsonCodec.write(sanitize(entry.metadata())),
                CorrelationId.currentOrNull(), entry.outcome());
    }

    static Map<String, Object> sanitize(Map<String, Object> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (FORBIDDEN_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                log.warn("Dropped forbidden audit metadata key '{}'", key);
            } else {
                safe.put(key, value);
            }
        });
        return safe;
    }
}
