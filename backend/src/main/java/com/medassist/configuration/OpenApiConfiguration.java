package com.medassist.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI medAssistOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MedAssist Clinical Decision Support API")
                        .version("v1")
                        .description("""
                                Clinical decision support for qualified clinicians. AI-generated output is decision \
                                support only and is never a diagnosis. Clinicians remain responsible for all \
                                medical decisions.""")
                        .license(new License().name("Proprietary - demonstration software")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("OIDC access token issued by the configured identity provider (Keycloak).")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
