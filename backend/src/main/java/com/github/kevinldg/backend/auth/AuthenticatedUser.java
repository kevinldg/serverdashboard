package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.role.Permission;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Security principal of a logged-in user.
 * <p>
 * Authorities are the user's effective permissions (e.g. {@code CONTAINER_START}) plus
 * {@link #ADMIN_AUTHORITY} for superusers.
 */
@Getter
public class AuthenticatedUser implements UserDetails, CredentialsContainer {

    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final String id;
    private final String username;
    private String passwordHash;
    private final boolean active;
    private final boolean admin;
    /** {@link com.github.kevinldg.backend.user.User#getSessionVersion()} at the time of login. */
    private final int sessionVersion;
    private final Set<Permission> permissions;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUser(String id, String username, String passwordHash, boolean active, boolean admin,
                             int sessionVersion, Set<Permission> permissions) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.active = active;
        this.admin = admin;
        this.sessionVersion = sessionVersion;
        this.permissions = Set.copyOf(permissions);

        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        permissions.forEach(permission -> grantedAuthorities.add(new SimpleGrantedAuthority(permission.name())));
        if (admin) {
            grantedAuthorities.add(new SimpleGrantedAuthority(ADMIN_AUTHORITY));
        }
        this.authorities = List.copyOf(grantedAuthorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /** Removes the password hash after authentication so it is not kept in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
