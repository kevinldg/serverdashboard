package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Builds {@link AuthenticatedUser} principals from the stored user and role.
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedUserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /** Used by Spring Security during login. */
    @Override
    public AuthenticatedUser loadUserByUsername(String username) {
        return userRepository.findByUsername(User.normalizeUsername(username))
                .map(this::toAuthenticatedUser)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Reloads a logged-in user on each request, so deactivation and role changes take effect immediately.
     *
     * @return the current principal, or empty if the user no longer exists or is deactivated
     *         (the caller additionally compares the session version)
     */
    public Optional<AuthenticatedUser> loadActiveUser(String userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(this::toAuthenticatedUser);
    }

    private AuthenticatedUser toAuthenticatedUser(User user) {
        Optional<Role> role = Optional.ofNullable(user.getRoleId()).flatMap(roleRepository::findById);
        boolean admin = role.map(Role::isSuperuser).orElse(false);
        Set<Permission> permissions = role.map(Role::effectivePermissions).orElse(EnumSet.noneOf(Permission.class));
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getPasswordHash(), user.isActive(), admin,
                user.getSessionVersion(), permissions);
    }
}
