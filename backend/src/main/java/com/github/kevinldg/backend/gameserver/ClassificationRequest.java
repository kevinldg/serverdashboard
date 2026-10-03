package com.github.kevinldg.backend.gameserver;

import jakarta.validation.constraints.NotNull;

/**
 * @param profileId only for {@link Mode#GAME_SERVER}; null means generic game server
 */
public record ClassificationRequest(@NotNull Mode mode, String profileId) {

    public enum Mode {
        /** Remove the manual classification and use automatic detection. */
        AUTOMATIC,
        GAME_SERVER,
        NOT_GAME_SERVER
    }
}
