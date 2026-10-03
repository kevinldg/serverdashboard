package com.github.kevinldg.backend.role;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Permissions that can be assigned to roles.
 * <p>
 * Permissions are defined in code; roles stored in MongoDB reference them by name.
 * Superuser roles implicitly have every permission.
 */
@Getter
@RequiredArgsConstructor
public enum Permission {
    DASHBOARD_VIEW(Group.DASHBOARD, "View the dashboard with the container overview and announcements"),

    CONTAINER_VIEW(Group.CONTAINERS, "Open container details (general information, storage, configuration)"),
    CONTAINER_LOGS_VIEW(Group.CONTAINERS, "Read container logs"),
    CONTAINER_ENV_VIEW(Group.CONTAINERS, "See environment variables of containers (they often contain passwords)"),
    CONTAINER_START(Group.CONTAINERS, "Start containers"),
    CONTAINER_STOP(Group.CONTAINERS, "Stop containers with a clean shutdown"),
    CONTAINER_RESTART(Group.CONTAINERS, "Restart containers"),
    CONTAINER_FORCE_STOP(Group.CONTAINERS, "Force-stop containers without a clean shutdown (possible data loss)"),
    CONTAINER_CREATE(Group.CONTAINERS, "Create containers"),
    CONTAINER_DELETE(Group.CONTAINERS, "Delete stopped containers (volumes are kept)"),

    GAMESERVER_MANAGE(Group.GAME_SERVERS, "Classify containers as game servers"),
    GAMESERVER_CONFIG_VIEW(Group.GAME_SERVERS, "View game server configuration files"),
    GAMESERVER_CONFIG_EDIT(Group.GAME_SERVERS, "Edit game server configuration files"),

    USER_MANAGE(Group.ADMINISTRATION, "Manage users: create, edit, reset passwords, delete"),
    ROLE_MANAGE(Group.ADMINISTRATION, "Manage roles and their permissions"),
    ANNOUNCEMENT_MANAGE(Group.ADMINISTRATION, "Manage dashboard announcements"),
    MAINTENANCE_MANAGE(Group.ADMINISTRATION, "Enable and disable maintenance mode"),
    SYSTEM_INFO_VIEW(Group.ADMINISTRATION, "View system information");

    private final Group group;
    private final String description;

    public enum Group {
        DASHBOARD, CONTAINERS, GAME_SERVERS, ADMINISTRATION
    }
}
