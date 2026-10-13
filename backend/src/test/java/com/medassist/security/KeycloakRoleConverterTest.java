package com.medassist.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleConverterTest {

    @Test
    void mapsOnlyKnownRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u1")
                .claim("realm_access", Map.of("roles", List.of("CLINICIAN", "offline_access", "uma_authorization", "ADMIN")))
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60))
                .build();
        assertThat(new KeycloakRoleConverter().convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_CLINICIAN");
    }

    @Test
    void tokenWithoutRealmRolesHasNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u1").claim("scope", "openid")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60)).build();
        assertThat(new KeycloakRoleConverter().convert(jwt)).isEmpty();
    }
}
