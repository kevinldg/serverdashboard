package com.github.kevinldg.backend.gameserver;

import java.util.Set;

public record GameServerProfileInfo(String id, String name, Set<String> imageNames) {
}
