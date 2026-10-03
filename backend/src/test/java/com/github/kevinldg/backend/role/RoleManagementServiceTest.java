package com.github.kevinldg.backend.role;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.user.UserRepository;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleManagementServiceTest {

    @Mock
    RoleRepository roleRepository;

    @Mock
    UserRepository userRepository;

    private RoleManagementService service;

    private final AuthenticatedUser admin = actor(true, Set.of());
    /** A non-admin who was granted ROLE_MANAGE plus a few container permissions. */
    private final AuthenticatedUser roleManager = actor(false,
            EnumSet.of(Permission.ROLE_MANAGE, Permission.DASHBOARD_VIEW, Permission.CONTAINER_VIEW));

    private Role adminRole;
    private Role userRole;
    private Role customRole;

    @BeforeEach
    void setUp() {
        service = new RoleManagementService(roleRepository, userRepository);
        adminRole = role("role-admin", "Admin", true, true, Set.of());
        userRole = role("role-user", "User", true, false, EnumSet.of(Permission.DASHBOARD_VIEW));
        customRole = role("role-custom", "Streamers", false, false, EnumSet.of(Permission.CONTAINER_START));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            if (role.getId() == null) {
                role.setId("new-id");
            }
            return role;
        });
    }

    @Test
    void listShowsAdminWithAllPermissionsAndUserCounts() {
        when(roleRepository.findAll()).thenReturn(List.of(customRole, userRole, adminRole));
        when(userRepository.countByRoleId("role-user")).thenReturn(4L);

        List<RoleResponse> roles = service.listRoles();

        assertThat(roles).extracting(RoleResponse::name).containsExactly("Admin", "User", "Streamers");
        assertThat(roles.getFirst().permissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(roles.get(1).userCount()).isEqualTo(4);
    }

    @Test
    void permissionsHaveGroupAndDescription() {
        assertThat(service.listPermissions())
                .hasSize(Permission.values().length)
                .allSatisfy(info -> {
                    assertThat(info.group()).isNotNull();
                    assertThat(info.description()).isNotBlank();
                });
    }

    @Test
    void createRoleCreatesCustomRole() {
        RoleResponse response = service.createRole(
                new RoleRequest(" Streamers 2 ", EnumSet.of(Permission.CONTAINER_START, Permission.CONTAINER_STOP)), admin);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Streamers 2");
        assertThat(captor.getValue().isBuiltIn()).isFalse();
        assertThat(captor.getValue().isSuperuser()).isFalse();
        assertThat(response.permissions()).containsExactlyInAnyOrder(Permission.CONTAINER_START, Permission.CONTAINER_STOP);
    }

    @Test
    void roleNamesAreUniqueIgnoringCase() {
        when(roleRepository.existsByNameIgnoreCase("streamers")).thenReturn(true);

        assertStatus(() -> service.createRole(new RoleRequest("streamers", Set.of()), admin), HttpStatus.CONFLICT);
    }

    @Test
    void concurrentDuplicateNameIsReportedAsConflict() {
        when(roleRepository.save(any(Role.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertStatus(() -> service.createRole(new RoleRequest("Streamers", Set.of()), admin), HttpStatus.CONFLICT);
    }

    @Test
    void adminRoleCannotBeChanged() {
        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(adminRole));

        assertStatus(() -> service.updateRole("role-admin", new RoleRequest("Admin", Set.of()), admin), HttpStatus.CONFLICT);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void builtInRolePermissionsCanBeChangedButNotTheName() {
        when(roleRepository.findById("role-user")).thenReturn(Optional.of(userRole));

        RoleResponse response = service.updateRole("role-user",
                new RoleRequest("User", EnumSet.of(Permission.DASHBOARD_VIEW, Permission.CONTAINER_LOGS_VIEW)), admin);
        assertThat(response.permissions()).contains(Permission.CONTAINER_LOGS_VIEW);

        assertStatus(() -> service.updateRole("role-user", new RoleRequest("Players", Set.of()), admin), HttpStatus.CONFLICT);
    }

    @Test
    void customRoleCanBeRenamed() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));

        RoleResponse response = service.updateRole("role-custom", new RoleRequest("Helpers", Set.of()), admin);

        assertThat(response.name()).isEqualTo("Helpers");
    }

    @Test
    void nonAdminCannotGrantPermissionsTheyDoNotHave() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));

        assertThatThrownBy(() -> service.createRole(
                new RoleRequest("Escalation", EnumSet.of(Permission.USER_MANAGE)), roleManager))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getMessage()).contains("USER_MANAGE");
                });
        assertStatus(() -> service.updateRole("role-custom",
                new RoleRequest("Streamers", EnumSet.of(Permission.CONTAINER_START, Permission.CONTAINER_DELETE)), roleManager),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void nonAdminCanGrantOwnPermissionsAndKeepOrRemoveOthers() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));

        // CONTAINER_START is kept although the role manager does not have it; CONTAINER_VIEW is their own.
        RoleResponse response = service.updateRole("role-custom",
                new RoleRequest("Streamers", EnumSet.of(Permission.CONTAINER_START, Permission.CONTAINER_VIEW)), roleManager);
        assertThat(response.permissions()).containsExactlyInAnyOrder(Permission.CONTAINER_START, Permission.CONTAINER_VIEW);

        response = service.updateRole("role-custom", new RoleRequest("Streamers", Set.of()), roleManager);
        assertThat(response.permissions()).isEmpty();
    }

    @Test
    void builtInRolesCannotBeDeleted() {
        when(roleRepository.findById("role-user")).thenReturn(Optional.of(userRole));

        assertStatus(() -> service.deleteRole("role-user", admin), HttpStatus.CONFLICT);
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void rolesInUseCannotBeDeleted() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));
        when(userRepository.countByRoleId("role-custom")).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteRole("role-custom", admin))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("2 users");
                });
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void unusedCustomRoleIsDeleted() {
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(customRole));

        service.deleteRole("role-custom", admin);

        verify(roleRepository).delete(customRole);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }

    private static Role role(String id, String name, boolean builtIn, boolean superuser, Set<Permission> permissions) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setBuiltIn(builtIn);
        role.setSuperuser(superuser);
        role.setPermissions(EnumSet.noneOf(Permission.class));
        role.getPermissions().addAll(permissions);
        return role;
    }

    private static AuthenticatedUser actor(boolean admin, Set<Permission> permissions) {
        return new AuthenticatedUser("actor", "actor", null, true, admin, 0, permissions);
    }
}
