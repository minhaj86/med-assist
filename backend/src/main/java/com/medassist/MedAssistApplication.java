package com.medassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MedAssist Clinical Decision Support Platform.
 *
 * <p>A modular monolith: each top-level package below {@code com.medassist} is a module with its own
 * domain, application and API layers. Modules reference other aggregates by identifier only.
 *
 * <p>This software provides decision support for qualified clinicians. It does not provide diagnoses.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MedAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedAssistApplication.class, args);
    }
}
