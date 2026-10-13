package com.medassist.diagnosis.scoring;

/** One input variable's contribution to a score. */
public record ScoreComponent(String variable, String value, int points, String rationale, String factKey) {
}
