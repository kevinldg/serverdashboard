package com.github.kevinldg.backend.user;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.auth.PasswordGenerator;
import com.github.kevinldg.backend.auth.PasswordPolicy;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User management for administrators.
 * <p>
 * Safeguards:
 * <ul>
 *     <li>Users cannot change their own role, deactivate, delete, or reset (via admin) their own account.</li>
 *     <li>The last active administrator cannot be deactivated, demoted, or deleted.</li>
 *     <li>Only administrators can assign the Admin role or manage administrator accounts
 *         (relevant if {@code USER_MANAGE} is granted to non-admin roles).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    static final int MIN_USERNAME_LENGTH = 3;
    static final int MAX_USERNAME_LENGTH = 32;
    static final String USERNAME_PATTERN = "[A-Za-z0-9._-]+";
    static final String USERNAME_MESSAGE = "may only contain letters, digits, dots, underscores and hyphens";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;

    public List<UserResponse> listUsers() {
        Map<String, Role> roles = roleRepository.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        return userRepository.findAll(Sort.by("username")).stream()
                .map(user -> toResponse(user, roles.get(user.getRoleId())))
                .toList();
    }

    public List<RoleOption> listRoleOptions() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::isSuperuser).reversed()
                        .thenComparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(role -> new RoleOption(role.getId(), role.getName(), role.isSuperuser()))
                .toList();
    }

    public CreatedUserResponse createUser(CreateUserRequest request, AuthenticatedUser actor) {
        Role role = findRole(request.roleId());
        requireMayAssign(role, actor);

        String username = User.normalizeUsername(request.username());
        requireUsernameAvailable(username);

        String generatedPassword = null;
        String password = request.password();
        if (StringUtils.hasText(password)) {
            PasswordPolicy.validate(password);
        } else {
            generatedPassword = passwordGenerator.generate();
            password = generatedPassword;
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoleId(role.getId());
        user.setActive(true);
        user.setPasswordChangeRecommended(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(user.getCreatedAt());
        User saved = save(user);

        log.info("User '{}' created user '{}' with role '{}'", actor.getUsername(), username, role.getName());
        return new CreatedUserResponse(toResponse(saved, role), generatedPassword);
    }

    public UserResponse updateUser(String userId, UpdateUserRequest request, AuthenticatedUser actor) {
        User user = findUser(userId);
        Role currentRole = roleOf(user);
        Role newRole = findRole(request.roleId());
        requireMayManage(currentRole, actor);
        requireMayAssign(newRole, actor);

        boolean roleChanged = !newRole.getId().equals(user.getRoleId());
        boolean deactivating = user.isActive() && !request.active();
        if (isSelf(user, actor) && (roleChanged || deactivating)) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot change your own role or deactivate your own account.");
        }
        boolean losesAdminAccess = deactivating || (roleChanged && !newRole.isSuperuser());
        if (isActiveAdmin(user, currentRole) && losesAdminAccess) {
            requireAnotherActiveAdmin();
        }

        String username = User.normalizeUsername(request.username());
        if (!username.equals(user.getUsername())) {
            requireUsernameAvailable(username);
        }

        user.setUsername(username);
        user.setRoleId(newRole.getId());
        user.setActive(request.active());
        user.setUpdatedAt(Instant.now());
        User saved = save(user);

        log.info("User '{}' updated user '{}' (role '{}', {})", actor.getUsername(), username, newRole.getName(),
                saved.isActive() ? "active" : "deactivated");
        return toResponse(saved, newRole);
    }

    /**
     * Generates a new password and ends all of the user's sessions.
     */
    public PasswordResetResponse resetPassword(String userId, AuthenticatedUser actor) {
        User user = findUser(userId);
        requireMayManage(roleOf(user), actor);
        if (isSelf(user, actor)) {
            throw new ApiException(HttpStatus.CONFLICT, "Use the account page to change your own password.");
        }

        String generatedPassword = passwordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(generatedPassword));
        user.setPasswordChangeRecommended(true);
        user.setSessionVersion(user.getSessionVersion() + 1);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("User '{}' reset the password of user '{}'", actor.getUsername(), user.getUsername());
        return new PasswordResetResponse(user.getUsername(), generatedPassword);
    }

    public void deleteUser(String userId, AuthenticatedUser actor) {
        User user = findUser(userId);
        Role role = roleOf(user);
        requireMayManage(role, actor);
        if (isSelf(user, actor)) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot delete your own account.");
        }
        if (isActiveAdmin(user, role)) {
            requireAnotherActiveAdmin();
        }

        userRepository.delete(user);
        log.info("User '{}' deleted user '{}'", actor.getUsername(), user.getUsername());
    }

    private void requireMayManage(Role targetRole, AuthenticatedUser actor) {
        if (targetRole != null && targetRole.isSuperuser() && !actor.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can manage administrator accounts.");
        }
    }

    private void requireMayAssign(Role role, AuthenticatedUser actor) {
        if (role.isSuperuser() && !actor.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can assign the Admin role.");
        }
    }

    private void requireAnotherActiveAdmin() {
        List<String> adminRoleIds = roleRepository.findBySuperuserTrue().stream().map(Role::getId).toList();
        if (userRepository.countByRoleIdInAndActiveTrue(adminRoleIds) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "At least one active administrator must remain.");
        }
    }

    private void requireUsernameAvailable(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this username already exists.");
        }
    }

    /** The unique index on username also catches concurrent requests. */
    private User save(User user) {
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this username already exists.");
        }
    }

    private static boolean isSelf(User user, AuthenticatedUser actor) {
        return user.getId().equals(actor.getId());
    }

    private static boolean isActiveAdmin(User user, Role role) {
        return user.isActive() && role != null && role.isSuperuser();
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The user does not exist (anymore)."));
    }

    private Role findRole(String roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The selected role does not exist."));
    }

    private Role roleOf(User user) {
        return user.getRoleId() == null ? null : roleRepository.findById(user.getRoleId()).orElse(null);
    }

    private static UserResponse toResponse(User user, Role role) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                role != null ? new UserResponse.RoleSummary(role.getId(), role.getName()) : null,
                role != null && role.isSuperuser(),
                user.isActive(),
                user.isPasswordChangeRecommended(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
