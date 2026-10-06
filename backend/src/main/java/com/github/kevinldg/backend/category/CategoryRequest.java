package com.github.kevinldg.backend.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank
        @Size(max = CategoryService.MAX_NAME_LENGTH)
        String name,

        @NotNull
        CategoryColor color
) {
}
