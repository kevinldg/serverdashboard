package com.github.kevinldg.backend.gameserver;

import com.github.kevinldg.backend.category.CategoryInfo;

/**
 * How a container is classified (game server, category, or neither), and why.
 *
 * @param profileId   null for generic game servers and for containers that are no game server
 * @param profileName display name of the profile, "Game server" for generic ones, null if no game server
 * @param category    a manually assigned category (then never a game server), otherwise null
 */
public record GameServerStatus(boolean gameServer, String profileId, String profileName, Source source,
                               CategoryInfo category) {

    public GameServerStatus(boolean gameServer, String profileId, String profileName, Source source) {
        this(gameServer, profileId, profileName, source, null);
    }

    public enum Source {
        /** Classified manually in the application. */
        MANUAL,
        /** Docker label {@code serverdashboard.gameserver}. */
        LABEL,
        /** Image name matches a profile. */
        IMAGE,
        /** Nothing matched. */
        NONE
    }

    static final GameServerStatus NOT_DETECTED = new GameServerStatus(false, null, null, Source.NONE);
}
