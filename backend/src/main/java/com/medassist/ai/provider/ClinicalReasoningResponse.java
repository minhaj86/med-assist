package com.medassist.ai.provider;

import java.time.Duration;

/** Raw, UNVALIDATED provider output with technical metadata. */
public record ClinicalReasoningResponse(String provider, String requestedModel, String reportedModel, String content,
                                        Integer promptTokens, Integer completionTokens, Duration latency) {
}
