package com.github.kevinldg.backend.gameserver.profile;

import java.util.List;

/**
 * Predefined container configuration for a game server. It pre-fills the creation form; every value can be changed
 * before the container is created.
 *
 * @param requiresEula   the game's EULA must be accepted explicitly (sets {@code EULA=TRUE})
 * @param memoryLimitMb  suggested container memory limit
 * @param notes          hints shown in the form, e.g. hardware requirements
 */
public record ContainerTemplate(
        String id,
        String name,
        String description,
        String image,
        List<Port> ports,
        List<Volume> volumes,
        List<EnvironmentVariable> environment,
        String restartPolicy,
        Integer memoryLimitMb,
        boolean requiresEula,
        List<String> notes
) {

    public record Port(int containerPort, String protocol, String description) {
    }

    /**
     * Persistent data; created as named volume {@code <container name>-<volumeSuffix>} by default.
     */
    public record Volume(String target, String volumeSuffix, String description) {
    }

    /**
     * @param options allowed values for a selection, empty for free text
     */
    public record EnvironmentVariable(String name, String defaultValue, String description, List<String> options) {

        public EnvironmentVariable(String name, String defaultValue, String description) {
            this(name, defaultValue, description, List.of());
        }
    }
}
