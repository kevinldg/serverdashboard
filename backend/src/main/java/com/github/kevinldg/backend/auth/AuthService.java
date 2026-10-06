package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Current user, password changes, and the audit log entries for logins and logouts
 * (the only entries that store the client address).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public CurrentUserResponse getCurrentUser(String userId) {
        User user = findUser(userId);
        Optional<Role> role = Optional.ofNullable(user.getRoleId()).flatMap(roleRepository::findById);

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                role.map(r -> new CurrentUserResponse.RoleSummary(r.getId(), r.getName())).orElse(null),
                role.map(Role::isSuperuser).orElse(false),
                role.map(Role::effectivePermissions).orElse(EnumSet.noneOf(Permission.class)),
                user.isPasswordChangeRecommended()
        );
    }

    public void recordSuccessfulLogin(String userId, String clientAddress) {
        User user = findUser(userId);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.record(AuditEvent.success(user.getUsername(), AuditAction.LOGIN, user.getUsername(), "Logged in")
                .ip(clientAddress));
    }

    /** A login rejected by Spring Security (wrong password, unknown or deactivated user). */
    public void recordFailedLogin(String username, String clientAddress, AuthenticationException exception) {
        String reason;
        if (exception instanceof AccountStatusException) {
            reason = "account deactivated";
        } else if (exception instanceof BadCredentialsException) {
            // Spring Security reports unknown users as bad credentials, too
            String normalized = User.normalizeUsername(username);
            reason = StringUtils.hasText(normalized) && userRepository.existsByUsername(normalized)
                    ? "wrong password" : "unknown user";
        } else {
            reason = "error: " + exception.getMessage();
        }
        auditService.record(failedLogin(username, clientAddress, reason));
    }

    /**
     * A login rejected before or after checking the password: too many failed attempts, or maintenance mode.
     * Repeated attempts are recorded once per deduplication interval, so a client retrying in a loop does not flood
     * the audit log.
     */
    public void recordBlockedLogin(String username, String clientAddress, String reason) {
        auditService.recordDeduplicated(failedLogin(username, clientAddress, reason));
    }

    public void recordLogout(String username, String clientAddress) {
        auditService.record(AuditEvent.success(username, AuditAction.LOGOUT, username, "Logged out").ip(clientAddress));
    }

    private static AuditEvent failedLogin(String username, String clientAddress, String reason) {
        String normalized = StringUtils.hasText(username) ? User.normalizeUsername(username) : "(no username)";
        return AuditEvent.failure(normalized, AuditAction.LOGIN_FAILED, normalized, "Login failed: " + reason, null)
                .ip(clientAddress);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.record(AuditEvent.failure(user.getUsername(), AuditAction.PASSWORD_CHANGE, user.getUsername(),
                    "Could not change the own password", "The current password is incorrect."));
            throw new ApiException(HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        }
        PasswordPolicy.validate(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangeRecommended(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(AuditEvent.success(user.getUsername(), AuditAction.PASSWORD_CHANGE, user.getUsername(),
                "Changed the own password"));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
