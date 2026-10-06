package com.github.kevinldg.backend.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.auth.AuthenticatedUserService;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.maintenance.MaintenanceService;
import com.github.kevinldg.backend.container.ContainerLogsResponse.LogLine;
import com.github.kevinldg.backend.role.Permission;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live container logs via Server-Sent Events.
 * <p>
 * Events: {@code ready} (stream started), {@code line} (a {@link LogLine}), and {@code end} with a reason:
 * {@code container-stopped}, {@code max-duration}, {@code access-revoked}, {@code maintenance}, {@code error},
 * {@code server-shutdown}.
 * Only new lines are sent; earlier lines come from {@link ContainerService#getLogs}.
 * <p>
 * Every heartbeat re-checks the user (active, session still valid, {@code CONTAINER_LOGS_VIEW}) and maintenance
 * mode, so revoked access ends the stream within one heartbeat interval.
 * Opening a stream is recorded in the audit log (logs may contain sensitive information).
 */
@Slf4j
@Service
public class ContainerLogStreamService {

    private final DockerClient dockerClient;
    private final ContainerService containerService;
    private final AuthenticatedUserService authenticatedUserService;
    private final LogStreamProperties properties;
    private final MaintenanceService maintenanceService;
    private final AuditService auditService;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final Map<LogStream, Boolean> streams = new ConcurrentHashMap<>();

    public ContainerLogStreamService(DockerClient dockerClient, ContainerService containerService,
                                     AuthenticatedUserService authenticatedUserService, LogStreamProperties properties,
                                     MaintenanceService maintenanceService, AuditService auditService) {
        this.dockerClient = dockerClient;
        this.containerService = containerService;
        this.authenticatedUserService = authenticatedUserService;
        this.properties = properties;
        this.maintenanceService = maintenanceService;
        this.auditService = auditService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("log-stream-scheduler").factory());
    }

    public SseEmitter openStream(String containerId, AuthenticatedUser user) {
        if (activeStreams.incrementAndGet() > properties.maxConcurrent()) {
            activeStreams.decrementAndGet();
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many live log streams are open. Please try again later.");
        }
        String containerName;
        try {
            // Fails with a regular API error (e.g. 404) before the stream starts
            containerName = containerService.getName(containerId);
        } catch (RuntimeException e) {
            activeStreams.decrementAndGet();
            throw e;
        }

        // Safety net only: streams normally end via the max-duration task below.
        SseEmitter emitter = createEmitter(properties.maxDuration().plusMinutes(1).toMillis());
        LogStream stream = new LogStream(containerId, user, emitter);
        streams.put(stream, Boolean.TRUE);
        stream.start();
        auditService.recordDeduplicated(AuditEvent.success(user.getUsername(), AuditAction.CONTAINER_LIVE_LOGS,
                containerName, "Opened the live logs of container '" + containerName + "'"));
        return stream.emitter;
    }

    /** Overridden in tests to observe the sent events. */
    SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    @PreDestroy
    void shutdown() {
        streams.keySet().forEach(stream -> stream.end("server-shutdown"));
        scheduler.shutdownNow();
    }

    int activeStreamCount() {
        return activeStreams.get();
    }

    private boolean stillAllowed(AuthenticatedUser user) {
        return authenticatedUserService.loadActiveUser(user.getId())
                .filter(current -> current.getSessionVersion() == user.getSessionVersion())
                .filter(current -> current.getPermissions().contains(Permission.CONTAINER_LOGS_VIEW))
                .isPresent();
    }

    private class LogStream extends ResultCallback.Adapter<Frame> {

        private final String containerId;
        private final AuthenticatedUser user;
        private final SseEmitter emitter;
        private final LogLineSplitter splitter;
        private final AtomicBoolean ending = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private ScheduledFuture<?> heartbeat;
        private ScheduledFuture<?> maxDuration;

        LogStream(String containerId, AuthenticatedUser user, SseEmitter emitter) {
            this.containerId = containerId;
            this.user = user;
            this.emitter = emitter;
            this.splitter = new LogLineSplitter(
                    line -> send(SseEmitter.event().name("line").data(line, MediaType.APPLICATION_JSON)));
        }

        void start() {
            emitter.onCompletion(this::cleanUp);
            emitter.onTimeout(this::cleanUp);
            emitter.onError(error -> cleanUp());

            // Sends the response headers immediately, so the browser knows the stream is open.
            send(SseEmitter.event().name("ready").data("ready"));

            long interval = properties.heartbeatInterval().toMillis();
            heartbeat = scheduler.scheduleAtFixedRate(this::heartbeat, interval, interval, TimeUnit.MILLISECONDS);
            maxDuration = scheduler.schedule(() -> end("max-duration"), properties.maxDuration().toMillis(),
                    TimeUnit.MILLISECONDS);

            try {
                // tail=0 + follow: only lines written from now on
                dockerClient.logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTimestamps(true)
                        .withFollowStream(true)
                        .withTail(0)
                        .exec(this);
            } catch (RuntimeException e) {
                log.warn("Starting live logs of container '{}' failed: {}", containerId, e.toString());
                end("error");
                return;
            }
            log.debug("User '{}' opened live logs of container '{}'", user.getUsername(), containerId);
        }

        @Override
        public void onNext(Frame frame) {
            synchronized (splitter) {
                splitter.accept(frame);
            }
        }

        /** Docker ends a following log stream when the container stops. */
        @Override
        public void onComplete() {
            synchronized (splitter) {
                splitter.flush();
            }
            end("container-stopped");
        }

        @Override
        public void onError(Throwable throwable) {
            if (!closed.get()) {
                log.warn("Live logs of container '{}' failed: {}", containerId, throwable.toString());
            }
            end("error");
        }

        private void heartbeat() {
            if (!stillAllowed(user)) {
                end("access-revoked");
                return;
            }
            if (!maintenanceService.allows(user)) {
                end("maintenance");
                return;
            }
            send(SseEmitter.event().comment("heartbeat"));
        }

        void end(String reason) {
            if (closed.get() || !ending.compareAndSet(false, true)) {
                return;
            }
            send(SseEmitter.event().name("end").data(Map.of("reason", reason), MediaType.APPLICATION_JSON));
            emitter.complete();
            cleanUp();
        }

        private void send(SseEmitter.SseEventBuilder event) {
            if (closed.get()) {
                return;
            }
            try {
                synchronized (emitter) {
                    emitter.send(event);
                }
            } catch (IOException | IllegalStateException e) {
                // The browser closed the connection
                cleanUp();
            }
        }

        /** Runs once, however the stream ends. */
        private void cleanUp() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            if (maxDuration != null) {
                maxDuration.cancel(false);
            }
            try {
                close();
            } catch (IOException e) {
                log.debug("Closing the Docker log stream failed", e);
            }
            streams.remove(this);
            activeStreams.decrementAndGet();
            log.debug("Live logs of container '{}' closed for user '{}'", containerId, user.getUsername());
        }
    }
}
