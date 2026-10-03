package com.github.kevinldg.backend.gameserver.profile;

import java.util.Set;

/**
 * A known game server type.
 * <p>
 * Profiles detect their game servers by image name. Later phases extend them with templates and
 * configuration files. To support a new game, add a new {@code @Component} implementing this interface.
 */
public interface GameServerProfile {

    /** Stable identifier, also used as value of the {@code serverdashboard.gameserver} label. */
    String id();

    String displayName();

    /**
     * Image names (without registry, tag, or digest, lower case) that identify this game server,
     * e.g. {@code itzg/minecraft-server}.
     */
    Set<String> imageNames();
}
