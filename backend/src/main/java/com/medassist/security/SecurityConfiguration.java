package com.medassist.security;

import com.medassist.common.web.ProblemDetailsWriter;
import com.medassist.common.web.ProblemTypes;
import com.medassist.security.user.UserAccountActivityFilter;
import com.medassist.security.user.UserDirectoryService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless OAuth2 resource server. Tokens are validated (signature via JWK set, expiry, issuer, audience)
 * by Spring Boot's resource-server auto-configuration; roles come from Keycloak realm roles.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    AuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemDetailsWriter writer) {
        return (request, response, ex) -> writer.write(request, response, HttpStatus.UNAUTHORIZED,
                ProblemTypes.UNAUTHENTICATED, "Authentication required", "A valid access token is required.");
    }

    @Bean
    AccessDeniedHandler problemAccessDeniedHandler(ProblemDetailsWriter writer) {
        return (request, response, ex) -> writer.write(request, response, HttpStatus.FORBIDDEN,
                ProblemTypes.FORBIDDEN, "Forbidden", "You do not have permission to perform this action.");
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiDocumentationChain(HttpSecurity http, SecurityProperties properties,
                                              AuthenticationEntryPoint entryPoint,
                                              AccessDeniedHandler accessDeniedHandler) throws Exception {
        http.securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    if (properties.openApiPublic()) {
                        auth.anyRequest().permitAll();
                    } else {
                        auth.anyRequest().hasRole(Roles.ADMIN);
                    }
                });
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain actuatorChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                      AccessDeniedHandler accessDeniedHandler) throws Exception {
        // The management server listens on a separate, non-public port (see management.server.port).
        http.securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().hasRole(Roles.ADMIN));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain apiChain(HttpSecurity http, SecurityProperties properties,
                                 AuthenticationEntryPoint entryPoint, AccessDeniedHandler accessDeniedHandler,
                                 UserDirectoryService userDirectoryService, ProblemDetailsWriter problemWriter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // stateless bearer tokens, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"));
                    headers.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    headers.frameOptions(f -> f.deny());
                    if (properties.hstsEnabled()) {
                        headers.httpStrictTransportSecurity(h -> h.includeSubDomains(true).maxAgeInSeconds(31536000));
                    } else {
                        headers.httpStrictTransportSecurity(h -> h.disable());
                    }
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterAfter(new UserAccountActivityFilter(userDirectoryService, problemWriter),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
