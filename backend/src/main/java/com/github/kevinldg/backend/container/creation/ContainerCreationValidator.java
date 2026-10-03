package com.github.kevinldg.backend.container.creation;

import com.github.kevinldg.backend.common.PosixPaths;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Network;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.EnvironmentVariable;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.MountSpec;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.MountType;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.PortSpec;
import com.github.kevinldg.backend.docker.DockerCalls;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.GameServerService.TemplateInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks a container creation request beyond the basic field validation, including the security rules:
 * <ul>
 *     <li>Bind mounts only below the configured root directory; the Docker socket can never be mounted.</li>
 *     <li>No host network or sharing another container's network namespace.</li>
 * </ul>
 * Also checks for conflicts with existing containers (name, published ports) so problems are reported before
 * anything is created. Ports of stopped containers are not published, so their conflicts only show up on start.
 */
@Component
@RequiredArgsConstructor
public class ContainerCreationValidator {

    private static final Pattern VOLUME_NAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]*");
    private static final Set<String> FORBIDDEN_NETWORKS = Set.of("host");

    private final DockerClient dockerClient;
    private final ContainerCreationProperties properties;
    private final GameServerService gameServerService;

    /**
     * @return the template the request is based on, if any
     * @throws ApiException 400 with field errors, or 409 if the name or a port is already in use
     */
    public Optional<TemplateInfo> validate(ContainerCreateRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        Optional<TemplateInfo> template = Optional.ofNullable(request.templateId()).flatMap(gameServerService::findTemplate);
        if (request.templateId() != null && template.isEmpty()) {
            errors.put("templateId", "Unknown template.");
        }

        validatePortsInRequest(request.ports(), errors);
        validateMounts(request.mounts(), errors);
        validateEnvironment(request.environment(), template, errors);
        validateRestartPolicy(request, errors);
        validateNetwork(request.network(), errors);

        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The container configuration is invalid.", errors);
        }

        checkConflictsWithExistingContainers(request);
        return template;
    }

    private static void validatePortsInRequest(List<PortSpec> ports, Map<String, String> errors) {
        Set<String> hostPorts = new HashSet<>();
        for (int i = 0; i < ports.size(); i++) {
            PortSpec port = ports.get(i);
            if (!hostPorts.add(port.hostPort() + "/" + port.protocol())) {
                errors.put("ports[" + i + "].hostPort", "Host port " + port.hostPort() + "/" + protocol(port)
                        + " is used more than once.");
            }
        }
    }

    private void validateMounts(List<MountSpec> mounts, Map<String, String> errors) {
        Set<String> targets = new HashSet<>();
        for (int i = 0; i < mounts.size(); i++) {
            MountSpec mount = mounts.get(i);
            String prefix = "mounts[" + i + "].";

            Optional<String> target = PosixPaths.normalizeAbsolute(mount.target());
            if (target.isEmpty() || target.get().equals("/")) {
                errors.put(prefix + "target", "Must be an absolute path inside the container, e.g. /data.");
            } else if (!targets.add(target.get())) {
                errors.put(prefix + "target", "This container path is used more than once.");
            }

            if (mount.type() == MountType.VOLUME) {
                if (!VOLUME_NAME.matcher(mount.source()).matches()) {
                    errors.put(prefix + "source", "Volume names may contain letters, digits, '.', '_' and '-'.");
                }
            } else {
                validateBindSource(mount.source(), prefix, errors);
            }
        }
    }

    private void validateBindSource(String source, String prefix, Map<String, String> errors) {
        if (!properties.bindMountsEnabled()) {
            errors.put(prefix + "type", "Bind mounts are disabled on this server. Use a volume instead.");
            return;
        }
        Optional<String> path = PosixPaths.normalizeAbsolute(source);
        String root = properties.bindMountRoot();
        if (path.isEmpty()) {
            errors.put(prefix + "source", "Must be an absolute host path without '..'.");
        } else if (path.get().toLowerCase(Locale.ROOT).contains("docker.sock")) {
            errors.put(prefix + "source", "The Docker socket cannot be mounted.");
        } else if (!PosixPaths.isBelow(path.get(), root)) {
            errors.put(prefix + "source", "Bind mounts are only allowed in a directory below " + root + ".");
        }
    }

    private static void validateEnvironment(List<EnvironmentVariable> environment, Optional<TemplateInfo> template,
                                            Map<String, String> errors) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < environment.size(); i++) {
            if (!names.add(environment.get(i).name())) {
                errors.put("environment[" + i + "].name", "This variable is defined more than once.");
            }
        }
        boolean eulaRequired = template.map(info -> info.template().requiresEula()).orElse(false);
        boolean eulaAccepted = environment.stream()
                .anyMatch(variable -> variable.name().equals("EULA") && variable.value().equalsIgnoreCase("TRUE"));
        if (eulaRequired && !eulaAccepted) {
            errors.put("eula", "The EULA must be accepted to create this server.");
        }
    }

    private static void validateRestartPolicy(ContainerCreateRequest request, Map<String, String> errors) {
        if (request.restartPolicy().maximumRetryCount() != null && !"on-failure".equals(request.restartPolicy().name())) {
            errors.put("restartPolicy.maximumRetryCount", "Only applies to the restart policy 'on-failure'.");
        }
    }

    private void validateNetwork(String network, Map<String, String> errors) {
        if (FORBIDDEN_NETWORKS.contains(network) || network.startsWith("container:")) {
            errors.put("network", "This network mode is not allowed.");
            return;
        }
        List<Network> networks = DockerCalls.call(() -> dockerClient.listNetworksCmd().exec(),
                "Networks could not be loaded.", "Networks could not be loaded.");
        if (networks.stream().noneMatch(existing -> network.equals(existing.getName()))) {
            errors.put("network", "The network does not exist.");
        }
    }

    private void checkConflictsWithExistingContainers(ContainerCreateRequest request) {
        List<Container> containers = DockerCalls.call(() -> dockerClient.listContainersCmd().withShowAll(true).exec(),
                "Containers could not be loaded.", "Containers could not be loaded.");
        Map<String, String> errors = new LinkedHashMap<>();

        boolean nameTaken = containers.stream()
                .flatMap(container -> container.getNames() == null ? Stream.empty() : Arrays.stream(container.getNames()))
                .anyMatch(name -> name.equals("/" + request.name()));
        if (nameTaken) {
            errors.put("name", "A container with this name already exists.");
        }

        Map<String, String> usedPorts = new HashMap<>();
        for (Container container : containers) {
            if (container.getPorts() == null) {
                continue;
            }
            for (ContainerPort port : container.getPorts()) {
                if (port.getPublicPort() != null) {
                    usedPorts.putIfAbsent(port.getPublicPort() + "/" + port.getType(),
                            container.getNames() != null && container.getNames().length > 0
                                    ? container.getNames()[0].substring(1) : container.getId());
                }
            }
        }
        for (int i = 0; i < request.ports().size(); i++) {
            PortSpec port = request.ports().get(i);
            String user = usedPorts.get(port.hostPort() + "/" + protocol(port));
            if (user != null) {
                errors.put("ports[" + i + "].hostPort", "Host port " + port.hostPort() + "/" + protocol(port)
                        + " is already used by container '" + user + "'.");
            }
        }

        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "The container conflicts with an existing container.", errors);
        }
    }

    private static String protocol(PortSpec port) {
        return port.protocol().name().toLowerCase(Locale.ROOT);
    }
}
