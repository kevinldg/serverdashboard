package com.github.kevinldg.backend.gameserver.profile;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MinecraftJavaProfile implements GameServerProfile {

    @Override
    public String id() {
        return "minecraft-java";
    }

    @Override
    public String displayName() {
        return "Minecraft (Java Edition)";
    }

    @Override
    public Set<String> imageNames() {
        return Set.of("itzg/minecraft-server");
    }

    /** Values verified against the itzg/minecraft-server documentation. */
    @Override
    public List<ContainerTemplate> templates() {
        return List.of(new ContainerTemplate(
                "minecraft-java",
                "Minecraft (Java Edition)",
                "Vanilla, Paper, Fabric, and more, installed and updated automatically on start.",
                "itzg/minecraft-server:latest",
                List.of(new ContainerTemplate.Port(25565, "tcp", "Game port")),
                List.of(new ContainerTemplate.Volume("/data", "data", "World, configuration, and mods")),
                List.of(
                        new ContainerTemplate.EnvironmentVariable("TYPE", "VANILLA", "Server type",
                                List.of("VANILLA", "PAPER", "FABRIC", "FORGE", "NEOFORGE", "PURPUR", "SPIGOT")),
                        new ContainerTemplate.EnvironmentVariable("VERSION", "LATEST",
                                "Minecraft version, e.g. 1.21.4, or LATEST"),
                        new ContainerTemplate.EnvironmentVariable("MEMORY", "2G",
                                "Java heap size. Keep the container memory limit about 1 GB higher.")),
                "unless-stopped",
                3072,
                true,
                List.of("The container memory limit must be higher than MEMORY (Java needs memory beyond the heap).")));
    }
}
