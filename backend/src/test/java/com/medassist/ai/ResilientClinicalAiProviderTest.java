package com.medassist.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.ai.provider.AiProperties;
import com.medassist.ai.provider.AiProviderException;
import com.medassist.ai.provider.ClinicalAiProvider;
import com.medassist.ai.provider.ClinicalReasoningRequest;
import com.medassist.ai.provider.ClinicalReasoningResponse;
import com.medassist.ai.provider.ResilientClinicalAiProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientClinicalAiProviderTest {

    private static final ClinicalReasoningRequest REQUEST = new ClinicalReasoningRequest("TEST", "p", "1", "s", "u",
            0.1, 100, "{}");

    private static class CountingProvider implements ClinicalAiProvider {
        final AtomicInteger calls = new AtomicInteger();
        final AiProviderException.Category failWith;

        CountingProvider(AiProviderException.Category failWith) {
            this.failWith = failWith;
        }

        @Override
        public ClinicalReasoningResponse reason(ClinicalReasoningRequest request) {
            calls.incrementAndGet();
            if (failWith != null) {
                throw new AiProviderException(failWith, "fail");
            }
            return new ClinicalReasoningResponse("fake", "m", "m", "{}", 1, 1, Duration.ZERO);
        }

        @Override
        public ProviderDescriptor descriptor() {
            return new ProviderDescriptor("fake", "m", "e", true, "ok");
        }
    }

    private static AiProperties.Resilience settings() {
        return new AiProperties.Resilience(50, 4, Duration.ofMinutes(5), 2);
    }

    @Test
    void retriesTransientFailuresOnce() {
        CountingProvider delegate = new CountingProvider(AiProviderException.Category.TIMEOUT);
        ResilientClinicalAiProvider provider = new ResilientClinicalAiProvider(delegate, settings(), new SimpleMeterRegistry());
        assertThatThrownBy(() -> provider.reason(REQUEST)).isInstanceOf(AiProviderException.class);
        assertThat(delegate.calls.get()).isEqualTo(2);
    }

    @Test
    void doesNotRetryNonTransientFailures() {
        CountingProvider delegate = new CountingProvider(AiProviderException.Category.BAD_REQUEST);
        ResilientClinicalAiProvider provider = new ResilientClinicalAiProvider(delegate, settings(), new SimpleMeterRegistry());
        assertThatThrownBy(() -> provider.reason(REQUEST)).isInstanceOf(AiProviderException.class);
        assertThat(delegate.calls.get()).isEqualTo(1);
    }

    @Test
    void opensCircuitAfterRepeatedProviderFailures() {
        CountingProvider delegate = new CountingProvider(AiProviderException.Category.UNAVAILABLE);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ResilientClinicalAiProvider provider = new ResilientClinicalAiProvider(delegate, settings(), registry);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> provider.reason(REQUEST)).isInstanceOf(AiProviderException.class);
        }
        assertThatThrownBy(() -> provider.reason(REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class,
                        e -> assertThat(e.category()).isEqualTo(AiProviderException.Category.CIRCUIT_OPEN));
        assertThat(provider.descriptor().statusMessage()).contains("Circuit open");
        assertThat(registry.find("llm_failures").counters()).isNotEmpty();
    }

    @Test
    void passesThroughSuccessfulCalls() {
        CountingProvider delegate = new CountingProvider(null);
        ResilientClinicalAiProvider provider = new ResilientClinicalAiProvider(delegate, settings(), new SimpleMeterRegistry());
        assertThat(provider.reason(REQUEST).content()).isEqualTo("{}");
    }
}
