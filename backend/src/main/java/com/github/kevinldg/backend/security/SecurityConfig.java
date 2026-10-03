package com.github.kevinldg.backend.security;

import com.github.kevinldg.backend.auth.AuthService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.auth.AuthenticatedUserService;
import com.github.kevinldg.backend.maintenance.MaintenanceModeException;
import com.github.kevinldg.backend.maintenance.MaintenanceService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Session-based authentication for the SPA.
 * <ul>
 *     <li>{@code POST /api/auth/login} (form parameters {@code username}, {@code password}) returns the current user.</li>
 *     <li>{@code POST /api/auth/logout} returns 204.</li>
 *     <li>CSRF protection uses the {@code XSRF-TOKEN} cookie and {@code X-XSRF-TOKEN} header (axios default).</li>
 *     <li>All {@code /api/**} endpoints require authentication; errors are returned as ProblemDetail JSON.</li>
 *     <li>Permissions are checked per endpoint with {@code @PreAuthorize("hasAuthority('PERMISSION')")}.</li>
 *     <li>While maintenance mode is active, only administrators can log in and use the API
 *         (see {@link MaintenanceModeFilter}).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    static PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticatedUserService authenticatedUserService,
                                            AuthService authService,
                                            MaintenanceService maintenanceService,
                                            JsonMapper jsonMapper,
                                            @Qualifier("handlerExceptionResolver")
                                            HandlerExceptionResolver exceptionResolver) {
        http
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        // Async dispatches continue an already authorized request (e.g. live log streams)
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/maintenance").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        // SPA route; also disables Spring Security's generated login page
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(loginSuccessHandler(authService, maintenanceService, jsonMapper, exceptionResolver))
                        .failureHandler((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception))
                        .accessDeniedHandler((request, response, exception) ->
                                exceptionResolver.resolveException(request, response, null, exception)))
                .requestCache(RequestCacheConfigurer::disable)
                .addFilterAfter(new CurrentUserRefreshFilter(authenticatedUserService), SecurityContextHolderFilter.class)
                .addFilterAfter(new MaintenanceModeFilter(maintenanceService, exceptionResolver), CurrentUserRefreshFilter.class);

        return http.build();
    }

    private static AuthenticationSuccessHandler loginSuccessHandler(AuthService authService,
                                                                    MaintenanceService maintenanceService,
                                                                    JsonMapper jsonMapper,
                                                                    HandlerExceptionResolver exceptionResolver) {
        return (request, response, authentication) -> {
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

            // During maintenance, non-admins cannot log in: discard the new session and report maintenance.
            if (!maintenanceService.allows(user)) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                exceptionResolver.resolveException(request, response, null,
                        new MaintenanceModeException(maintenanceService.getStatus().message()));
                return;
            }

            authService.recordSuccessfulLogin(user.getId());

            // The CSRF token is replaced on login; reading it issues the new XSRF-TOKEN cookie.
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }

            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            jsonMapper.writeValue(response.getOutputStream(), authService.getCurrentUser(user.getId()));
        };
    }
}
