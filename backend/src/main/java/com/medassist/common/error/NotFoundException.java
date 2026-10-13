package com.medassist.common.error;

import java.util.UUID;

/** Requested resource does not exist (or is not visible to the caller). Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resourceType, Object id) {
        super(resourceType + " " + id + " was not found");
    }

    public static NotFoundException of(String resourceType, UUID id) {
        return new NotFoundException(resourceType, id);
    }
}
