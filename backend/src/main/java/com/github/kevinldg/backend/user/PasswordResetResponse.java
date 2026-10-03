package com.github.kevinldg.backend.user;

/**
 * @param generatedPassword the new password, shown exactly once
 */
public record PasswordResetResponse(String username, String generatedPassword) {
}
