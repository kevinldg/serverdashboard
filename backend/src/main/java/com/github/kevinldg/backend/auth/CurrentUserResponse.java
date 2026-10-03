package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.role.Permission;

import java.util.Set;

public record CurrentUserResponse(
        String id,
        String username,
        RoleSummary role,
        boolean admin,
        Set<Permission> permissions,
        boolean passwordChangeRecommended
) {

    public record RoleSummary(String id, String name) {
    }
}
