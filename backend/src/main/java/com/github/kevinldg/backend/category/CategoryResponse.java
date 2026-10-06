package com.github.kevinldg.backend.category;

/**
 * @param containerCount number of containers the category is assigned to
 */
public record CategoryResponse(String id, String name, CategoryColor color, long containerCount) {
}
