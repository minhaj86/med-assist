package com.medassist.diagnosis.scoring;

import com.medassist.diagnosis.context.ClinicalContext;

/**
 * Extension point for clinical scoring systems (e.g. NEWS2, CURB-65, Wells, HEART, CHA2DS2-VASc, qSOFA).
 *
 * <p>Real-world scores must only be implemented when the formula and its reference source are documented in the
 * implementation and reviewed clinically. Each calculator must report its version, inputs, missing inputs and a
 * reference, and must never guess missing values.
 */
public interface ClinicalScoreCalculator {

    String scoreId();

    String name();

    String version();

    ScoreResult calculate(ClinicalContext context);
}
