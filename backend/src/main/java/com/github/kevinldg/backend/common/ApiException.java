package com.github.kevinldg.backend.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Expected business error. The message is safe to show to any user.
 * <p>
 * An optional cause (e.g. a Docker error) is logged and shown to administrators only.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
