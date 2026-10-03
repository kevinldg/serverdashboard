package com.github.kevinldg.backend.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generatesPasswordsFromUnambiguousAlphabet() {
        String password = generator.generate();

        assertThat(password).hasSize(PasswordGenerator.LENGTH);
        assertThat(password.chars()).allMatch(c -> PasswordGenerator.ALPHABET.indexOf(c) >= 0);
        assertThat(PasswordGenerator.ALPHABET).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    void generatedPasswordsSatisfyPolicyAndDiffer() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String password = generator.generate();
            assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
            passwords.add(password);
        }
        assertThat(passwords).hasSize(100);
    }
}
