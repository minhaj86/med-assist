package com.medassist.diagnosis.context;

import com.medassist.common.domain.DataProvenance;
import java.time.Instant;
import java.util.UUID;

/**
 * A clinician-recorded (or clinician-confirmed) fact with a stable key (F1, F2, ...) that rules, scores and AI
 * output reference as evidence.
 *
 * @param identity stable identity used to compare snapshots across assessment versions
 *                 (e.g. "VITAL_SIGN:HEART_RATE" so a new measurement shows as a change, not add/remove)
 */
public record ClinicalFact(String key, FactCategory category, String description, String sourceType, UUID sourceId,
                           DataProvenance provenance, Instant observedAt, String identity) {
}
