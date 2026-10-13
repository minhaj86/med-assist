package com.medassist.common.error;

/**
 * An optional dependency (for example the AI provider) is unavailable. Core clinical functionality keeps
 * working; callers receive HTTP 503 with a non-alarming message.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
