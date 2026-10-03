package com.github.kevinldg.backend.role;

import java.util.Set;

/**
 * @param builtIn     built-in roles (Admin, Moderator, User) cannot be renamed or deleted
 * @param admin       superuser role: has every permission and cannot be changed
 * @param permissions effective permissions (all permissions for the Admin role)
 * @param userCount   number of users with this role
 */
public record RoleResponse(String id, String name, boolean builtIn, boolean admin, Set<Permission> permissions,
                           long userCount) {
}
