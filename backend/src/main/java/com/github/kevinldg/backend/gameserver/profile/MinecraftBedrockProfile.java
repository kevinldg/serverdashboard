package com.github.kevinldg.backend.gameserver.profile;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MinecraftBedrockProfile implements GameServerProfile {

    @Override
    public String id() {
        return "minecraft-bedrock";
    }

    @Override
    public String displayName() {
        return "Minecraft (Bedrock Edition)";
    }

    @Override
    public Set<String> imageNames() {
        return Set.of("itzg/minecraft-bedrock-server");
    }

    @Override
    public List<String> configRoots() {
        return List.of("/data");
    }

    @Override
    public List<String> knownConfigFiles() {
        return List.of("/data/server.properties", "/data/allowlist.json", "/data/permissions.json");
    }

    /** Values verified against the itzg/minecraft-bedrock-server documentation. */
    @Override
    public List<ContainerTemplate> templates() {
        return List.of(new ContainerTemplate(
                "minecraft-bedrock",
                "Minecraft (Bedrock Edition)",
                "Bedrock dedicated server for consoles, mobile, and Windows clients.",
                "itzg/minecraft-bedrock-server:latest",
                List.of(new ContainerTemplate.Port(19132, "udp", "Game port")),
                List.of(new ContainerTemplate.Volume("/data", "data", "Worlds and configuration")),
                List.of(new ContainerTemplate.EnvironmentVariable("VERSION", "LATEST",
                        "Bedrock server version, or LATEST")),
                "unless-stopped",
                2048,
                true,
                List.of()));
    }
}
