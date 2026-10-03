package com.github.kevinldg.backend.gameserver;

/**
 * Whether a container is a game server, and why.
 *
 * @param profileId   null for generic game servers and for containers that are no game server
 * @param profileName display name of the profile, "Game server" for generic ones, null if no game server
 */
public record GameServerStatus(boolean gameServer, String profileId, String profileName, Source source) {

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
