package com.github.kevinldg.backend.role;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
@RequiredArgsConstructor
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    @GetMapping("/roles")
    public List<RoleResponse> listRoles() {
        return roleManagementService.listRoles();
    }

    /** All permissions with group and description, for the role editor. */
    @GetMapping("/permissions")
    public List<PermissionInfo> listPermissions() {
        return roleManagementService.listPermissions();
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody RoleRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleManagementService.createRole(request, actor));
    }

    @PutMapping("/roles/{id}")
    public RoleResponse updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return roleManagementService.updateRole(id, request, actor);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser actor) {
        roleManagementService.deleteRole(id, actor);
        return ResponseEntity.noContent().build();
    }
}
