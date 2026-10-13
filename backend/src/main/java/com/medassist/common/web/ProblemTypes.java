package com.medassist.common.web;

import java.net.URI;

/** Stable problem type identifiers (RFC 7807 "type" member). */
public final class ProblemTypes {

    public static final URI VALIDATION = URI.create("urn:medassist:problem:validation-error");
    public static final URI NOT_FOUND = URI.create("urn:medassist:problem:not-found");
    public static final URI CONFLICT = URI.create("urn:medassist:problem:conflict");
    public static final URI DOMAIN_RULE = URI.create("urn:medassist:problem:domain-rule-violation");
    public static final URI UNAUTHENTICATED = URI.create("urn:medassist:problem:unauthenticated");
    public static final URI FORBIDDEN = URI.create("urn:medassist:problem:forbidden");
    public static final URI SERVICE_UNAVAILABLE = URI.create("urn:medassist:problem:service-unavailable");
    public static final URI PAYLOAD_TOO_LARGE = URI.create("urn:medassist:problem:payload-too-large");
    public static final URI BAD_REQUEST = URI.create("urn:medassist:problem:bad-request");
    public static final URI INTERNAL = URI.create("urn:medassist:problem:internal-error");

    private ProblemTypes() {
    }
}
