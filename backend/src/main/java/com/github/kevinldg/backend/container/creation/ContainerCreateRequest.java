package com.github.kevinldg.backend.container.creation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new container. Covers a practical subset of Docker options; new options are added as new fields.
 * <p>
 * Deliberately not supported (security): privileged mode, capabilities, host network, devices, PID/IPC namespaces.
 *
 * @param templateId    the template the form was based on (optional); adds the game server label and requires EULA
 *                      acceptance where applicable
 * @param memoryLimitMb optional memory limit in MB
 * @param start         start the container after creating it
 */
public record ContainerCreateRequest(
        @NotBlank
        @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_.-]{0,62}",
                message = "1-63 characters: letters, digits, '.', '_' and '-', starting with a letter or digit")
        String name,

        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "[a-z0-9][a-z0-9._/-]*(:[A-Za-z0-9_][A-Za-z0-9._-]{0,127})?",
                message = "must be an image name with optional tag, e.g. nginx:1.27 (digests are not supported)")
        String image,

        @NotNull @Size(max = 50) @Valid
        List<PortSpec> ports,

        @NotNull @Size(max = 20) @Valid
        List<MountSpec> mounts,

        @NotNull @Size(max = 100) @Valid
        List<EnvironmentVariable> environment,

        @NotNull @Valid
        RestartSpec restartPolicy,

        @NotBlank
        String network,

        @Min(64) @Max(1_048_576)
        Integer memoryLimitMb,

        String templateId,

        boolean start
) {

    public record PortSpec(
            @NotNull @Min(1) @Max(65535) Integer hostPort,
            @NotNull @Min(1) @Max(65535) Integer containerPort,
            @NotNull Protocol protocol
    ) {
    }

    public enum Protocol {
        TCP, UDP
    }

    /**
     * @param source volume name (type VOLUME) or absolute host path below the configured bind mount root (type BIND)
     * @param target absolute path inside the container
     */
    public record MountSpec(
            @NotNull MountType type,
            @NotBlank @Size(max = 4096) String source,
            @NotBlank @Size(max = 4096) String target,
            boolean readOnly
    ) {
    }

    public enum MountType {
        VOLUME, BIND
    }

    public record EnvironmentVariable(
            @NotBlank
            @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]*", message = "must start with a letter or '_' and contain only letters, digits and '_'")
            @Size(max = 255)
            String name,

            @NotNull @Size(max = 4096)
            String value
    ) {
    }

    /**
     * @param maximumRetryCount only for {@code on-failure}; 0 means unlimited
     */
    public record RestartSpec(
            @NotNull
            @Pattern(regexp = "no|always|unless-stopped|on-failure")
            String name,

            @Min(0) @Max(100)
            Integer maximumRetryCount
    ) {
    }
}
