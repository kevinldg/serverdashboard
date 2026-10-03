package com.github.kevinldg.backend.setup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the first admin user, created on startup if no admin exists yet.
 * Set via {@code INITIAL_ADMIN_USERNAME} and {@code INITIAL_ADMIN_PASSWORD}.
 */
@ConfigurationProperties("app.initial-admin")
public record InitialAdminProperties(String username, String password) {
}
