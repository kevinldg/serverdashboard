package com.github.kevinldg.backend.container;

import com.github.kevinldg.backend.gameserver.GameServerStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @param health             health check status ({@code healthy}, {@code unhealthy}, {@code starting}),
 *                           or null without health check
 * @param startedAt          null if the container has never been started
 * @param gameServer         effective game server status (including a manual classification)
 * @param detectedGameServer what automatic detection results in, ignoring a manual classification
 */
public record ContainerDetailsResponse(
        String id,
        String name,
        String image,
        String imageId,
        String state,
        String health,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer exitCode,
        Integer restartCount,
        List<MountInfo> mounts,
        Configuration configuration,
        GameServerStatus gameServer,
        GameServerStatus detectedGameServer
) {

    /**
     * @param type   {@code volume} or {@code bind}
     * @param name   volume name, null for bind mounts
     * @param source host path
     */
    public record MountInfo(String type, String name, String source, String destination, boolean readOnly) {
    }

    /**
     * @param environment       null if the user lacks the permission to see environment variables
     * @param environmentHidden true if environment variables were withheld
     */
    public record Configuration(
            List<EnvironmentVariable> environment,
            boolean environmentHidden,
            RestartPolicyInfo restartPolicy,
            List<PortMapping> ports,
            List<String> networks,
            Map<String, String> labels
    ) {
    }

    public record EnvironmentVariable(String name, String value) {
    }

    /**
     * @param name Docker restart policy: {@code no}, {@code always}, {@code unless-stopped} or {@code on-failure}
     */
    public record RestartPolicyInfo(String name, Integer maximumRetryCount) {
    }

    /**
     * @param hostIp   null if the port is not published
     * @param hostPort null if the port is not published
     */
    public record PortMapping(int containerPort, String protocol, String hostIp, String hostPort) {
    }
}
