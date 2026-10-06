package com.github.kevinldg.backend.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Everything that is recorded in the audit log.
 */
@Getter
@RequiredArgsConstructor
public enum AuditAction {
    LOGIN(AuditCategory.AUTHENTICATION),
    LOGIN_FAILED(AuditCategory.AUTHENTICATION),
    LOGOUT(AuditCategory.AUTHENTICATION),
    PASSWORD_CHANGE(AuditCategory.AUTHENTICATION),

    CONTAINER_START(AuditCategory.CONTAINER),
    CONTAINER_STOP(AuditCategory.CONTAINER),
    CONTAINER_RESTART(AuditCategory.CONTAINER),
    CONTAINER_FORCE_STOP(AuditCategory.CONTAINER),
    CONTAINER_DELETE(AuditCategory.CONTAINER),
    CONTAINER_CREATE(AuditCategory.CONTAINER),

    GAMESERVER_CLASSIFY(AuditCategory.GAME_SERVER),

    CONFIG_FILE_SAVE(AuditCategory.CONFIG_FILE),

    USER_CREATE(AuditCategory.USER),
    USER_UPDATE(AuditCategory.USER),
    USER_PASSWORD_RESET(AuditCategory.USER),
    USER_DELETE(AuditCategory.USER),

    ROLE_CREATE(AuditCategory.ROLE),
    ROLE_UPDATE(AuditCategory.ROLE),
    ROLE_DELETE(AuditCategory.ROLE),

    ANNOUNCEMENT_CREATE(AuditCategory.ANNOUNCEMENT),
    ANNOUNCEMENT_UPDATE(AuditCategory.ANNOUNCEMENT),
    ANNOUNCEMENT_DELETE(AuditCategory.ANNOUNCEMENT),

    MAINTENANCE_ENABLE(AuditCategory.MAINTENANCE),
    MAINTENANCE_DISABLE(AuditCategory.MAINTENANCE),

    CONTAINER_ENV_VIEW(AuditCategory.SENSITIVE_READ),
    CONTAINER_LIVE_LOGS(AuditCategory.SENSITIVE_READ),
    CONFIG_FILE_OPEN(AuditCategory.SENSITIVE_READ),
    CONFIG_FILE_BACKUP_VIEW(AuditCategory.SENSITIVE_READ),

    ACCESS_DENIED(AuditCategory.ACCESS);

    private final AuditCategory category;
}
