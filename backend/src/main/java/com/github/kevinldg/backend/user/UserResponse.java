package com.github.kevinldg.backend.user;

import java.time.Instant;

/**
 * A user as shown in the user management. Never contains password information.
 *
 * @param role  null if the user's role no longer exists
 * @param admin whether the user's role is a superuser role
 */
public record UserResponse(
        String id,
        String username,
        RoleSummary role,
        boolean admin,
        boolean active,
        boolean passwordChangeRecommended,
        Instant createdAt,
        Instant lastLoginAt
) {

    public record RoleSummary(String id, String name) {
    }
}
