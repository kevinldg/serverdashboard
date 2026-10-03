package com.github.kevinldg.backend.user;

/**
 * @param generatedPassword the generated password (shown exactly once), or null if the password was set manually
 */
public record CreatedUserResponse(UserResponse user, String generatedPassword) {
}
