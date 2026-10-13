package com.medassist.security;

import java.util.Set;

/** The authenticated principal as seen by application services. */
public record CurrentUser(String id, String username, String displayName, Set<String> roles) {

    public static final CurrentUser SYSTEM = new CurrentUser("system", "system", "MedAssist system", Set.of());

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
