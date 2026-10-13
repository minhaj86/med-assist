package com.medassist.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application security settings. Token validation (JWK set, issuer, audience) is configured through the
 * standard {@code spring.security.oauth2.resourceserver.jwt.*} properties.
 *
 * @param allowedOrigins browser origins allowed by CORS
 * @param openApiPublic  whether API documentation is reachable without authentication (local only)
 * @param hstsEnabled    whether to send Strict-Transport-Security (enable behind TLS)
 */
@ConfigurationProperties(prefix = "medassist.security")
public record SecurityProperties(
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins,
        @DefaultValue("false") boolean openApiPublic,
        @DefaultValue("false") boolean hstsEnabled) {
}
