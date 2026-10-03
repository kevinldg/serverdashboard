package com.github.kevinldg.backend.security;

import com.github.kevinldg.backend.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Rejects login attempts with 429 while {@link LoginThrottle} blocks the username or client address, before the
 * password is checked. Not a Spring bean on purpose: it must only run inside the security filter chain.
 */
@RequiredArgsConstructor
class LoginThrottleFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";

    private final LoginThrottle throttle;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !LOGIN_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> blocked = throttle.blockedFor(request.getParameter("username"), request.getRemoteAddr());
        if (blocked.isPresent()) {
            long minutes = Math.max(1, (blocked.get().toSeconds() + 59) / 60);
            response.setHeader("Retry-After", String.valueOf(blocked.get().toSeconds()));
            exceptionResolver.resolveException(request, response, null, new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.")));
            return;
        }
        chain.doFilter(request, response);
    }
}
