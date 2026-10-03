package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct-password-123";

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder);

        user = new User();
        user.setId("user-1");
        user.setUsername("alice");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRoleId("role-1");
        user.setActive(true);
        user.setPasswordChangeRecommended(true);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
    }

    @Test
    void currentUserOfSuperuserRoleHasAllPermissions() {
        Role role = new Role();
        role.setId("role-1");
        role.setName("Admin");
        role.setSuperuser(true);
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));

        CurrentUserResponse response = authService.getCurrentUser("user-1");

        assertThat(response.admin()).isTrue();
        assertThat(response.permissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(response.role()).isEqualTo(new CurrentUserResponse.RoleSummary("role-1", "Admin"));
    }

    @Test
    void currentUserOfRegularRoleHasAssignedPermissions() {
        Role role = new Role();
        role.setId("role-1");
        role.setName("User");
        role.setPermissions(EnumSet.of(Permission.DASHBOARD_VIEW));
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(role));

        CurrentUserResponse response = authService.getCurrentUser("user-1");

        assertThat(response.admin()).isFalse();
        assertThat(response.permissions()).containsExactly(Permission.DASHBOARD_VIEW);
    }

    @Test
    void changePasswordStoresNewHashAndClearsRecommendation() {
        authService.changePassword("user-1", new ChangePasswordRequest(PASSWORD, "a-new-secure-password"));

        assertThat(passwordEncoder.matches("a-new-secure-password", user.getPasswordHash())).isTrue();
        assertThat(user.isPasswordChangeRecommended()).isFalse();
        assertThat(user.getUpdatedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        assertThatThrownBy(() -> authService.changePassword("user-1",
                new ChangePasswordRequest("wrong-password", "a-new-secure-password")))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsPasswordsLongerThan72Bytes() {
        // 48 Java chars (within the 72-character validation limit), but 96 bytes in UTF-8
        String newPassword = "🔑".repeat(24);

        assertThatThrownBy(() -> authService.changePassword("user-1", new ChangePasswordRequest(PASSWORD, newPassword)))
                .isInstanceOf(ApiException.class)
                .hasMessage("The password is too long.");
        verify(userRepository, never()).save(any());
    }
}
