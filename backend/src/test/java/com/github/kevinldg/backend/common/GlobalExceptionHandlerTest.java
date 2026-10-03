package com.github.kevinldg.backend.common;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unexpectedErrorIncludesTechnicalDetailsForAdmins() {
        authenticateWith(AuthenticatedUser.ADMIN_AUTHORITY);

        ProblemDetail problem = handler.handleUnexpectedException(new IllegalStateException("Docker socket missing"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getProperties())
                .containsEntry("exception", IllegalStateException.class.getName())
                .containsEntry("exceptionMessage", "Docker socket missing");
    }

    @Test
    void unexpectedErrorHidesTechnicalDetailsFromOtherUsers() {
        authenticateWith("DASHBOARD_VIEW");

        ProblemDetail problem = handler.handleUnexpectedException(new IllegalStateException("Docker socket missing"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("Docker");
        assertThat(problem.getProperties()).isNullOrEmpty();
    }

    private void authenticateWith(String authority) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user", null, AuthorityUtils.createAuthorityList(authority)));
    }
}
