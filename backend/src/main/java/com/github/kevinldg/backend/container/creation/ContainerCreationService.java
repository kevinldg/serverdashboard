package com.github.kevinldg.backend.container.creation;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditEvent;
import com.github.kevinldg.backend.audit.AuditService;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.PortSpec;
import com.github.kevinldg.backend.docker.DockerCalls;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.GameServerService.TemplateInfo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates containers as background jobs: download the image if needed, create the container, start it if requested.
 * <p>
 * Jobs are kept in memory (single backend instance) and removed one hour after they finished.
 * Progress is available via {@link #getJob} and as Server-Sent Events ({@link #subscribe}, event {@code update}).
 * The result of each job (created, created but not started, failed) is recorded in the audit log.
 */
@Slf4j
@Service
public class ContainerCreationService {

    /** Label set on every container created by the application. */
    public static final String CREATED_BY_LABEL = "serverdashboard.created-by";

    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration JOB_RETENTION = Duration.ofHours(1);
    private static final Pattern MISSING_BIND_SOURCE = Pattern.compile("bind source path does not exist: ([^\\\"]+)");

    private final DockerClient dockerClient;
    private final ContainerCreationValidator validator;
    private final ContainerCreationProperties properties;
    private final GameServerService gameServerService;
    private final AuditService auditService;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public ContainerCreationService(DockerClient dockerClient, ContainerCreationValidator validator,
                                    ContainerCreationProperties properties, GameServerService gameServerService,
                                    AuditService auditService, Clock clock) {
        this.dockerClient = dockerClient;
        this.validator = validator;
        this.properties = properties;
        this.gameServerService = gameServerService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Everything the creation form needs. */
    public CreationOptions getOptions() {
        List<Network> networks = DockerCalls.call(() -> dockerClient.listNetworksCmd().exec(),
                "Networks could not be loaded.", "Networks could not be loaded.");
        return new CreationOptions(
                properties.bindMountRoot(),
                networks.stream()
                        .filter(network -> !"host".equals(network.getName()))
                        .map(network -> new CreationOptions.NetworkOption(network.getName(), network.getDriver()))
                        .sorted((a, b) -> a.name().equals("bridge") ? -1 : b.name().equals("bridge") ? 1
                                : a.name().compareToIgnoreCase(b.name()))
                        .toList(),
                gameServerService.listTemplates());
    }

    /**
     * Validates the request (synchronously, so configuration errors are reported immediately) and starts the job.
     */
    public CreationJobResponse create(ContainerCreateRequest request, AuthenticatedUser actor) {
        Optional<TemplateInfo> template = validator.validate(request);
        removeOldJobs();

        Job job = new Job(UUID.randomUUID().toString(), actor.getId(), request.name());
        jobs.put(job.id, job);
        log.info("User '{}' started creating container '{}' from image '{}'{}", actor.getUsername(), request.name(),
                request.image(), template.map(info -> " (template " + info.template().id() + ")").orElse(""));

        executor.submit(() -> run(job, request, template, actor));
        return job.toResponse(actor.isAdmin());
    }

    public CreationJobResponse getJob(String jobId, AuthenticatedUser viewer) {
        return findJob(jobId, viewer).toResponse(viewer.isAdmin());
    }

    /** Sends the current state and every change as {@code update} events until the job has finished. */
    public SseEmitter subscribe(String jobId, AuthenticatedUser viewer) {
        Job job = findJob(jobId, viewer);
        SseEmitter emitter = new SseEmitter(PULL_TIMEOUT.plusMinutes(5).toMillis());
        job.subscribe(emitter, viewer.isAdmin());
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void run(Job job, ContainerCreateRequest request, Optional<TemplateInfo> template, AuthenticatedUser actor) {
        try {
            if (!imageExists(request.image())) {
                pullImage(request.image(), job);
            }

            job.update(CreationJobStatus.CREATING, "Creating the container…", null);
            CreateContainerResponse created = createContainer(request, template, actor);
            job.setContainerId(created.getId());

            if (!request.start()) {
                job.update(CreationJobStatus.COMPLETED, "The container was created.", null);
                auditService.record(withRequestDetails(AuditEvent.success(actor.getUsername(),
                        AuditAction.CONTAINER_CREATE, request.name(), "Created container '" + request.name() + "'"),
                        request, template));
                return;
            }

            job.update(CreationJobStatus.STARTING, "Starting the container…", null);
            try {
                dockerClient.startContainerCmd(created.getId()).exec();
            } catch (RuntimeException e) {
                String message = startFailureMessage(e);
                job.fail(CreationJobStatus.START_FAILED, message, e);
                log.warn("Container '{}' was created but could not be started: {}", request.name(), e.toString());
                auditService.record(withRequestDetails(AuditEvent.failure(actor.getUsername(),
                        AuditAction.CONTAINER_CREATE, request.name(),
                        "Created container '" + request.name() + "', but it could not be started", message),
                        request, template));
                return;
            }
            job.update(CreationJobStatus.COMPLETED, "The container was created and started.", null);
            auditService.record(withRequestDetails(AuditEvent.success(actor.getUsername(), AuditAction.CONTAINER_CREATE,
                    request.name(), "Created and started container '" + request.name() + "'"), request, template));
        } catch (PullFailedException e) {
            failJob(job, e.getMessage(), e.getCause(), request, template, actor);
        } catch (ConflictException e) {
            failJob(job, "A container with this name already exists.", e, request, template, actor);
        } catch (RuntimeException e) {
            log.warn("Creating container '{}' failed: {}", request.name(), e.toString());
            failJob(job, createFailureMessage(e), e, request, template, actor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failJob(job, "Creating the container was interrupted.", e, request, template, actor);
        }
    }

    private void failJob(Job job, String message, Throwable cause, ContainerCreateRequest request,
                         Optional<TemplateInfo> template, AuthenticatedUser actor) {
        job.fail(CreationJobStatus.FAILED, message, cause);
        auditService.record(withRequestDetails(AuditEvent.failure(actor.getUsername(), AuditAction.CONTAINER_CREATE,
                request.name(), "Could not create container '" + request.name() + "'", message), request, template));
    }

    private static AuditEvent withRequestDetails(AuditEvent event, ContainerCreateRequest request,
                                                 Optional<TemplateInfo> template) {
        return event.detail("image", request.image())
                .detail("template", template.map(info -> info.template().id()).orElse(null));
    }

    private boolean imageExists(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private void pullImage(String image, Job job) throws InterruptedException {
        int tagSeparator = image.lastIndexOf(':');
        boolean hasTag = tagSeparator > image.lastIndexOf('/');
        String repository = hasTag ? image.substring(0, tagSeparator) : image;
        String tag = hasTag ? image.substring(tagSeparator + 1) : "latest";

        job.update(CreationJobStatus.PULLING_IMAGE, "Downloading image " + repository + ":" + tag + "…", 0);
        PullProgress progress = new PullProgress(job, clock);
        try {
            boolean finished = dockerClient.pullImageCmd(repository).withTag(tag).exec(progress)
                    .awaitCompletion(PULL_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                progress.close();
                throw new PullFailedException("Downloading the image took too long.", null);
            }
        } catch (NotFoundException e) {
            throw new PullFailedException("The image " + repository + ":" + tag + " was not found.", e);
        } catch (PullFailedException e) {
            throw e;
        } catch (IOException e) {
            throw new PullFailedException("Downloading the image failed.", e);
        } catch (RuntimeException e) {
            if (DockerCalls.isConnectionFailure(e)) {
                throw e;
            }
            throw new PullFailedException("Downloading the image failed. Check the image name and tag.", e);
        }
    }

    private CreateContainerResponse createContainer(ContainerCreateRequest request, Optional<TemplateInfo> template,
                                                    AuthenticatedUser actor) {
        Set<ExposedPort> exposedPorts = new LinkedHashSet<>();
        Ports portBindings = new Ports();
        for (PortSpec port : request.ports()) {
            ExposedPort exposed = port.protocol() == ContainerCreateRequest.Protocol.UDP
                    ? ExposedPort.udp(port.containerPort())
                    : ExposedPort.tcp(port.containerPort());
            exposedPorts.add(exposed);
            portBindings.bind(exposed, Ports.Binding.bindPort(port.hostPort()));
        }

        List<Mount> mounts = request.mounts().stream()
                .map(mount -> new Mount()
                        .withType(mount.type() == ContainerCreateRequest.MountType.BIND ? MountType.BIND : MountType.VOLUME)
                        .withSource(mount.source())
                        .withTarget(mount.target())
                        .withReadOnly(mount.readOnly()))
                .toList();

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(portBindings)
                .withMounts(mounts)
                .withRestartPolicy(restartPolicy(request.restartPolicy()))
                .withNetworkMode(request.network());
        if (request.memoryLimitMb() != null) {
            hostConfig.withMemory(request.memoryLimitMb() * 1024L * 1024L);
        }

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(CREATED_BY_LABEL, actor.getUsername());
        template.ifPresent(info -> labels.put(GameServerService.LABEL, info.profileId()));

        return dockerClient.createContainerCmd(request.image())
                .withName(request.name())
                .withEnv(request.environment().stream().map(variable -> variable.name() + "=" + variable.value()).toList())
                .withExposedPorts(new ArrayList<>(exposedPorts))
                .withLabels(labels)
                .withHostConfig(hostConfig)
                .exec();
    }

    private static RestartPolicy restartPolicy(ContainerCreateRequest.RestartSpec spec) {
        return switch (spec.name()) {
            case "always" -> RestartPolicy.alwaysRestart();
            case "unless-stopped" -> RestartPolicy.unlessStoppedRestart();
            case "on-failure" -> RestartPolicy.onFailureRestart(spec.maximumRetryCount() == null ? 0 : spec.maximumRetryCount());
            default -> RestartPolicy.noRestart();
        };
    }

    /** Docker's errors are technical; common causes get a clear message. */
    private static String createFailureMessage(RuntimeException e) {
        if (DockerCalls.isConnectionFailure(e)) {
            return "The Docker host is currently unavailable.";
        }
        Matcher missingDirectory = MISSING_BIND_SOURCE.matcher(String.valueOf(e.getMessage()));
        if (missingDirectory.find()) {
            return "The host directory " + missingDirectory.group(1) + " does not exist. Create it on the server first.";
        }
        return "The container could not be created.";
    }

    /** Docker's start errors are technical; the most common cause gets a clear message. */
    private static String startFailureMessage(RuntimeException e) {
        String detail = String.valueOf(e.getMessage()).toLowerCase();
        if (detail.contains("port is already allocated") || detail.contains("address already in use")) {
            return "The container was created but could not be started: a host port is already in use "
                    + "(possibly by a stopped container or another program).";
        }
        return "The container was created but could not be started. Check its configuration and logs.";
    }

    private Job findJob(String jobId, AuthenticatedUser viewer) {
        Job job = jobs.get(jobId);
        // Jobs of other users are reported as not found (administrators can see all jobs).
        if (job == null || (!job.ownerId.equals(viewer.getId()) && !viewer.isAdmin())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "The creation job does not exist (anymore).");
        }
        return job;
    }

    private void removeOldJobs() {
        Instant cutoff = clock.instant().minus(JOB_RETENTION);
        jobs.values().removeIf(job -> job.finishedBefore(cutoff));
    }

    private static class PullFailedException extends RuntimeException {
        PullFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Aggregates the download progress of all image layers. */
    private static class PullProgress extends ResultCallback.Adapter<PullResponseItem> {

        private static final Duration UPDATE_INTERVAL = Duration.ofMillis(250);

        private final Job job;
        private final Clock clock;
        private final Map<String, long[]> layers = new ConcurrentHashMap<>();
        private Instant lastUpdate = Instant.EPOCH;

        PullProgress(Job job, Clock clock) {
            this.job = job;
            this.clock = clock;
        }

        @Override
        public void onNext(PullResponseItem item) {
            if (item.getErrorDetail() != null || item.getError() != null) {
                onError(new PullFailedException("Downloading the image failed.",
                        new IllegalStateException(item.getError())));
                return;
            }
            if (item.getId() == null || item.getStatus() == null) {
                return;
            }
            String status = item.getStatus();
            if (status.startsWith("Downloading") && item.getProgressDetail() != null
                    && item.getProgressDetail().getTotal() != null && item.getProgressDetail().getTotal() > 0) {
                layers.put(item.getId(), new long[]{item.getProgressDetail().getCurrent(), item.getProgressDetail().getTotal()});
            } else if (status.equals("Download complete") || status.equals("Pull complete")) {
                layers.computeIfPresent(item.getId(), (id, layer) -> new long[]{layer[1], layer[1]});
            }
            publish();
        }

        private synchronized void publish() {
            Instant now = clock.instant();
            if (Duration.between(lastUpdate, now).compareTo(UPDATE_INTERVAL) < 0) {
                return;
            }
            lastUpdate = now;
            long current = layers.values().stream().mapToLong(layer -> layer[0]).sum();
            long total = layers.values().stream().mapToLong(layer -> layer[1]).sum();
            if (total > 0) {
                job.updateProgress((int) Math.min(100, current * 100 / total));
            }
        }
    }

    /** A creation job and the browsers following it. */
    private class Job {

        private final String id;
        private final String ownerId;
        private final String containerName;
        private final Map<SseEmitter, Boolean> subscribers = new ConcurrentHashMap<>();
        private CreationJobStatus status = CreationJobStatus.CREATING;
        private String message = "Preparing…";
        private Integer progress;
        private String containerId;
        private String error;
        private String technicalError;
        private Instant finishedAt;

        Job(String id, String ownerId, String containerName) {
            this.id = id;
            this.ownerId = ownerId;
            this.containerName = containerName;
        }

        synchronized void update(CreationJobStatus newStatus, String newMessage, Integer newProgress) {
            status = newStatus;
            message = newMessage;
            progress = newProgress;
            if (newStatus.isFinished()) {
                finishedAt = clock.instant();
            }
            notifySubscribers();
        }

        synchronized void updateProgress(int newProgress) {
            if (status == CreationJobStatus.PULLING_IMAGE && (progress == null || newProgress > progress)) {
                progress = newProgress;
                notifySubscribers();
            }
        }

        synchronized void setContainerId(String id) {
            containerId = id;
        }

        synchronized void fail(CreationJobStatus failureStatus, String userMessage, Throwable cause) {
            error = userMessage;
            technicalError = cause == null ? null : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            update(failureStatus, userMessage, null);
        }

        synchronized boolean finishedBefore(Instant cutoff) {
            return finishedAt != null && finishedAt.isBefore(cutoff);
        }

        synchronized CreationJobResponse toResponse(boolean admin) {
            return new CreationJobResponse(id, status, message, progress, containerName, containerId, error,
                    admin ? technicalError : null);
        }

        synchronized void subscribe(SseEmitter emitter, boolean admin) {
            subscribers.put(emitter, admin);
            emitter.onCompletion(() -> subscribers.remove(emitter));
            emitter.onTimeout(() -> subscribers.remove(emitter));
            emitter.onError(e -> subscribers.remove(emitter));
            send(emitter, admin);
        }

        private void notifySubscribers() {
            subscribers.forEach(this::send);
        }

        private void send(SseEmitter emitter, boolean admin) {
            try {
                emitter.send(SseEmitter.event().name("update").data(toResponse(admin), MediaType.APPLICATION_JSON));
                if (status.isFinished()) {
                    subscribers.remove(emitter);
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                subscribers.remove(emitter);
            }
        }
    }
}
