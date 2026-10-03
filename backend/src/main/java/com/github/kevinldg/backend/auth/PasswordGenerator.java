package com.github.kevinldg.backend.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random passwords for new users and password resets.
 * The alphabet omits look-alike characters (0/O, 1/l/I) so passwords can be read and typed reliably.
 */
@Component
public class PasswordGenerator {

    static final int LENGTH = 20;
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
