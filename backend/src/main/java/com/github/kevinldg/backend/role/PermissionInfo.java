package com.github.kevinldg.backend.role;

/**
 * A permission with its group and description, for the role editor.
 */
public record PermissionInfo(Permission name, Permission.Group group, String description) {
}
