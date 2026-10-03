package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.common.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

/**
 * Rules for passwords chosen by users or administrators (generated passwords always satisfy them).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    /** BCrypt only uses the first 72 bytes of a password and rejects longer input. */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The password is too long.");
        }
    }
}
