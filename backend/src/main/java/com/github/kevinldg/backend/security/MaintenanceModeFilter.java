package com.github.kevinldg.backend.security;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.maintenance.MaintenanceModeException;
import com.github.kevinldg.backend.maintenance.MaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;

/**
 * Rejects API requests of non-admins with 503 while maintenance mode is active.
 * <p>
 * Exempt: the public maintenance status, CSRF token, login (non-admin logins are rejected in the login success
 * handler), and logout. Not a Spring bean on purpose: it must only run inside the security filter chain.
 */
@RequiredArgsConstructor
class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/maintenance", "/api/auth/csrf", "/api/auth/login", "/api/auth/logout");

    private final MaintenanceService maintenanceService;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/") && !EXEMPT_PATHS.contains(path) && !maintenanceService.allows(currentUser())) {
            exceptionResolver.resolveException(request, response, null,
                    new MaintenanceModeException(maintenanceService.getStatus().message()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }
}
