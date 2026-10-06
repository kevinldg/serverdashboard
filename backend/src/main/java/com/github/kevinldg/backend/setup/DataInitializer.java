package com.github.kevinldg.backend.setup;

import com.github.kevinldg.backend.audit.AuditEntry;
import com.github.kevinldg.backend.audit.AuditProperties;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Prepares the database on startup: indexes (including the audit log's retention), the built-in roles, and the
 * initial admin user.
 * Existing data is never overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    static final String ADMIN_ROLE = "Admin";
    static final String MODERATOR_ROLE = "Moderator";
    static final String USER_ROLE = "User";

    static final String AUDIT_TTL_INDEX = "timestamp_ttl";

    static final Set<Permission> USER_PERMISSIONS = EnumSet.of(
            Permission.DASHBOARD_VIEW,
            Permission.CONTAINER_VIEW,
            Permission.CONTAINER_LOGS_VIEW);

    static final Set<Permission> MODERATOR_PERMISSIONS = EnumSet.of(
            Permission.DASHBOARD_VIEW,
            Permission.CONTAINER_VIEW,
            Permission.CONTAINER_LOGS_VIEW,
            Permission.CONTAINER_START,
            Permission.CONTAINER_STOP,
            Permission.CONTAINER_RESTART);

    private final MongoOperations mongoOperations;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InitialAdminProperties initialAdmin;
    private final AuditProperties auditProperties;

    @Override
    public void run(ApplicationArguments args) {
        createIndexes();
        Role adminRole = ensureRole(ADMIN_ROLE, true, EnumSet.noneOf(Permission.class));
        ensureRole(MODERATOR_ROLE, false, MODERATOR_PERMISSIONS);
        ensureRole(USER_ROLE, false, USER_PERMISSIONS);
        ensureInitialAdmin(adminRole);
    }

    private void createIndexes() {
        mongoOperations.indexOps(User.class).createIndex(new Index().on("username", Sort.Direction.ASC).unique());
        mongoOperations.indexOps(Role.class).createIndex(new Index().on("name", Sort.Direction.ASC).unique());
        createAuditRetentionIndex();
    }

    /**
     * MongoDB deletes audit log entries after the retention period. An index with a different (previously configured)
     * retention cannot be updated by creating it again, so it is replaced.
     */
    private void createAuditRetentionIndex() {
        IndexOperations indexOps = mongoOperations.indexOps(AuditEntry.class);
        Duration retention = auditProperties.retention();
        boolean outdated = indexOps.getIndexInfo().stream()
                .anyMatch(index -> AUDIT_TTL_INDEX.equals(index.getName())
                        && !index.getExpireAfter().equals(Optional.of(retention)));
        if (outdated) {
            indexOps.dropIndex(AUDIT_TTL_INDEX);
            log.info("Changing the audit log retention to {} days", retention.toDays());
        }
        indexOps.createIndex(new Index().on("timestamp", Sort.Direction.ASC).named(AUDIT_TTL_INDEX).expire(retention));
    }

    private Role ensureRole(String name, boolean superuser, Set<Permission> permissions) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setBuiltIn(true);
            role.setSuperuser(superuser);
            role.setPermissions(new HashSet<>(permissions));
            role.setCreatedAt(Instant.now());
            role.setUpdatedAt(role.getCreatedAt());
            log.info("Created built-in role '{}'", name);
            return roleRepository.save(role);
        });
    }

    private void ensureInitialAdmin(Role adminRole) {
        if (userRepository.existsByRoleId(adminRole.getId())) {
            return;
        }
        if (!StringUtils.hasText(initialAdmin.username()) || !StringUtils.hasText(initialAdmin.password())) {
            log.warn("No admin user exists. Set INITIAL_ADMIN_USERNAME and INITIAL_ADMIN_PASSWORD to create one.");
            return;
        }

        String username = User.normalizeUsername(initialAdmin.username());
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("No admin user exists, but user '{}' already exists without the admin role. "
                    + "Set INITIAL_ADMIN_USERNAME to a different username.", username);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(initialAdmin.password()));
        admin.setRoleId(adminRole.getId());
        admin.setActive(true);
        admin.setPasswordChangeRecommended(true);
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(admin.getCreatedAt());
        userRepository.save(admin);
        log.info("Created initial admin user '{}'", admin.getUsername());
    }
}
