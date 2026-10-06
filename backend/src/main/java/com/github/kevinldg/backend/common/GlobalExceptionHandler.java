package com.github.kevinldg.backend.common;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.maintenance.MaintenanceModeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts exceptions into RFC 9457 {@link ProblemDetail} responses.
 * <p>
 * Security errors raised in the filter chain are routed here as well (see
 * {@link com.github.kevinldg.backend.security.SecurityConfig}), so all API errors share one format.
 * Technical details (unexpected errors, causes of {@link ApiException}s) are only included for administrators.
 * Requests rejected for missing permissions (403) are recorded in the audit log.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final AuditService auditService;

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus() == HttpStatus.FORBIDDEN) {
            recordAccessDenied(request, ex.getMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        if (!ex.getFieldErrors().isEmpty()) {
            problem.setProperty("errors", new LinkedHashMap<>(ex.getFieldErrors()));
        }
        if (ex.getCause() != null) {
            log.warn("{}: {}", ex.getMessage(), ex.getCause().toString());
            addTechnicalDetailsForAdmins(problem, ex.getCause());
        }
        return problem;
    }

    @ExceptionHandler(MaintenanceModeException.class)
    public ProblemDetail handleMaintenanceMode(MaintenanceModeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setProperty("maintenance", true);
        problem.setProperty("maintenanceMessage", ex.getMaintenanceMessage());
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        // Deactivated users get the same message as wrong credentials.
        boolean loginFailed = ex instanceof BadCredentialsException || ex instanceof AccountStatusException;
        String detail = loginFailed ? "Invalid username or password." : "Authentication required.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
    }

    @ExceptionHandler(CsrfException.class)
    public ProblemDetail handleCsrfException(CsrfException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Invalid or missing CSRF token. Please reload the page and try again.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        recordAccessDenied(request, null);
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        addTechnicalDetailsForAdmins(problem, ex);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail("The request contains invalid fields.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Deduplicated, so a page that keeps requesting something the user may not see does not flood the audit log.
     * CSRF failures are handled separately and not recorded.
     */
    private void recordAccessDenied(HttpServletRequest request, String reason) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.getUsername() : "(anonymous)";
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        auditService.recordDeduplicated(AuditEvent.denied(actor, AuditAction.ACCESS_DENIED, endpoint,
                "Access denied: " + endpoint).detail("reason", reason));
    }

    private void addTechnicalDetailsForAdmins(ProblemDetail problem, Throwable cause) {
        if (isAdmin()) {
            problem.setProperty("exception", cause.getClass().getName());
            problem.setProperty("exceptionMessage", cause.getMessage());
        }
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> AuthenticatedUser.ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
