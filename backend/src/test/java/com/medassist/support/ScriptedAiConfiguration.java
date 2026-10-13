package com.medassist.support;

import com.medassist.ai.provider.ClinicalAiProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class ScriptedAiConfiguration {

    @Bean
    @Primary
    ScriptedClinicalAiProvider scriptedClinicalAiProvider() {
        return new ScriptedClinicalAiProvider();
    }

    @Bean
    ClinicalAiProviderMarker marker(ClinicalAiProvider provider) {
        return new ClinicalAiProviderMarker(provider);
    }

    /** Fails context startup if the scripted provider is not the one injected (guards against real AI calls). */
    public record ClinicalAiProviderMarker(ClinicalAiProvider provider) {
        public ClinicalAiProviderMarker {
            if (!(provider instanceof ScriptedClinicalAiProvider)) {
                throw new IllegalStateException("Tests must use the scripted AI provider");
            }
        }
    }
}
