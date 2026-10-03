package com.github.kevinldg.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank
        @Size(min = UserManagementService.MIN_USERNAME_LENGTH, max = UserManagementService.MAX_USERNAME_LENGTH)
        @Pattern(regexp = UserManagementService.USERNAME_PATTERN, message = UserManagementService.USERNAME_MESSAGE)
        String username,

        @NotBlank
        String roleId,

        @NotNull
        Boolean active
) {
}
