package com.github.kevinldg.backend.container.creation;

public enum CreationJobStatus {
    PULLING_IMAGE,
    CREATING,
    STARTING,
    /** The container was created (and started, if requested). */
    COMPLETED,
    /** The container was created but could not be started; it is kept so its configuration and logs can be checked. */
    START_FAILED,
    /** Nothing was created. */
    FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == START_FAILED || this == FAILED;
    }
}
