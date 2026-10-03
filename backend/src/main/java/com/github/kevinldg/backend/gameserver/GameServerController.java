package com.github.kevinldg.backend.gameserver;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameServerController {

    private final GameServerService gameServerService;

    /** Available game server profiles, for the classification dialog. */
    @GetMapping("/api/game-server-profiles")
    @PreAuthorize("hasAuthority('GAMESERVER_MANAGE')")
    public List<GameServerProfileInfo> listProfiles() {
        return gameServerService.listProfiles();
    }
}
