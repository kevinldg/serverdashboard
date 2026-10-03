package com.github.kevinldg.backend.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Expected business error. The message is safe to show to any user.
 * <p>
 * An optional cause (e.g. a Docker error) is logged and shown to administrators only.
 * Optional field errors are returned like validation errors ({@code errors}: field → message).
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.of();
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.fieldErrors = Map.of();
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }
}
