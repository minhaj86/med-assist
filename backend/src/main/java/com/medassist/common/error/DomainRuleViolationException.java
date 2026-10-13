package com.medassist.common.error;

/** A domain invariant or workflow rule was violated by an otherwise well-formed request. HTTP 422. */
public class DomainRuleViolationException extends RuntimeException {

    public DomainRuleViolationException(String message) {
        super(message);
    }
}
