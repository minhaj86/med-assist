package com.medassist.ai.provider;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Decorates a provider with a bulkhead (concurrency limit), circuit breaker and bounded retry for transient
 * failures, and records technical metrics (no patient data in tags).
 */
public class ResilientClinicalAiProvider implements ClinicalAiProvider {

    private final ClinicalAiProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final MeterRegistry meterRegistry;

    public ResilientClinicalAiProvider(ClinicalAiProvider delegate, AiProperties.Resilience settings,
                                       MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(Math.min(5, settings.slidingWindowSize()))
                .waitDurationInOpenState(settings.openStateWait())
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(e -> e instanceof AiProviderException ape && ape.category().providerFailure())
                .build());
        this.circuitBreaker = registry.circuitBreaker("llm-provider");
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        this.retry = Retry.of("llm-provider", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(750))
                .retryOnException(e -> e instanceof AiProviderException ape && ape.category().retryable())
                .build());
        this.bulkhead = Bulkhead.of("llm-provider", BulkheadConfig.custom()
                .maxConcurrentCalls(Math.max(1, settings.maxConcurrentCalls()))
                .maxWaitDuration(Duration.ofSeconds(10))
                .build());
    }

    @Override
    public ProviderDescriptor descriptor() {
        ProviderDescriptor d = delegate.descriptor();
        String state = circuitBreaker.getState() == CircuitBreaker.State.OPEN
                ? "Circuit open: provider temporarily bypassed after repeated failures" : d.statusMessage();
        return new ProviderDescriptor(d.provider(), d.model(), d.endpoint(), d.enabled(), state);
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    @Override
    public ClinicalReasoningResponse reason(ClinicalReasoningRequest request) {
        Supplier<ClinicalReasoningResponse> call = () -> delegate.reason(request);
        Supplier<ClinicalReasoningResponse> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, Bulkhead.decorateSupplier(bulkhead, call)));
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            outcome = "circuit_open";
            throw failure(AiProviderException.Category.CIRCUIT_OPEN,
                    new AiProviderException(AiProviderException.Category.CIRCUIT_OPEN,
                            "AI provider temporarily unavailable (circuit open)", e));
        } catch (BulkheadFullException e) {
            outcome = "bulkhead_full";
            throw failure(AiProviderException.Category.BULKHEAD_FULL,
                    new AiProviderException(AiProviderException.Category.BULKHEAD_FULL, "AI provider is busy", e));
        } catch (AiProviderException e) {
            outcome = e.category().name().toLowerCase();
            throw failure(e.category(), e);
        } finally {
            sample.stop(Timer.builder("llm_duration").description("LLM provider call duration")
                    .tag("provider", delegate.descriptor().provider())
                    .tag("purpose", request.purpose())
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    private AiProviderException failure(AiProviderException.Category category, AiProviderException e) {
        Counter.builder("llm_failures").description("LLM provider failures by category")
                .tag("provider", delegate.descriptor().provider())
                .tag("category", category.name())
                .register(meterRegistry).increment();
        return e;
    }
}
