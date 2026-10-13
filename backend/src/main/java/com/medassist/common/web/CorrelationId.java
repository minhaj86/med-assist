package com.medassist.common.web;

import java.util.Optional;
import org.slf4j.MDC;

/** Access to the request correlation identifier stored in the logging MDC. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    public static String currentOrNull() {
        return MDC.get(MDC_KEY);
    }
}
