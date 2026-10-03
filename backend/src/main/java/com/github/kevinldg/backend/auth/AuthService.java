package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

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

    public void recordSuccessfulLogin(String userId) {
        User user = findUser(userId);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        }
        PasswordPolicy.validate(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangeRecommended(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
