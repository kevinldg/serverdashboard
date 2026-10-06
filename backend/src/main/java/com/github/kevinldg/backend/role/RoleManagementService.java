package com.github.kevinldg.backend.role;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role and permission management for administrators.
 * <p>
 * Rules:
 * <ul>
 *     <li>The Admin role (superuser) cannot be changed or deleted.</li>
 *     <li>Built-in roles cannot be renamed (they are recognized by name on startup) or deleted.</li>
 *     <li>Custom roles can only be deleted while no user has them.</li>
 *     <li>Non-admins (with {@code ROLE_MANAGE}) cannot grant permissions they do not have themselves.</li>
 * </ul>
 * Permission changes apply to affected users with their next request.
 */
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::isSuperuser).reversed()
                        .thenComparing(Role::isBuiltIn, Comparator.reverseOrder())
                        .thenComparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    public List<PermissionInfo> listPermissions() {
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionInfo(permission, permission.getGroup(), permission.getDescription()))
                .toList();
    }

    public RoleResponse createRole(RoleRequest request, AuthenticatedUser actor) {
        String name = request.name().trim();
        requireNameAvailable(name);
        requireMayGrant(EnumSet.noneOf(Permission.class), request.permissions(), actor);

        Role role = new Role();
        role.setName(name);
        role.setPermissions(new HashSet<>(request.permissions()));
        role.setBuiltIn(false);
        role.setSuperuser(false);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(role.getCreatedAt());
        Role saved = save(role);

        auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.ROLE_CREATE, name,
                        "Created role '" + name + "'")
                .detail("permissions", format(sorted(request.permissions()))));
        return toResponse(saved);
    }

    public RoleResponse updateRole(String roleId, RoleRequest request, AuthenticatedUser actor) {
        Role role = findRole(roleId);
        if (role.isSuperuser()) {
            throw new ApiException(HttpStatus.CONFLICT, "The Admin role always has full access and cannot be changed.");
        }

        String name = request.name().trim();
        if (!name.equals(role.getName())) {
            if (role.isBuiltIn()) {
                throw new ApiException(HttpStatus.CONFLICT, "Built-in roles cannot be renamed.");
            }
            if (!name.equalsIgnoreCase(role.getName())) {
                requireNameAvailable(name);
            }
        }
        requireMayGrant(role.getPermissions(), request.permissions(), actor);

        Set<Permission> previous = sorted(role.getPermissions());
        String previousName = role.getName();
        role.setName(name);
        role.setPermissions(new HashSet<>(request.permissions()));
        role.setUpdatedAt(Instant.now());
        Role saved = save(role);

        Set<Permission> added = sorted(request.permissions());
        added.removeAll(previous);
        Set<Permission> removed = sorted(previous);
        removed.removeAll(request.permissions());
        auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.ROLE_UPDATE, name,
                        "Updated role '" + name + "'")
                .detail("name", previousName.equals(name) ? null : previousName + " → " + name)
                .detail("added", added.isEmpty() ? null : format(added))
                .detail("removed", removed.isEmpty() ? null : format(removed)));
        return toResponse(saved);
    }

    public void deleteRole(String roleId, AuthenticatedUser actor) {
        Role role = findRole(roleId);
        if (role.isBuiltIn()) {
            throw new ApiException(HttpStatus.CONFLICT, "Built-in roles cannot be deleted.");
        }
        long userCount = userRepository.countByRoleId(roleId);
        if (userCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "The role is still assigned to " + userCount
                    + (userCount == 1 ? " user" : " users") + ". Assign them a different role first.");
        }

        roleRepository.delete(role);
        auditService.record(AuditEvent.success(actor.getUsername(), AuditAction.ROLE_DELETE, role.getName(),
                "Deleted role '" + role.getName() + "'"));
    }

    /**
     * Non-admins may only add permissions they have themselves; removing permissions is always allowed.
     */
    private static void requireMayGrant(Set<Permission> current, Set<Permission> requested, AuthenticatedUser actor) {
        if (actor.isAdmin()) {
            return;
        }
        Set<Permission> added = EnumSet.noneOf(Permission.class);
        added.addAll(requested);
        added.removeAll(current);
        added.removeAll(actor.getPermissions());
        if (!added.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "You cannot grant permissions you do not have yourself: " + sorted(added));
        }
    }

    private void requireNameAvailable(String name) {
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A role with this name already exists.");
        }
    }

    /** The unique index on the name also catches concurrent requests. */
    private Role save(Role role) {
        try {
            return roleRepository.save(role);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "A role with this name already exists.");
        }
    }

    private Role findRole(String roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The role does not exist (anymore)."));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.isBuiltIn(), role.isSuperuser(),
                role.effectivePermissions(), userRepository.countByRoleId(role.getId()));
    }

    /** A modifiable, ordered copy. */
    private static Set<Permission> sorted(Set<Permission> permissions) {
        return permissions == null ? EnumSet.noneOf(Permission.class) : permissions.stream()
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

    private static String format(Set<Permission> permissions) {
        return permissions.isEmpty() ? "none" : permissions.stream().map(Permission::name).collect(Collectors.joining(", "));
    }
}
