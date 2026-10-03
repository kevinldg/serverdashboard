package com.github.kevinldg.backend.container.creation;

import com.github.kevinldg.backend.gameserver.GameServerService.TemplateInfo;

import java.util.List;

/**
 * Everything the creation form needs.
 *
 * @param bindMountRoot host directory for bind mounts, null if bind mounts are disabled
 * @param networks      selectable networks (the host network is never offered)
 */
public record CreationOptions(String bindMountRoot, List<NetworkOption> networks, List<TemplateInfo> templates) {

    public record NetworkOption(String name, String driver) {
    }
}
