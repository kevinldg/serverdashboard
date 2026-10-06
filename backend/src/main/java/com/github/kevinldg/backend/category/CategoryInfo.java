package com.github.kevinldg.backend.category;

/**
 * A category as shown on containers and offered in the classification dialog.
 */
public record CategoryInfo(String id, String name, CategoryColor color) {

    public static CategoryInfo of(ContainerCategory category) {
        return new CategoryInfo(category.getId(), category.getName(), category.getColor());
    }
}
