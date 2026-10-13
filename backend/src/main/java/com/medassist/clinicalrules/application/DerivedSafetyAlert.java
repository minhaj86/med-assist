package com.medassist.clinicalrules.application;

import com.medassist.common.domain.AlertOrigin;
import com.medassist.common.domain.ClinicalSeverity;
import java.util.List;

/** A deterministic safety alert derived from a rule result or a clinical score. */
public record DerivedSafetyAlert(AlertOrigin origin, ClinicalSeverity severity, String title, String explanation,
                                 String recommendedAction, String sourceReference, List<String> supportingFactKeys,
                                 boolean demonstrationOnly) {
}
