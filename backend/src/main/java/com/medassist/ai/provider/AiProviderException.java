package com.medassist.ai.provider;

/** Provider failure with a category that drives retry, circuit-breaking and clinician messaging. */
public class AiProviderException extends RuntimeException {

    public enum Category {
        NOT_CONFIGURED(false, false),
        TIMEOUT(true, true),
        RATE_LIMITED(true, true),
        UNAVAILABLE(true, true),
        AUTHENTICATION(false, true),
        BAD_REQUEST(false, false),
        CIRCUIT_OPEN(false, false),
        BULKHEAD_FULL(false, false),
        EMPTY_RESPONSE(true, true);

        private final boolean retryable;
        private final boolean providerFailure;

        Category(boolean retryable, boolean providerFailure) {
            this.retryable = retryable;
            this.providerFailure = providerFailure;
        }

        public boolean retryable() {
            return retryable;
        }

        /** Whether the failure should count towards opening the circuit breaker. */
        public boolean providerFailure() {
            return providerFailure;
        }
    }

    private final Category category;

    public AiProviderException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public AiProviderException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
