package com.github.kevinldg.backend.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.auth.AuthenticatedUserService;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.maintenance.MaintenanceService;
import com.github.kevinldg.backend.role.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerLogStreamServiceTest {

    private final DockerClient dockerClient = mock(DockerClient.class);
    private final ContainerService containerService = mock(ContainerService.class);
    private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
    private final LogContainerCmd logCommand = mock(LogContainerCmd.class, RETURNS_SELF);
    private final MaintenanceService maintenanceService = mock(MaintenanceService.class);

    private final AuthenticatedUser user = new AuthenticatedUser("user-1", "alice", null, true, false, 0,
            Set.of(Permission.CONTAINER_LOGS_VIEW));

    /**
     * Events sent to the browser, as SSE text. Data objects appear via toString() here (JSON serialization
     * happens in Spring's message converters, which this test emitter bypasses).
     */
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private volatile ResultCallback.Adapter<Frame> dockerCallback;
    private ContainerLogStreamService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void streamsNewLinesUntilContainerStops() throws Exception {
        createService(Duration.ofMinutes(30), Duration.ofSeconds(15), 20);

        service.openStream("abc", user);
        verify(logCommand).withFollowStream(true);
        verify(logCommand).withTail(0);
        assertThat(service.activeStreamCount()).isEqualTo(1);

        dockerCallback.onNext(new Frame(StreamType.STDOUT, "2026-10-03T16:00:00Z Player joined\n".getBytes(StandardCharsets.UTF_8)));
        dockerCallback.onNext(new Frame(StreamType.STDERR, "2026-10-03T16:00:01Z Warn".getBytes(StandardCharsets.UTF_8)));
        dockerCallback.onComplete();

        assertThat(sent.getFirst()).contains("event:ready");
        assertThat(sent).anySatisfy(event -> assertThat(event).contains("event:line")
                .contains("timestamp=2026-10-03T16:00:00Z, stream=stdout, message=Player joined"));
        // The incomplete last line is flushed when the container stops
        assertThat(sent).anySatisfy(event -> assertThat(event).contains("stream=stderr, message=Warn"));
        assertThat(sent.getLast()).contains("event:end").contains("container-stopped");
        assertThat(service.activeStreamCount()).isZero();
    }

    @Test
    void numberOfStreamsIsLimited() {
        createService(Duration.ofMinutes(30), Duration.ofSeconds(15), 1);
        service.openStream("abc", user);

        assertThatThrownBy(() -> service.openStream("abc", user))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(service.activeStreamCount()).isEqualTo(1);
    }

    @Test
    void missingContainerFailsBeforeStreaming() {
        createService(Duration.ofMinutes(30), Duration.ofSeconds(15), 20);
        when(containerService.getState("gone")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "gone"));

        assertThatThrownBy(() -> service.openStream("gone", user))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.activeStreamCount()).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    void revokedAccessEndsStreamAtNextHeartbeat() throws Exception {
        createService(Duration.ofMinutes(30), Duration.ofMillis(50), 20);
        when(authenticatedUserService.loadActiveUser("user-1")).thenReturn(Optional.of(user));

        service.openStream("abc", user);
        waitUntil(() -> sent.stream().anyMatch(event -> event.contains(":heartbeat")));

        // The user loses CONTAINER_LOGS_VIEW
        when(authenticatedUserService.loadActiveUser("user-1")).thenReturn(Optional.of(
                new AuthenticatedUser("user-1", "alice", null, true, false, 0, Set.of(Permission.DASHBOARD_VIEW))));
        waitUntil(() -> service.activeStreamCount() == 0);

        assertThat(sent.getLast()).contains("event:end").contains("access-revoked");
    }

    @Test
    void passwordResetEndsStream() throws Exception {
        createService(Duration.ofMinutes(30), Duration.ofMillis(50), 20);
        when(authenticatedUserService.loadActiveUser("user-1")).thenReturn(Optional.of(
                new AuthenticatedUser("user-1", "alice", null, true, false, 1, Set.of(Permission.CONTAINER_LOGS_VIEW))));

        service.openStream("abc", user);
        waitUntil(() -> service.activeStreamCount() == 0);

        assertThat(sent.getLast()).contains("access-revoked");
    }

    @Test
    void maintenanceModeEndsStreamOfNonAdmins() throws Exception {
        createService(Duration.ofMinutes(30), Duration.ofMillis(50), 20);
        when(authenticatedUserService.loadActiveUser("user-1")).thenReturn(Optional.of(user));

        service.openStream("abc", user);
        when(maintenanceService.allows(any())).thenReturn(false);
        waitUntil(() -> service.activeStreamCount() == 0);

        assertThat(sent.getLast()).contains("event:end").contains("maintenance");
    }

    @Test
    void streamEndsAfterMaximumDuration() throws Exception {
        createService(Duration.ofMillis(100), Duration.ofSeconds(15), 20);

        service.openStream("abc", user);
        waitUntil(() -> service.activeStreamCount() == 0);

        assertThat(sent.getLast()).contains("event:end").contains("max-duration");
    }

    private void createService(Duration maxDuration, Duration heartbeatInterval, int maxConcurrent) {
        when(maintenanceService.allows(any())).thenReturn(true);
        when(dockerClient.logContainerCmd(any())).thenReturn(logCommand);
        when(logCommand.exec(any())).thenAnswer(invocation -> {
            dockerCallback = invocation.getArgument(0);
            return dockerCallback;
        });
        service = new ContainerLogStreamService(dockerClient, containerService, authenticatedUserService,
                new LogStreamProperties(maxDuration, heartbeatInterval, maxConcurrent), maintenanceService) {
            @Override
            SseEmitter createEmitter(long timeoutMillis) {
                return new SseEmitter(timeoutMillis) {
                    @Override
                    public void send(SseEventBuilder builder) {
                        sent.add(builder.build().stream()
                                .map(part -> String.valueOf(part.getData()))
                                .collect(Collectors.joining()));
                    }
                };
            }
        };
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 5 seconds");
            }
            Thread.sleep(10);
        }
    }
}
