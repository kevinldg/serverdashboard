package com.github.kevinldg.backend.user;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.auth.PasswordGenerator;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManagementServiceTest {

    @Mock
    AuditService auditService;

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PasswordGenerator passwordGenerator;

    private UserManagementService service;

    private final Role adminRole = role("role-admin", "Admin", true);
    private final Role userRole = role("role-user", "User", false);

    private final AuthenticatedUser admin = actor("admin-1", true);
    /** A non-admin who was granted USER_MANAGE. */
    private final AuthenticatedUser userManager = actor("manager-1", false);

    @BeforeEach
    void setUp() {
        service = new UserManagementService(userRepository, roleRepository, passwordEncoder, passwordGenerator, auditService);
        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findById("role-user")).thenReturn(Optional.of(userRole));
        when(roleRepository.findBySuperuserTrue()).thenReturn(List.of(adminRole));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(passwordGenerator.generate()).thenReturn("GeneratedPassword2345");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId("new-id");
            }
            return user;
        });
    }

    @Test
    void createUserWithGeneratedPassword() {
        CreatedUserResponse response = service.createUser(new CreateUserRequest(" Alice ", "role-user", null), admin);

        assertThat(response.generatedPassword()).isEqualTo("GeneratedPassword2345");
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().role().name()).isEqualTo("User");

        User saved = savedUser();
        assertThat(saved.getPasswordHash()).isEqualTo("hash:GeneratedPassword2345");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isPasswordChangeRecommended()).isTrue();
    }

    @Test
    void createUserWithManualPassword() {
        CreatedUserResponse response = service.createUser(
                new CreateUserRequest("alice", "role-user", "a-manual-password"), admin);

        assertThat(response.generatedPassword()).isNull();
        assertThat(savedUser().getPasswordHash()).isEqualTo("hash:a-manual-password");
    }

    @Test
    void createUserRejectsWeakManualPassword() {
        assertStatus(() -> service.createUser(new CreateUserRequest("alice", "role-user", "short"), admin),
                HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertStatus(() -> service.createUser(new CreateUserRequest("ALICE", "role-user", null), admin),
                HttpStatus.CONFLICT);
    }

    @Test
    void concurrentDuplicateUsernameIsReportedAsConflict() {
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertStatus(() -> service.createUser(new CreateUserRequest("alice", "role-user", null), admin),
                HttpStatus.CONFLICT);
    }

    @Test
    void createUserWithUnknownRoleIsRejected() {
        when(roleRepository.findById("missing")).thenReturn(Optional.empty());

        assertStatus(() -> service.createUser(new CreateUserRequest("alice", "missing", null), admin),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonAdminCannotAssignAdminRole() {
        assertStatus(() -> service.createUser(new CreateUserRequest("alice", "role-admin", null), userManager),
                HttpStatus.FORBIDDEN);

        User user = stubUser("user-2", "bob", userRole, true);
        assertStatus(() -> service.updateUser("user-2", new UpdateUserRequest("bob", "role-admin", true), userManager),
                HttpStatus.FORBIDDEN);
        assertThat(user.getRoleId()).isEqualTo("role-user");
    }

    @Test
    void nonAdminCannotManageAdminAccounts() {
        stubUser("admin-2", "root", adminRole, true);

        assertStatus(() -> service.updateUser("admin-2", new UpdateUserRequest("root", "role-admin", false), userManager),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> service.resetPassword("admin-2", userManager), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.deleteUser("admin-2", userManager), HttpStatus.FORBIDDEN);
    }

    @Test
    void nonAdminCanManageRegularUsers() {
        stubUser("user-2", "bob", userRole, true);

        UserResponse response = service.updateUser("user-2", new UpdateUserRequest("bob", "role-user", false), userManager);

        assertThat(response.active()).isFalse();
        // Only changed fields are recorded
        verify(auditService).record(argThat(event -> event.action() == AuditAction.USER_UPDATE
                && event.actor().equals("manager-1")
                && event.target().equals("bob")
                && event.details().equals(Map.of("active", "yes → no"))));
    }

    @Test
    void usersCannotChangeTheirOwnRoleOrDeactivateThemselves() {
        stubUser("admin-1", "kevin", adminRole, true);

        assertStatus(() -> service.updateUser("admin-1", new UpdateUserRequest("kevin", "role-user", true), admin),
                HttpStatus.CONFLICT);
        assertStatus(() -> service.updateUser("admin-1", new UpdateUserRequest("kevin", "role-admin", false), admin),
                HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void usersCanRenameThemselves() {
        stubUser("admin-1", "kevin", adminRole, true);

        UserResponse response = service.updateUser("admin-1", new UpdateUserRequest("Kevin.L", "role-admin", true), admin);

        assertThat(response.username()).isEqualTo("kevin.l");
        verify(auditService).record(argThat(event -> event.target().equals("kevin.l")
                && event.details().equals(Map.of("username", "kevin → kevin.l"))));
    }

    @Test
    void renamingToExistingUsernameIsRejected() {
        stubUser("user-2", "bob", userRole, true);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertStatus(() -> service.updateUser("user-2", new UpdateUserRequest("alice", "role-user", true), admin),
                HttpStatus.CONFLICT);
    }

    @Test
    void lastActiveAdminCannotBeDemotedDeactivatedOrDeleted() {
        stubUser("admin-2", "root", adminRole, true);
        when(userRepository.countByRoleIdInAndActiveTrue(List.of("role-admin"))).thenReturn(1L);

        assertStatus(() -> service.updateUser("admin-2", new UpdateUserRequest("root", "role-user", true), admin),
                HttpStatus.CONFLICT);
        assertStatus(() -> service.updateUser("admin-2", new UpdateUserRequest("root", "role-admin", false), admin),
                HttpStatus.CONFLICT);
        assertStatus(() -> service.deleteUser("admin-2", admin), HttpStatus.CONFLICT);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void adminCanBeDemotedWhileAnotherActiveAdminRemains() {
        User root = stubUser("admin-2", "root", adminRole, true);
        when(userRepository.countByRoleIdInAndActiveTrue(List.of("role-admin"))).thenReturn(2L);

        service.updateUser("admin-2", new UpdateUserRequest("root", "role-user", true), admin);

        assertThat(root.getRoleId()).isEqualTo("role-user");
    }

    @Test
    void deactivatedAdminCanBeDeletedWithoutCountingAdmins() {
        User root = stubUser("admin-2", "root", adminRole, false);

        service.deleteUser("admin-2", admin);

        verify(userRepository).delete(root);
        verify(userRepository, never()).countByRoleIdInAndActiveTrue(any());
    }

    @Test
    void resetPasswordGeneratesPasswordAndEndsSessions() {
        User bob = stubUser("user-2", "bob", userRole, true);
        bob.setSessionVersion(3);

        PasswordResetResponse response = service.resetPassword("user-2", admin);

        assertThat(response.generatedPassword()).isEqualTo("GeneratedPassword2345");
        assertThat(bob.getPasswordHash()).isEqualTo("hash:GeneratedPassword2345");
        assertThat(bob.isPasswordChangeRecommended()).isTrue();
        assertThat(bob.getSessionVersion()).isEqualTo(4);
    }

    @Test
    void usersCannotResetOrDeleteThemselves() {
        stubUser("admin-1", "kevin", adminRole, true);

        assertStatus(() -> service.resetPassword("admin-1", admin), HttpStatus.CONFLICT);
        assertStatus(() -> service.deleteUser("admin-1", admin), HttpStatus.CONFLICT);
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertStatus(() -> service.deleteUser("missing", admin), HttpStatus.NOT_FOUND);
    }

    @Test
    void roleOptionsListAdminFirst() {
        when(roleRepository.findAll()).thenReturn(List.of(userRole, role("role-mod", "Moderator", false), adminRole));

        assertThat(service.listRoleOptions()).extracting(RoleOption::name).containsExactly("Admin", "Moderator", "User");
    }

    private User stubUser(String id, String username, Role role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRoleId(role.getId());
        user.setActive(active);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }

    private static Role role(String id, String name, boolean superuser) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setSuperuser(superuser);
        return role;
    }

    private static AuthenticatedUser actor(String id, boolean admin) {
        return new AuthenticatedUser(id, id, null, true, admin, 0, Set.of(Permission.USER_MANAGE));
    }
}
