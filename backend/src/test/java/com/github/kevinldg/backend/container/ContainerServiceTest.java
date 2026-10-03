package com.github.kevinldg.backend.container;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.EnvironmentVariable;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.MountInfo;
import com.github.kevinldg.backend.container.ContainerDetailsResponse.PortMapping;
import com.github.kevinldg.backend.container.ContainerLogsResponse.LogLine;
import com.github.kevinldg.backend.docker.DockerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerServiceTest {

    /**
     * docker-java models are mapped with Jackson 2, so fixtures use the Docker API's JSON format.
     * Like docker-java itself, unknown fields (e.g. the mount "Type") are ignored.
     */
    private static final ObjectMapper DOCKER_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String CONTAINERS_JSON = """
            [
              {"Id": "1", "Names": ["/b-teamspeak"], "Image": "teamspeak", "State": "running",
               "Status": "Up 6 months", "Created": 1700000000},
              {"Id": "2", "Names": ["/A-minecraft"], "Image": "itzg/minecraft-server", "State": "exited",
               "Status": "Exited (0) 2 days ago", "Created": 1700000001},
              {"Id": "3", "Names": ["/c-paused"], "Image": "nginx", "State": "paused",
               "Status": "Up 1 hour (Paused)", "Created": 1700000002}
            ]
            """;

    private static final String INSPECT_JSON = """
            {
              "Id": "abc123",
              "Name": "/minecraft-server01",
              "Created": "2026-06-23T20:57:25.750440069Z",
              "Image": "sha256:image",
              "RestartCount": 2,
              "State": {"Status": "running", "Running": true, "ExitCode": 0,
                        "StartedAt": "2026-07-01T10:00:00.5Z", "FinishedAt": "0001-01-01T00:00:00Z",
                        "Health": {"Status": "healthy"}},
              "Config": {"Image": "itzg/minecraft-server:java25",
                         "Env": ["EULA=TRUE", "RCON_PASSWORD=secret=with=equals", "EMPTY="],
                         "Labels": {"b": "2", "a": "1"}},
              "HostConfig": {"RestartPolicy": {"Name": "unless-stopped", "MaximumRetryCount": 0}},
              "Mounts": [
                {"Type": "bind", "Source": "/home/kevin/docker/minecraft-server01", "Destination": "/data",
                 "Mode": "", "RW": true},
                {"Type": "volume", "Name": "mc-backups", "Source": "/var/lib/docker/volumes/mc-backups/_data",
                 "Destination": "/backups", "Driver": "local", "Mode": "z", "RW": false}
              ],
              "NetworkSettings": {
                "Ports": {"25565/tcp": [{"HostIp": "0.0.0.0", "HostPort": "25565"}, {"HostIp": "::", "HostPort": "25565"}],
                          "25575/tcp": null},
                "Networks": {"bridge": {}}
              }
            }
            """;

    private DockerClient dockerClient;
    private ContainerService containerService;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        containerService = new ContainerService(dockerClient,
                new DockerProperties("unix:///var/run/docker.sock", Duration.ofSeconds(5), Duration.ofSeconds(5),
                        Duration.ofSeconds(60)));
    }

    @Test
    void overviewIsSortedByNameAndCountsStates() throws Exception {
        List<Container> containers = DOCKER_JSON.readValue(CONTAINERS_JSON, new TypeReference<>() {
        });
        when(dockerClient.listContainersCmd().withShowAll(true).exec()).thenReturn(containers);

        ContainerOverviewResponse overview = containerService.getOverview();

        assertThat(overview.containers()).extracting(ContainerOverviewResponse.ContainerSummary::name)
                .containsExactly("A-minecraft", "b-teamspeak", "c-paused");
        assertThat(overview.containers().getFirst().createdAt()).isEqualTo(Instant.ofEpochSecond(1700000001));
        // Paused containers are neither running nor stopped.
        assertThat(overview.statistics()).isEqualTo(new ContainerOverviewResponse.Statistics(3, 1, 1));
    }

    @Test
    void detailsAreMappedFromInspectResponse() throws Exception {
        stubInspect();

        ContainerDetailsResponse details = containerService.getDetails("abc123", true);

        assertThat(details.name()).isEqualTo("minecraft-server01");
        assertThat(details.image()).isEqualTo("itzg/minecraft-server:java25");
        assertThat(details.state()).isEqualTo("running");
        assertThat(details.health()).isEqualTo("healthy");
        assertThat(details.createdAt()).isEqualTo(Instant.parse("2026-06-23T20:57:25.750440069Z"));
        assertThat(details.startedAt()).isEqualTo(Instant.parse("2026-07-01T10:00:00.5Z"));
        assertThat(details.finishedAt()).isNull();
        assertThat(details.restartCount()).isEqualTo(2);

        assertThat(details.mounts()).containsExactly(
                new MountInfo("bind", null, "/home/kevin/docker/minecraft-server01", "/data", false),
                new MountInfo("volume", "mc-backups", "/var/lib/docker/volumes/mc-backups/_data", "/backups", true));

        ContainerDetailsResponse.Configuration configuration = details.configuration();
        assertThat(configuration.environmentHidden()).isFalse();
        assertThat(configuration.environment()).containsExactly(
                new EnvironmentVariable("EULA", "TRUE"),
                new EnvironmentVariable("RCON_PASSWORD", "secret=with=equals"),
                new EnvironmentVariable("EMPTY", ""));
        assertThat(configuration.restartPolicy().name()).isEqualTo("unless-stopped");
        // IPv4 and IPv6 bindings of the same port are merged; unpublished ports are listed without host port.
        assertThat(configuration.ports()).containsExactly(
                new PortMapping(25565, "tcp", "0.0.0.0", "25565"),
                new PortMapping(25575, "tcp", null, null));
        assertThat(configuration.networks()).containsExactly("bridge");
        assertThat(configuration.labels()).containsExactly(entry("a", "1"), entry("b", "2"));
    }

    @Test
    void environmentIsWithheldWithoutPermission() throws Exception {
        stubInspect();

        ContainerDetailsResponse details = containerService.getDetails("abc123", false);

        assertThat(details.configuration().environment()).isNull();
        assertThat(details.configuration().environmentHidden()).isTrue();
    }

    @Test
    void missingContainerResultsInNotFound() {
        when(dockerClient.inspectContainerCmd("gone").exec()).thenThrow(new NotFoundException("No such container"));

        assertThatThrownBy(() -> containerService.getDetails("gone", false))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCause()).isInstanceOf(NotFoundException.class);
                });
    }

    @Test
    void unreachableDockerHostResultsInServiceUnavailable() {
        when(dockerClient.listContainersCmd().withShowAll(true).exec())
                .thenThrow(new RuntimeException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> containerService.getOverview())
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).isEqualTo("The Docker host is currently unavailable.");
                });
    }

    @Test
    void dockerErrorResultsInBadGateway() {
        when(dockerClient.listContainersCmd().withShowAll(true).exec())
                .thenThrow(new InternalServerErrorException("daemon error"));

        assertThatThrownBy(() -> containerService.getOverview())
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void logsAreSplitIntoLinesPerStream() {
        LogContainerCmd logCommand = mock(LogContainerCmd.class, RETURNS_SELF);
        when(dockerClient.logContainerCmd("abc123")).thenReturn(logCommand);
        when(logCommand.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
            // A line split across two frames, a multi-byte character split across frames, ANSI colors, stderr,
            // and a last line without newline.
            byte[] umlaut = "ü".getBytes(StandardCharsets.UTF_8);
            callback.onNext(frame(StreamType.STDOUT, "2026-10-03T16:00:00.000000001Z Server sta"));
            callback.onNext(frame(StreamType.STDOUT, "rted\n2026-10-03T16:00:01Z \u001B[32mDone\u001B[0m G"));
            callback.onNext(new Frame(StreamType.STDOUT, new byte[]{umlaut[0]}));
            callback.onNext(new Frame(StreamType.STDOUT, concat(new byte[]{umlaut[1]}, "r\n".getBytes(StandardCharsets.UTF_8))));
            callback.onNext(frame(StreamType.STDERR, "2026-10-03T16:00:02Z Warning\r\n"));
            callback.onNext(frame(StreamType.STDOUT, "2026-10-03T16:00:03Z incomplete"));
            callback.onComplete();
            return callback;
        });

        ContainerLogsResponse logs = containerService.getLogs("abc123", 100);

        verify(logCommand).withTail(100);
        assertThat(logs.lines()).containsExactly(
                new LogLine("2026-10-03T16:00:00.000000001Z", "stdout", "Server started"),
                new LogLine("2026-10-03T16:00:01Z", "stdout", "Done Gür"),
                new LogLine("2026-10-03T16:00:02Z", "stderr", "Warning"),
                new LogLine("2026-10-03T16:00:03Z", "stdout", "incomplete"));
    }

    @Test
    void logsOfMissingContainerResultInNotFound() {
        LogContainerCmd logCommand = mock(LogContainerCmd.class, RETURNS_SELF);
        when(dockerClient.logContainerCmd("gone")).thenReturn(logCommand);
        when(logCommand.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
            callback.onError(new NotFoundException("No such container"));
            return callback;
        });

        assertThatThrownBy(() -> containerService.getLogs("gone", 100))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void startStartsContainer() {
        containerService.start("abc123", "kevin");

        verify(dockerClient.startContainerCmd("abc123")).exec();
    }

    @Test
    void startingRunningContainerIsNoOp() {
        when(dockerClient.startContainerCmd("abc123").exec()).thenThrow(new NotModifiedException("already started"));

        assertThatCode(() -> containerService.start("abc123", "kevin")).doesNotThrowAnyException();
    }

    @Test
    void stopAndRestartUseConfiguredStopTimeout() {
        containerService.stop("abc123", "kevin");
        containerService.restart("abc123", "kevin");

        verify(dockerClient.stopContainerCmd("abc123")).withTimeout(60);
        verify(dockerClient.restartContainerCmd("abc123")).withTimeout(60);
    }

    @Test
    void stoppingStoppedContainerIsNoOp() {
        when(dockerClient.stopContainerCmd("abc123").withTimeout(60).exec())
                .thenThrow(new NotModifiedException("already stopped"));

        assertThatCode(() -> containerService.stop("abc123", "kevin")).doesNotThrowAnyException();
    }

    @Test
    void forceStopKillsRunningContainer() throws Exception {
        stubState("abc123", "running");

        containerService.forceStop("abc123", "kevin");

        verify(dockerClient.killContainerCmd("abc123")).exec();
    }

    @Test
    void forceStoppingStoppedContainerIsNoOp() throws Exception {
        stubState("abc123", "exited");

        containerService.forceStop("abc123", "kevin");

        verify(dockerClient, never()).killContainerCmd(anyString());
    }

    @Test
    void deleteRemovesStoppedContainerButKeepsVolumes() throws Exception {
        stubState("abc123", "exited");
        RemoveContainerCmd removeCommand = mock(RemoveContainerCmd.class, RETURNS_SELF);
        when(dockerClient.removeContainerCmd("abc123")).thenReturn(removeCommand);

        containerService.delete("abc123", "kevin");

        verify(removeCommand).withRemoveVolumes(false);
        verify(removeCommand).withForce(false);
        verify(removeCommand).exec();
    }

    @Test
    void deletingRunningContainerIsRejected() throws Exception {
        stubState("abc123", "running");

        assertThatThrownBy(() -> containerService.delete("abc123", "kevin"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("Stop the container before deleting it.");
                });
        verify(dockerClient, never()).removeContainerCmd(anyString());
    }

    @Test
    void dockerConflictResultsInConflict() {
        when(dockerClient.startContainerCmd("abc123").exec()).thenThrow(new ConflictException("conflict"));

        assertThatThrownBy(() -> containerService.start("abc123", "kevin"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private void stubState(String containerId, String state) throws IOException {
        InspectContainerResponse response = DOCKER_JSON.readValue("""
                {"Id": "%s", "State": {"Status": "%s"}}
                """.formatted(containerId, state), InspectContainerResponse.class);
        when(dockerClient.inspectContainerCmd(containerId).exec()).thenReturn(response);
    }

    private void stubInspect() throws IOException {
        InspectContainerResponse response = DOCKER_JSON.readValue(INSPECT_JSON, InspectContainerResponse.class);
        when(dockerClient.inspectContainerCmd("abc123").exec()).thenReturn(response);
    }

    private static Frame frame(StreamType streamType, String payload) {
        return new Frame(streamType, payload.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
