package com.github.kevinldg.backend.docker;

import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.kevinldg.backend.common.ApiException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Translates Docker errors into {@link ApiException}s with user-friendly messages; the original error is attached
 * as cause (shown to administrators only).
 */
public final class DockerCalls {

    private DockerCalls() {
    }

    /**
     * @param notFoundMessage message if Docker reports 404 (e.g. "The container does not exist (anymore).")
     * @param conflictMessage message if Docker reports 409
     */
    public static <T> T call(Supplier<T> call, String notFoundMessage, String conflictMessage) {
        try {
            return call.get();
        } catch (ApiException e) {
            throw e;
        } catch (NotFoundException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage, e);
        } catch (ConflictException e) {
            throw new ApiException(HttpStatus.CONFLICT, conflictMessage, e);
        } catch (DockerException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The Docker operation failed.", e);
        } catch (RuntimeException e) {
            if (isConnectionFailure(e)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The Docker host is currently unavailable.", e);
            }
            throw e;
        }
    }

    /** Docker-java reports connection problems as runtime exceptions caused by an {@link IOException}. */
    public static boolean isConnectionFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
