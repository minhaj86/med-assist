package com.medassist.security.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local directory entry for a user known from the identity provider. The identity provider remains the
 * source of truth for credentials and role assignments; MedAssist keeps an activity record and a local
 * access switch so administrators can suspend application access.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "username", nullable = false, length = 200)
    private String username;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "roles", nullable = false, length = 500)
    private String roles;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UserAccount(String id, String username, String displayName, String email, List<String> roles,
                       Instant now) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.roles = String.join(",", roles);
        this.enabled = true;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    public void recordActivity(String username, String displayName, String email, List<String> roles, Instant now) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.roles = String.join(",", roles);
        this.lastSeenAt = now;
    }

    public List<String> roleList() {
        return roles.isBlank() ? List.of() : Arrays.asList(roles.split(","));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
