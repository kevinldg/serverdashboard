package com.github.kevinldg.backend.gameserver.profile;

import org.springframework.stereotype.Component;

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
}
