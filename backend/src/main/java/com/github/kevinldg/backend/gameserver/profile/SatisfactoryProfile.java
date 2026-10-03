package com.github.kevinldg.backend.gameserver.profile;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SatisfactoryProfile implements GameServerProfile {

    @Override
    public String id() {
        return "satisfactory";
    }

    @Override
    public String displayName() {
        return "Satisfactory";
    }

    @Override
    public Set<String> imageNames() {
        return Set.of("wolveix/satisfactory-server");
    }

    /** Values verified against the wolveix/satisfactory-server documentation. */
    @Override
    public List<ContainerTemplate> templates() {
        return List.of(new ContainerTemplate(
                "satisfactory",
                "Satisfactory",
                "Satisfactory dedicated server; the game updates automatically on start.",
                "wolveix/satisfactory-server:latest",
                List.of(
                        new ContainerTemplate.Port(7777, "tcp", "Game port"),
                        new ContainerTemplate.Port(7777, "udp", "Game port"),
                        new ContainerTemplate.Port(8888, "tcp", "Messaging port")),
                List.of(new ContainerTemplate.Volume("/config", "config", "Game files and save games")),
                List.of(
                        new ContainerTemplate.EnvironmentVariable("MAXPLAYERS", "4", "Player limit"),
                        new ContainerTemplate.EnvironmentVariable("PUID", "1000", "User ID the server runs as"),
                        new ContainerTemplate.EnvironmentVariable("PGID", "1000", "Group ID the server runs as"),
                        new ContainerTemplate.EnvironmentVariable("STEAMBETA", "false", "Experimental game version",
                                List.of("false", "true"))),
                "unless-stopped",
                8192,
                false,
                List.of("Needs 8 GB of RAM or more; 8-16 GB recommended for the late game or many players.",
                        "The first start downloads the game (several GB) and takes a while.")));
    }
}
