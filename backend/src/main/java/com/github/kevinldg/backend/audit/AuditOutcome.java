package com.github.kevinldg.backend.audit;

public enum AuditOutcome {
    SUCCESS,
    /** The action was attempted but failed, e.g. a Docker error or a conflict. */
    FAILURE,
    /** The request was rejected for missing permissions. */
    DENIED
}
