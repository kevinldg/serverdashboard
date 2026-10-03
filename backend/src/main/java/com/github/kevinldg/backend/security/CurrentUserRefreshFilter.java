package com.github.kevinldg.backend.security;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.auth.AuthenticatedUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Reloads the logged-in user from the database on every request.
 * <p>
 * Deactivated or deleted users lose their session immediately, as do sessions created before an
 * administrator reset the user's password. Role or permission changes apply to the next request.
 * The refreshed principal is only used for the current request; the session keeps the user ID it was created with.
 * <p>
 * Not a Spring bean on purpose: it must only run inside the security filter chain.
 */
@RequiredArgsConstructor
class CurrentUserRefreshFilter extends OncePerRequestFilter {

    private final AuthenticatedUserService authenticatedUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            Optional<AuthenticatedUser> currentUser = authenticatedUserService.loadActiveUser(principal.getId())
                    .filter(user -> user.getSessionVersion() == principal.getSessionVersion());

            if (currentUser.isPresent()) {
                AuthenticatedUser user = currentUser.get();
                user.eraseCredentials();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
                SecurityContextHolder.setContext(context);
            } else {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
        }

        chain.doFilter(request, response);
    }
}
