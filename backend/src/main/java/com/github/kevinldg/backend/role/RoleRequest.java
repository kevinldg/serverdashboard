package com.github.kevinldg.backend.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(
        @NotBlank
        @Size(min = 2, max = 32)
        @Pattern(regexp = "[\\p{L}\\p{N} ._-]+", message = "may only contain letters, digits, spaces, dots, underscores and hyphens")
        String name,

        @NotNull
        Set<Permission> permissions
) {
}
