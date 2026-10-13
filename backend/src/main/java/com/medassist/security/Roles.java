package com.medassist.security;

import java.util.Set;

/** Application roles. Identity-provider realm roles with these names are mapped to {@code ROLE_*} authorities. */
public final class Roles {

    public static final String CLINICIAN = "CLINICIAN";
    public static final String ADMIN = "ADMIN";
    public static final String KNOWLEDGE_MANAGER = "KNOWLEDGE_MANAGER";

    public static final Set<String> ALL = Set.of(CLINICIAN, ADMIN, KNOWLEDGE_MANAGER);

    private Roles() {
    }
}
