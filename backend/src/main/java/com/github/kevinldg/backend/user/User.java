package com.github.kevinldg.backend.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Locale;

@Document("users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private String id;

    /** Always stored normalized, see {@link #normalizeUsername(String)}. */
    private String username;

    private String passwordHash;

    private String roleId;

    /** Deactivated users remain stored but cannot authenticate. */
    private boolean active;

    /** Set for new or reset passwords; users are encouraged (not forced) to change them. */
    private boolean passwordChangeRecommended;

    /**
     * Incremented when an administrator resets the password. Sessions created with an older version
     * are ended on their next request.
     */
    private int sessionVersion;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant lastLoginAt;

    /**
     * Usernames are case-insensitive: they are trimmed and lower-cased before storing or looking them up.
     */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
