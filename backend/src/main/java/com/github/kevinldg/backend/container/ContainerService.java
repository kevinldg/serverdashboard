package com.github.kevinldg.backend.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.Configuration;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.EnvironmentVariable;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.MountInfo;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.PortMapping;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.RestartPolicyInfo;
import com.github.kevinldg.backend.container.ContainerLogsResponse.LogLine;
import com.github.kevinldg.backend.container.ContainerOverviewResponse.ContainerSummary;
import com.github.kevinldg.backend.container.ContainerOverviewResponse.Statistics;
import com.github.kevinldg.backend.docker.DockerCalls;
import com.github.kevinldg.backend.docker.DockerProperties;
import com.github.kevinldg.backend.gameserver.ClassificationRequest;
import com.github.kevinldg.backend.gameserver.GameServerService.ContainerRef;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.GameServerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Access to Docker containers: overview, details, logs, and lifecycle actions.
 * <p>
 * Docker errors are translated into {@link ApiException}s with user-friendly messages;
 * the original error is attached as cause (shown to administrators only).
 * Actions are idempotent: e.g. starting a running container succeeds without doing anything.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerService {

    private static final Set<String> STOPPED_STATES = Set.of("created", "exited", "dead");

    /**
     * Label of the dashboard's own container. It cannot be stopped, restarted, or deleted through the application,
     * because the dashboard could not bring itself back.
     */
    public static final String SELF_LABEL = "serverdashboard.self";

    private final DockerClient dockerClient;
    private final DockerProperties dockerProperties;
    private final GameServerService gameServerService;

    public ContainerOverviewResponse getOverview() {
        List<Container> containers = callDocker(() -> dockerClient.listContainersCmd().withShowAll(true).exec());

        Map<String, GameServerStatus> gameServers = gameServerService.detectAll(containers.stream()
                .map(container -> new ContainerRef(nameOf(container), container.getImage(), container.getLabels()))
                .toList());

        List<ContainerSummary> summaries = containers.stream()
                .map(container -> toSummary(container, gameServers.get(nameOf(container))))
                .sorted(Comparator.comparing(ContainerSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int running = (int) summaries.stream().filter(c -> "running".equals(c.state())).count();
        int stopped = (int) summaries.stream().filter(c -> STOPPED_STATES.contains(c.state())).count();
        int gameServerCount = (int) summaries.stream().filter(c -> c.gameServer().gameServer()).count();

        return new ContainerOverviewResponse(new Statistics(summaries.size(), running, stopped, gameServerCount), summaries);
    }

    /**
     * @param includeEnvironment whether environment variables may be returned (they often contain secrets)
     */
    public ContainerDetailsResponse getDetails(String containerId, boolean includeEnvironment) {
        InspectContainerResponse container = callDocker(() -> dockerClient.inspectContainerCmd(containerId).exec());
        InspectContainerResponse.ContainerState state = container.getState();
        ContainerConfig config = container.getConfig();
        ContainerRef gameServerRef = new ContainerRef(stripLeadingSlash(container.getName()),
                config != null ? config.getImage() : null, config != null ? config.getLabels() : null);

        return new ContainerDetailsResponse(
                container.getId(),
                stripLeadingSlash(container.getName()),
                config != null ? config.getImage() : null,
                container.getImageId(),
                state != null ? state.getStatus() : null,
                state != null && state.getHealth() != null ? state.getHealth().getStatus() : null,
                parseTimestamp(container.getCreated()),
                state != null ? parseTimestamp(state.getStartedAt()) : null,
                state != null ? parseTimestamp(state.getFinishedAt()) : null,
                state != null ? state.getExitCode() : null,
                container.getRestartCount(),
                toMounts(container.getMounts()),
                new Configuration(
                        includeEnvironment ? toEnvironment(config) : null,
                        !includeEnvironment,
                        toRestartPolicy(container.getHostConfig()),
                        toPorts(container.getNetworkSettings()),
                        toNetworks(container.getNetworkSettings()),
                        config != null && config.getLabels() != null ? new TreeMap<>(config.getLabels()) : Map.of()
                ),
                gameServerService.detect(gameServerRef),
                gameServerService.detectAutomatically(gameServerRef),
                isSelf(config != null ? config.getLabels() : null)
        );
    }

    /**
     * Returns the last {@code tail} log lines of stdout and stderr.
     */
    public ContainerLogsResponse getLogs(String containerId, int tail) {
        return callDocker(() -> {
            LogCollector collector = new LogCollector();
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTimestamps(true)
                    .withTail(tail)
                    .exec(collector);
            awaitCompletion(collector);
            return new ContainerLogsResponse(collector.finish());
        });
    }

    public void start(String containerId, String actor) {
        boolean changed = runDockerAction(() -> dockerClient.startContainerCmd(containerId).exec());
        logAction(actor, "started", containerId, changed);
    }

    /**
     * Sends SIGTERM and gives the container {@code app.docker.stop-timeout} to shut down before Docker kills it.
     */
    public void stop(String containerId, String actor) {
        requireNotSelf(inspect(containerId), "stopped");
        boolean changed = runDockerAction(
                () -> dockerClient.stopContainerCmd(containerId).withTimeout(stopTimeoutSeconds()).exec());
        logAction(actor, "stopped", containerId, changed);
    }

    public void restart(String containerId, String actor) {
        requireNotSelf(inspect(containerId), "restarted");
        runDockerAction(() -> dockerClient.restartContainerCmd(containerId).withTimeout(stopTimeoutSeconds()).exec());
        logAction(actor, "restarted", containerId, true);
    }

    /**
     * Kills the container immediately (SIGKILL), without a clean shutdown.
     */
    public void forceStop(String containerId, String actor) {
        InspectContainerResponse container = inspect(containerId);
        requireNotSelf(container, "stopped");
        // Docker rejects killing a container that is not running; treat that as "already stopped".
        if (STOPPED_STATES.contains(container.getState() != null ? container.getState().getStatus() : null)) {
            logAction(actor, "force-stopped", containerId, false);
            return;
        }
        runDockerAction(() -> dockerClient.killContainerCmd(containerId).exec());
        logAction(actor, "force-stopped", containerId, true);
    }

    /**
     * Deletes a stopped container. Volumes are never removed, so persistent data is kept.
     */
    public void delete(String containerId, String actor) {
        InspectContainerResponse container = inspect(containerId);
        requireNotSelf(container, "deleted");
        if (!STOPPED_STATES.contains(container.getState() != null ? container.getState().getStatus() : null)) {
            throw new ApiException(HttpStatus.CONFLICT, "Stop the container before deleting it.");
        }
        runDockerAction(() -> dockerClient.removeContainerCmd(containerId)
                .withRemoveVolumes(false)
                .withForce(false)
                .exec());
        gameServerService.removeClassification(stripLeadingSlash(container.getName()));
        log.info("User '{}' deleted container '{}' (volumes kept)", actor, containerId);
    }

    /**
     * Sets or removes the manual game server classification. It is stored by container name, so it survives
     * recreating the container.
     */
    public void classify(String containerId, ClassificationRequest request, AuthenticatedUser actor) {
        gameServerService.classify(stripLeadingSlash(inspect(containerId).getName()), request, actor);
    }

    /** Current Docker state of the container, e.g. "running"; 404 if it does not exist. */
    String getState(String containerId) {
        InspectContainerResponse container = inspect(containerId);
        return container.getState() != null ? container.getState().getStatus() : null;
    }

    private static void requireNotSelf(InspectContainerResponse container, String action) {
        if (isSelf(container.getConfig() != null ? container.getConfig().getLabels() : null)) {
            throw new ApiException(HttpStatus.CONFLICT, "This is the dashboard's own container. It cannot be " + action
                    + " through the dashboard; use Docker on the server instead.");
        }
    }

    private static boolean isSelf(Map<String, String> labels) {
        return labels != null && "true".equalsIgnoreCase(labels.get(SELF_LABEL));
    }

    private InspectContainerResponse inspect(String containerId) {
        return callDocker(() -> dockerClient.inspectContainerCmd(containerId).exec());
    }

    private int stopTimeoutSeconds() {
        return (int) dockerProperties.stopTimeout().toSeconds();
    }

    /**
     * Runs a state-changing Docker command. "Not modified" (the container is already in the requested state)
     * counts as success.
     *
     * @return false if the container was already in the requested state
     */
    private boolean runDockerAction(Runnable action) {
        return callDocker(() -> {
            try {
                action.run();
                return true;
            } catch (NotModifiedException e) {
                return false;
            }
        });
    }

    private static void logAction(String actor, String action, String containerId, boolean changed) {
        if (changed) {
            log.info("User '{}' {} container '{}'", actor, action, containerId);
        } else {
            log.info("User '{}' requested: {} container '{}' (no change, already in that state)", actor, action, containerId);
        }
    }

    private void awaitCompletion(LogCollector collector) {
        try {
            long timeoutMillis = dockerProperties.responseTimeout().toMillis();
            if (!collector.awaitCompletion(timeoutMillis, TimeUnit.MILLISECONDS)) {
                collector.close();
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The Docker host did not respond in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Loading the logs was interrupted.", e);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The Docker host is currently unavailable.", e);
        }
    }

    private <T> T callDocker(Supplier<T> call) {
        return DockerCalls.call(call, "The container does not exist (anymore).",
                "The container's current state does not allow this action.");
    }

    private static String nameOf(Container container) {
        return container.getNames() != null && container.getNames().length > 0
                ? stripLeadingSlash(container.getNames()[0])
                : container.getId();
    }

    private static ContainerSummary toSummary(Container container, GameServerStatus gameServer) {
        Instant createdAt = container.getCreated() != null ? Instant.ofEpochSecond(container.getCreated()) : null;
        return new ContainerSummary(container.getId(), nameOf(container), container.getImage(), container.getState(),
                container.getStatus(), createdAt, gameServer, isSelf(container.getLabels()));
    }

    private static List<MountInfo> toMounts(List<InspectContainerResponse.Mount> mounts) {
        if (mounts == null) {
            return List.of();
        }
        return mounts.stream()
                .map(mount -> {
                    boolean isVolume = mount.getName() != null && !mount.getName().isBlank();
                    String type = isVolume ? "volume" : (mount.getSource() == null || mount.getSource().isBlank() ? "tmpfs" : "bind");
                    String destination = mount.getDestination() != null ? mount.getDestination().getPath() : null;
                    return new MountInfo(type, isVolume ? mount.getName() : null, mount.getSource(), destination,
                            !Boolean.TRUE.equals(mount.getRW()));
                })
                .toList();
    }

    private static List<EnvironmentVariable> toEnvironment(ContainerConfig config) {
        if (config == null || config.getEnv() == null) {
            return List.of();
        }
        return Arrays.stream(config.getEnv())
                .map(entry -> {
                    int separator = entry.indexOf('=');
                    return separator < 0
                            ? new EnvironmentVariable(entry, "")
                            : new EnvironmentVariable(entry.substring(0, separator), entry.substring(separator + 1));
                })
                .toList();
    }

    private static RestartPolicyInfo toRestartPolicy(HostConfig hostConfig) {
        RestartPolicy policy = hostConfig != null ? hostConfig.getRestartPolicy() : null;
        if (policy == null || policy.getName() == null || policy.getName().isBlank()) {
            return new RestartPolicyInfo("no", null);
        }
        return new RestartPolicyInfo(policy.getName(), policy.getMaximumRetryCount());
    }

    /**
     * Published ports, one entry per container port and host port (IPv4 and IPv6 bindings are merged).
     */
    private static List<PortMapping> toPorts(NetworkSettings networkSettings) {
        if (networkSettings == null || networkSettings.getPorts() == null) {
            return List.of();
        }
        Map<String, PortMapping> mappings = new LinkedHashMap<>();
        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : networkSettings.getPorts().getBindings().entrySet()) {
            ExposedPort exposedPort = entry.getKey();
            String protocol = exposedPort.getProtocol().toString().toLowerCase();
            if (entry.getValue() == null || entry.getValue().length == 0) {
                mappings.putIfAbsent(exposedPort.getPort() + "/" + protocol,
                        new PortMapping(exposedPort.getPort(), protocol, null, null));
                continue;
            }
            for (Ports.Binding binding : entry.getValue()) {
                mappings.putIfAbsent(exposedPort.getPort() + "/" + protocol + "/" + binding.getHostPortSpec(),
                        new PortMapping(exposedPort.getPort(), protocol, binding.getHostIp(), binding.getHostPortSpec()));
            }
        }
        return mappings.values().stream()
                .sorted(Comparator.comparingInt(PortMapping::containerPort).thenComparing(PortMapping::protocol))
                .toList();
    }

    private static List<String> toNetworks(NetworkSettings networkSettings) {
        if (networkSettings == null || networkSettings.getNetworks() == null) {
            return List.of();
        }
        return networkSettings.getNetworks().keySet().stream().sorted().toList();
    }

    private static String stripLeadingSlash(String name) {
        return name != null && name.startsWith("/") ? name.substring(1) : name;
    }

    /**
     * Parses Docker timestamps; Docker uses {@code 0001-01-01T00:00:00Z} for "never".
     */
    private static Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank() || timestamp.startsWith("0001-")) {
            return null;
        }
        return OffsetDateTime.parse(timestamp).toInstant();
    }

    /**
     * Collects all log lines of a (non-following) log request.
     */
    static class LogCollector extends ResultCallback.Adapter<Frame> {

        private final List<LogLine> lines = new ArrayList<>();
        private final LogLineSplitter splitter = new LogLineSplitter(lines::add);

        @Override
        public synchronized void onNext(Frame frame) {
            splitter.accept(frame);
        }

        /** Flushes incomplete last lines and returns all lines. */
        synchronized List<LogLine> finish() {
            splitter.flush();
            return List.copyOf(lines);
        }
    }
}
