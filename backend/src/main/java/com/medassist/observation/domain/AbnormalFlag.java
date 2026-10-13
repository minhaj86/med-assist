package com.medassist.observation.domain;

/** Laboratory interpretation flag. CRITICAL_* flags are only ever taken from the reporting laboratory. */
public enum AbnormalFlag {
    NORMAL,
    LOW,
    HIGH,
    CRITICAL_LOW,
    CRITICAL_HIGH,
    ABNORMAL
}
