package com.github.kevinldg.backend.role;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Document("roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    private String id;

    private String name;

    private Set<Permission> permissions = new HashSet<>();

    /** Built-in roles (Admin, Moderator, User) cannot be deleted. */
    private boolean builtIn;

    /** Superuser roles bypass permission checks and implicitly have every permission. */
    private boolean superuser;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Returns the permissions this role actually grants: all permissions for superuser roles,
     * otherwise the assigned permissions.
     */
    public Set<Permission> effectivePermissions() {
        if (superuser) {
            return EnumSet.allOf(Permission.class);
        }
        return permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
    }
}
