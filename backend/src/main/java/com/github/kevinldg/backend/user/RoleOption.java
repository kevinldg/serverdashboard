package com.github.kevinldg.backend.user;

/**
 * A role that can be assigned to users.
 *
 * @param admin whether the role is a superuser role (may only be assigned by administrators)
 */
public record RoleOption(String id, String name, boolean admin) {
}
