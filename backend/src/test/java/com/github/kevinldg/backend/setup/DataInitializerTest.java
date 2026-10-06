package com.github.kevinldg.backend.setup;

import com.github.kevinldg.backend.audit.AuditProperties;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    private static final Duration RETENTION = Duration.ofDays(365);

    @Mock
    MongoOperations mongoOperations;

    @Mock
    IndexOperations indexOperations;

    @Mock
    RoleRepository roleRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        when(mongoOperations.indexOps(any(Class.class))).thenReturn(indexOperations);
    }

    @Test
    void createsBuiltInRolesAndInitialAdminOnEmptyDatabase() {
        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId("id-" + role.getName());
            return role;
        });
        when(userRepository.existsByRoleId("id-Admin")).thenReturn(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("initial-password")).thenReturn("hashed");

        initializer(" Admin ", "initial-password").run(null);

        ArgumentCaptor<Role> roles = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(3)).save(roles.capture());
        Map<String, Role> rolesByName = roles.getAllValues().stream()
                .collect(Collectors.toMap(Role::getName, Function.identity()));

        assertThat(rolesByName.get("Admin").isSuperuser()).isTrue();
        assertThat(rolesByName.get("Moderator").getPermissions()).containsExactlyInAnyOrder(
                Permission.DASHBOARD_VIEW, Permission.CONTAINER_VIEW, Permission.CONTAINER_LOGS_VIEW,
                Permission.CONTAINER_START, Permission.CONTAINER_STOP, Permission.CONTAINER_RESTART);
        assertThat(rolesByName.get("User").getPermissions()).containsExactlyInAnyOrder(
                Permission.DASHBOARD_VIEW, Permission.CONTAINER_VIEW, Permission.CONTAINER_LOGS_VIEW);
        assertThat(roles.getAllValues()).allMatch(Role::isBuiltIn);
        assertThat(roles.getAllValues()).filteredOn(role -> !role.getName().equals("Admin"))
                .noneMatch(Role::isSuperuser);

        ArgumentCaptor<User> admin = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(admin.capture());
        assertThat(admin.getValue().getUsername()).isEqualTo("admin");
        assertThat(admin.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(admin.getValue().getRoleId()).isEqualTo("id-Admin");
        assertThat(admin.getValue().isActive()).isTrue();
        assertThat(admin.getValue().isPasswordChangeRecommended()).isTrue();

        // username, role name, category name, audit log retention
        verify(indexOperations, times(4)).createIndex(any());
    }

    @Test
    void replacesAuditRetentionIndexWhenTheRetentionChanged() {
        stubExistingRoles();
        when(userRepository.existsByRoleId("id-Admin")).thenReturn(true);
        when(indexOperations.getIndexInfo()).thenReturn(List.of(ttlIndex(Duration.ofDays(90))));

        initializer("admin", "initial-password").run(null);

        verify(indexOperations).dropIndex(DataInitializer.AUDIT_TTL_INDEX);
        verify(indexOperations, times(4)).createIndex(any());
    }

    @Test
    void keepsAuditRetentionIndexWithUnchangedRetention() {
        stubExistingRoles();
        when(userRepository.existsByRoleId("id-Admin")).thenReturn(true);
        when(indexOperations.getIndexInfo()).thenReturn(List.of(ttlIndex(RETENTION)));

        initializer("admin", "initial-password").run(null);

        verify(indexOperations, never()).dropIndex(anyString());
    }

    @Test
    void keepsExistingRolesAndAdmins() {
        stubExistingRoles();
        when(userRepository.existsByRoleId("id-Admin")).thenReturn(true);

        initializer("admin", "initial-password").run(null);

        verify(roleRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsInitialAdminWithoutConfiguredCredentials() {
        stubExistingRoles();
        when(userRepository.existsByRoleId("id-Admin")).thenReturn(false);

        initializer("", "").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsInitialAdminIfUsernameIsTaken() {
        stubExistingRoles();
        when(userRepository.existsByRoleId("id-Admin")).thenReturn(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));

        initializer("admin", "initial-password").run(null);

        verify(userRepository, never()).save(any());
    }

    private static IndexInfo ttlIndex(Duration expireAfter) {
        return IndexInfo.indexInfoOf(new Document("name", DataInitializer.AUDIT_TTL_INDEX)
                .append("key", new Document("timestamp", 1))
                .append("expireAfterSeconds", expireAfter.toSeconds()));
    }

    private void stubExistingRoles() {
        for (String name : List.of("Admin", "Moderator", "User")) {
            Role role = new Role();
            role.setId("id-" + name);
            role.setName(name);
            when(roleRepository.findByName(name)).thenReturn(Optional.of(role));
        }
    }

    private DataInitializer initializer(String username, String password) {
        return new DataInitializer(mongoOperations, roleRepository, userRepository, passwordEncoder,
                new InitialAdminProperties(username, password), new AuditProperties(RETENTION, Duration.ofMinutes(15)));
    }
}
