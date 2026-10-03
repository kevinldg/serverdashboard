package com.github.kevinldg.backend.support;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sends a valid CSRF token the way the frontend does: {@code XSRF-TOKEN} cookie plus {@code X-XSRF-TOKEN} header.
 * <p>
 * Spring Security's {@code csrf()} post-processor is deliberately not used: it replaces the CSRF token
 * repository in the shared application context, which breaks cookie-based CSRF tests.
 */
public final class CsrfSupport {

    private CsrfSupport() {
    }

    public static RequestPostProcessor xsrf(MockMvc mockMvc) throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return request -> {
            request.setCookies(csrfCookie);
            request.addHeader("X-XSRF-TOKEN", csrfCookie.getValue());
            return request;
        };
    }
}
