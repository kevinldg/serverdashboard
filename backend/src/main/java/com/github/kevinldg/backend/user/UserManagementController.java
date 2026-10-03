package com.github.kevinldg.backend.user;

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
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    public List<UserResponse> listUsers() {
        return userManagementService.listUsers();
    }

    /** Roles for the role selection when creating or editing users. */
    @GetMapping("/role-options")
    public List<RoleOption> listRoleOptions() {
        return userManagementService.listRoleOptions();
    }

    @PostMapping
    public ResponseEntity<CreatedUserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request, actor));
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return userManagementService.updateUser(id, request, actor);
    }

    @PostMapping("/{id}/reset-password")
    public PasswordResetResponse resetPassword(@PathVariable String id,
                                               @AuthenticationPrincipal AuthenticatedUser actor) {
        return userManagementService.resetPassword(id, actor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser actor) {
        userManagementService.deleteUser(id, actor);
        return ResponseEntity.noContent().build();
    }
}
