package com.medassist.common.error;

/** Request conflicts with the current state of a resource (duplicates, finalized records). HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
