package com.medassist.observation.domain;

/** ACVPU level of consciousness. */
public enum ConsciousnessLevel {
    ALERT("Alert"),
    NEW_CONFUSION("New confusion"),
    VOICE("Responds to voice"),
    PAIN("Responds to pain"),
    UNRESPONSIVE("Unresponsive");

    private final String displayName;

    ConsciousnessLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
