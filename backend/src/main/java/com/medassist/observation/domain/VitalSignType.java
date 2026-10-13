package com.medassist.observation.domain;

import com.medassist.terminology.loinc.LoincVitalSignCodes;
import java.math.BigDecimal;

/**
 * Supported vital signs with canonical UCUM units. LOINC codes are from the HL7 FHIR R4 Vital Signs profile.
 * The plausible ranges are data-entry sanity checks to catch typing errors - they are NOT clinical thresholds.
 */
public enum VitalSignType {
    SYSTOLIC_BLOOD_PRESSURE("Systolic blood pressure", LoincVitalSignCodes.SYSTOLIC_BLOOD_PRESSURE, "mm[Hg]", "30", "300"),
    DIASTOLIC_BLOOD_PRESSURE("Diastolic blood pressure", LoincVitalSignCodes.DIASTOLIC_BLOOD_PRESSURE, "mm[Hg]", "10", "200"),
    HEART_RATE("Heart rate", LoincVitalSignCodes.HEART_RATE, "/min", "10", "300"),
    RESPIRATORY_RATE("Respiratory rate", LoincVitalSignCodes.RESPIRATORY_RATE, "/min", "2", "80"),
    OXYGEN_SATURATION("Oxygen saturation (pulse oximetry)", LoincVitalSignCodes.OXYGEN_SATURATION_PULSE_OXIMETRY, "%", "30", "100"),
    BODY_TEMPERATURE("Body temperature", LoincVitalSignCodes.BODY_TEMPERATURE, "Cel", "25", "45"),
    BODY_WEIGHT("Body weight", LoincVitalSignCodes.BODY_WEIGHT, "kg", "0.3", "500"),
    BODY_HEIGHT("Body height", LoincVitalSignCodes.BODY_HEIGHT, "cm", "20", "260"),
    BODY_MASS_INDEX("Body mass index", LoincVitalSignCodes.BODY_MASS_INDEX, "kg/m2", "5", "150"),
    /** ACVPU scale; coded value, no LOINC code shipped. */
    CONSCIOUSNESS_LEVEL("Level of consciousness (ACVPU)", null, null, null, null);

    private final String displayName;
    private final String loincCode;
    private final String unit;
    private final BigDecimal plausibleMin;
    private final BigDecimal plausibleMax;

    VitalSignType(String displayName, String loincCode, String unit, String plausibleMin, String plausibleMax) {
        this.displayName = displayName;
        this.loincCode = loincCode;
        this.unit = unit;
        this.plausibleMin = plausibleMin == null ? null : new BigDecimal(plausibleMin);
        this.plausibleMax = plausibleMax == null ? null : new BigDecimal(plausibleMax);
    }

    public String displayName() {
        return displayName;
    }

    public String loincCode() {
        return loincCode;
    }

    public String unit() {
        return unit;
    }

    public boolean isCoded() {
        return this == CONSCIOUSNESS_LEVEL;
    }

    public BigDecimal plausibleMin() {
        return plausibleMin;
    }

    public BigDecimal plausibleMax() {
        return plausibleMax;
    }

    public boolean isPlausible(BigDecimal value) {
        return value != null && value.compareTo(plausibleMin) >= 0 && value.compareTo(plausibleMax) <= 0;
    }
}
