package com.medassist.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.medassist.security.KeycloakRoleConverter;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Synthetic users represented as Keycloak-style JWTs. */
public final class TestUsers {

    private TestUsers() {
    }

    public static RequestPostProcessor clinician() {
        return user("clinician-test-1", "dr.synthetic", "CLINICIAN");
    }

    public static RequestPostProcessor otherClinician() {
        return user("clinician-test-2", "dr.other", "CLINICIAN");
    }

    public static RequestPostProcessor admin() {
        return user("admin-test-1", "admin.synthetic", "ADMIN");
    }

    public static RequestPostProcessor knowledgeManager() {
        return user("km-test-1", "km.synthetic", "KNOWLEDGE_MANAGER");
    }

    public static RequestPostProcessor user(String subject, String username, String... roles) {
        return jwt().jwt(j -> j.subject(subject)
                        .claim("preferred_username", username)
                        .claim("name", "Synthetic " + username)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRoleConverter());
    }
}
