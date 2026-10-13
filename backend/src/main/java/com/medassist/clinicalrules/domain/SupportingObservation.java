package com.medassist.clinicalrules.domain;

import java.util.UUID;

/** A clinical fact that caused (or was examined by) a rule, linked to its source record. */
public record SupportingObservation(String factKey, String sourceType, UUID sourceId, String description) {
}
