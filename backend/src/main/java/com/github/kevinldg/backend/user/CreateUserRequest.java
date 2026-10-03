package com.github.kevinldg.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param password optional; if blank, a random password is generated and returned once
 */
public record CreateUserRequest(
        @NotBlank
        @Size(min = UserManagementService.MIN_USERNAME_LENGTH, max = UserManagementService.MAX_USERNAME_LENGTH)
        @Pattern(regexp = UserManagementService.USERNAME_PATTERN, message = UserManagementService.USERNAME_MESSAGE)
        String username,

        @NotBlank
        String roleId,

        String password
) {
}
