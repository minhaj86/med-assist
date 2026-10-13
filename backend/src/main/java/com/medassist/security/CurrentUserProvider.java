package com.medassist.security;

import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the current user from the security context; background work without a user runs as SYSTEM. */
@Component
public class CurrentUserProvider {

    public CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return CurrentUser.SYSTEM;
        }
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                roles.add(name.substring("ROLE_".length()));
            }
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            String displayName = firstNonBlank(jwt.getClaimAsString("name"), username);
            return new CurrentUser(jwt.getSubject(), username, displayName, Set.copyOf(roles));
        }
        return new CurrentUser(authentication.getName(), authentication.getName(), authentication.getName(),
                Set.copyOf(roles));
    }

    public String currentUserId() {
        return current().id();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
