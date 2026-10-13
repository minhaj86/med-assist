package com.medassist.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FhirConfiguration {

    /** FhirContext is expensive to create and thread-safe: one shared R4 instance. */
    @Bean
    FhirContext fhirContext() {
        return FhirContext.forR4Cached();
    }
}
