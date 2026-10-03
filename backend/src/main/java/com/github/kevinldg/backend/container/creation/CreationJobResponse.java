package com.github.kevinldg.backend.container.creation;

/**
 * Current state of a container creation job.
 *
 * @param progress       image download progress in percent, null if unknown or not downloading
 * @param containerId    set as soon as the container exists
 * @param error          user-friendly error message, if the job failed
 * @param technicalError Docker's error message; only included for administrators
 */
public record CreationJobResponse(
        String id,
        CreationJobStatus status,
        String message,
        Integer progress,
        String containerName,
        String containerId,
        String error,
        String technicalError
) {
}
