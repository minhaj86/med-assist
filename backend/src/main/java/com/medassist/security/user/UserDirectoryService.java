package com.medassist.security.user;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.NotFoundException;
import com.medassist.security.KeycloakRoleConverter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the local user directory. Activity writes are throttled to avoid a database write per request.
 * The enabled flag is cached per instance for {@link #ACTIVITY_WRITE_INTERVAL}.
 */
@Service
public class UserDirectoryService {

    static final Duration ACTIVITY_WRITE_INTERVAL = Duration.ofMinutes(5);

    private record CachedState(boolean enabled, List<String> roles, Instant writtenAt) {
    }

    private final UserAccountRepository repository;
    private final AuditService auditService;
    private final Clock clock;
    private final Map<String, CachedState> cache = new ConcurrentHashMap<>();

    public UserDirectoryService(UserAccountRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Records that the token's subject used the application and returns whether access is enabled. */
    @Transactional
    public boolean recordActivityAndCheckEnabled(Jwt jwt) {
        Instant now = Instant.now(clock);
        List<String> roles = KeycloakRoleConverter.extractRoles(jwt);
        CachedState cached = cache.get(jwt.getSubject());
        if (cached != null && cached.roles().equals(roles)
                && Duration.between(cached.writtenAt(), now).compareTo(ACTIVITY_WRITE_INTERVAL) < 0) {
            return cached.enabled();
        }
        String username = jwt.getClaimAsString("preferred_username");
        String displayName = jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");
        UserAccount account = repository.findById(jwt.getSubject())
                .map(existing -> {
                    existing.recordActivity(orSubject(username, jwt), displayName, email, roles, now);
                    return existing;
                })
                .orElseGet(() -> repository.save(new UserAccount(jwt.getSubject(), orSubject(username, jwt),
                        displayName, email, roles, now)));
        cache.put(jwt.getSubject(), new CachedState(account.isEnabled(), roles, now));
        return account.isEnabled();
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listUsers() {
        return repository.findAll(Sort.by("username"));
    }

    @Transactional
    public UserAccount setEnabled(String userId, boolean enabled) {
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        account.setEnabled(enabled);
        cache.remove(userId);
        auditService.record(AuditEntry.of(enabled ? AuditEventType.USER_ENABLED : AuditEventType.USER_DISABLED)
                .entity("User", userId));
        return account;
    }

    private static String orSubject(String username, Jwt jwt) {
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }
}
