package com.github.kevinldg.backend.gameserver;

import jakarta.validation.constraints.NotNull;

/**
 * @param profileId  only for {@link Mode#GAME_SERVER}; null means generic game server
 * @param categoryId only for {@link Mode#CATEGORY}; required there
 */
public record ClassificationRequest(@NotNull Mode mode, String profileId, String categoryId) {

    public enum Mode {
        /** Remove the manual classification and use automatic detection. */
        AUTOMATIC,
        GAME_SERVER,
        /** A container category, e.g. "System"; not a game server. */
        CATEGORY,
        NOT_GAME_SERVER
    }
}
