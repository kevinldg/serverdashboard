package com.github.kevinldg.backend.gameserver.profile;

import java.util.List;
import java.util.Set;

/**
 * A known game server type.
 * <p>
 * Profiles detect their game servers by image name and provide container templates. A later phase extends them
 * with configuration files. To support a new game, add a new {@code @Component} implementing this interface.
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

    /** Templates for creating containers of this game; IDs must be unique across all profiles. */
    default List<ContainerTemplate> templates() {
        return List.of();
    }
}
