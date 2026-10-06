package com.github.kevinldg.backend.audit;

/**
 * Groups of audit log entries, used for filtering.
 */
public enum AuditCategory {
    AUTHENTICATION,
    CONTAINER,
    GAME_SERVER,
    CONFIG_FILE,
    USER,
    ROLE,
    ANNOUNCEMENT,
    MAINTENANCE,
    /** Viewing information that may contain secrets (environment variables, configuration files, live logs). */
    SENSITIVE_READ,
    /** Requests rejected for missing permissions. */
    ACCESS
}
