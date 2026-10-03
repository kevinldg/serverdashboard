package com.github.kevinldg.backend.gameserver.profile;

import org.springframework.stereotype.Component;

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
}
