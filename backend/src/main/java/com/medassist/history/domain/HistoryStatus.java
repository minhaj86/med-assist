package com.medassist.history.domain;

/** Status of a history entry; the meaningful subset depends on the category (e.g. smoking: CURRENT/FORMER/NEVER). */
public enum HistoryStatus {
    ACTIVE,
    RESOLVED,
    CURRENT,
    FORMER,
    NEVER,
    UNKNOWN
}
