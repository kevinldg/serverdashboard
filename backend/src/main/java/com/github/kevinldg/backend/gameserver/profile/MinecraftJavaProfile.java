package com.github.kevinldg.backend.gameserver.profile;

import org.springframework.stereotype.Component;

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
}
