package com.github.kevinldg.backend.container.creation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.EnvironmentVariable;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.MountSpec;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.PortSpec;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.Protocol;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.RestartSpec;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.GameServerService.TemplateInfo;
import com.github.kevinldg.backend.gameserver.profile.MinecraftJavaProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerCreationServiceTest {

    private static final ObjectMapper DOCKER_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DockerClient dockerClient = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final ContainerCreationValidator validator = mock(ContainerCreationValidator.class);
    private final GameServerService gameServerService = mock(GameServerService.class);
    private final CreateContainerCmd createCommand = mock(CreateContainerCmd.class, RETURNS_SELF);
    private final PullImageCmd pullCommand = mock(PullImageCmd.class, RETURNS_SELF);

    private final AuthenticatedUser admin = new AuthenticatedUser("admin-1", "kevin", null, true, true, 0, Set.of());
    private final AuthenticatedUser otherCreator = new AuthenticatedUser("user-2", "alice", null, true, false, 0, Set.of());

    private ContainerCreationService service;

    @BeforeEach
    void setUp() {
        service = new ContainerCreationService(dockerClient, validator, new ContainerCreationProperties("/srv/gs"),
                gameServerService, Clock.systemUTC());
        when(validator.validate(any())).thenReturn(Optional.empty());
        when(dockerClient.createContainerCmd(any())).thenReturn(createCommand);
        CreateContainerResponse created = new CreateContainerResponse();
        created.setId("new-container-id");
        when(createCommand.exec()).thenReturn(created);
        when(dockerClient.pullImageCmd(any())).thenReturn(pullCommand);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void createsAndStartsContainerWithRequestedConfiguration() throws Exception {
        CreationJobResponse job = service.create(request(true), admin);
        CreationJobResponse result = awaitFinished(job.id(), admin);

        assertThat(result.status()).isEqualTo(CreationJobStatus.COMPLETED);
        assertThat(result.containerId()).isEqualTo("new-container-id");
        verify(dockerClient).createContainerCmd("nginx:1.27");
        verify(createCommand).withName("web");
        verify(createCommand).withEnv(List.of("MODE=production"));
        verify(createCommand).withExposedPorts(List.of(ExposedPort.tcp(80)));
        verify(createCommand).withLabels(Map.of(ContainerCreationService.CREATED_BY_LABEL, "kevin"));

        ArgumentCaptor<HostConfig> hostConfig = ArgumentCaptor.forClass(HostConfig.class);
        verify(createCommand).withHostConfig(hostConfig.capture());
        assertThat(hostConfig.getValue().getPortBindings().getBindings().get(ExposedPort.tcp(80))[0].getHostPortSpec())
                .isEqualTo("8080");
        assertThat(hostConfig.getValue().getMounts()).singleElement().satisfies(mount -> {
            assertThat(mount.getType()).isEqualTo(MountType.VOLUME);
            assertThat(mount.getSource()).isEqualTo("web-data");
            assertThat(mount.getTarget()).isEqualTo("/usr/share/nginx/html");
            assertThat(mount.getReadOnly()).isTrue();
        });
        assertThat(hostConfig.getValue().getRestartPolicy().getName()).isEqualTo("unless-stopped");
        assertThat(hostConfig.getValue().getNetworkMode()).isEqualTo("bridge");
        assertThat(hostConfig.getValue().getMemory()).isEqualTo(512L * 1024 * 1024);
        verify(dockerClient).startContainerCmd("new-container-id");
        verify(dockerClient, never()).pullImageCmd(any());
    }

    @Test
    void templateAddsGameServerLabel() throws Exception {
        MinecraftJavaProfile minecraft = new MinecraftJavaProfile();
        when(validator.validate(any())).thenReturn(Optional.of(
                new TemplateInfo(minecraft.id(), minecraft.displayName(), minecraft.templates().getFirst())));

        awaitFinished(service.create(request(false), admin).id(), admin);

        verify(createCommand).withLabels(Map.of(ContainerCreationService.CREATED_BY_LABEL, "kevin",
                GameServerService.LABEL, "minecraft-java"));
        verify(dockerClient, never()).startContainerCmd(any());
    }

    @Test
    void missingImageIsPulledWithProgress() throws Exception {
        when(dockerClient.inspectImageCmd("nginx:1.27").exec()).thenThrow(new NotFoundException("No such image"));
        when(pullCommand.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<PullResponseItem> callback = invocation.getArgument(0);
            callback.onNext(pullItem("{\"id\": \"layer1\", \"status\": \"Downloading\", \"progressDetail\": {\"current\": 50, \"total\": 100}}"));
            callback.onNext(pullItem("{\"id\": \"layer1\", \"status\": \"Pull complete\"}"));
            callback.onComplete();
            return callback;
        });

        CreationJobResponse result = awaitFinished(service.create(request(false), admin).id(), admin);

        assertThat(result.status()).isEqualTo(CreationJobStatus.COMPLETED);
        verify(dockerClient).pullImageCmd("nginx");
        verify(pullCommand).withTag("1.27");
    }

    @Test
    void unknownImageFailsWithoutCreatingAnything() throws Exception {
        when(dockerClient.inspectImageCmd("nginx:1.27").exec()).thenThrow(new NotFoundException("No such image"));
        when(pullCommand.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<PullResponseItem> callback = invocation.getArgument(0);
            callback.onError(new NotFoundException("manifest unknown"));
            return callback;
        });

        CreationJobResponse result = awaitFinished(service.create(request(true), admin).id(), admin);

        assertThat(result.status()).isEqualTo(CreationJobStatus.FAILED);
        assertThat(result.error()).isEqualTo("The image nginx:1.27 was not found.");
        verify(dockerClient, never()).createContainerCmd(any());
    }

    @Test
    void startFailureKeepsTheContainer() throws Exception {
        when(dockerClient.startContainerCmd("new-container-id").exec())
                .thenThrow(new InternalServerErrorException("Bind for 0.0.0.0:8080 failed: port is already allocated"));

        CreationJobResponse result = awaitFinished(service.create(request(true), admin).id(), admin);

        assertThat(result.status()).isEqualTo(CreationJobStatus.START_FAILED);
        assertThat(result.containerId()).isEqualTo("new-container-id");
        assertThat(result.error()).contains("host port is already in use");
        assertThat(result.technicalError()).contains("port is already allocated");
    }

    @Test
    void missingBindDirectoryIsExplained() throws Exception {
        when(createCommand.exec()).thenThrow(new BadRequestException(
                "Status 400: {\"message\":\"invalid mount config for type \\\"bind\\\": bind source path does not exist: /srv/gs/mc\"}"));

        CreationJobResponse result = awaitFinished(service.create(request(true), admin).id(), admin);

        assertThat(result.status()).isEqualTo(CreationJobStatus.FAILED);
        assertThat(result.error()).isEqualTo("The host directory /srv/gs/mc does not exist. Create it on the server first.");
    }

    @Test
    void nameConflictDuringCreationFails() throws Exception {
        when(createCommand.exec()).thenThrow(new ConflictException("name already in use"));

        CreationJobResponse result = awaitFinished(service.create(request(true), admin).id(), admin);

        assertThat(result.status()).isEqualTo(CreationJobStatus.FAILED);
        assertThat(result.error()).isEqualTo("A container with this name already exists.");
    }

    @Test
    void technicalDetailsAndJobsOfOthersAreProtected() throws Exception {
        when(dockerClient.startContainerCmd("new-container-id").exec()).thenThrow(new InternalServerErrorException("boom"));
        AuthenticatedUser creator = new AuthenticatedUser("user-1", "bob", null, true, false, 0, Set.of());

        CreationJobResponse job = service.create(request(true), creator);
        CreationJobResponse asCreator = awaitFinished(job.id(), creator);

        assertThat(asCreator.technicalError()).isNull();
        assertThat(service.getJob(job.id(), admin).technicalError()).contains("boom");
        assertThatThrownBy(() -> service.getJob(job.id(), otherCreator))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void validationErrorsAreReportedBeforeAJobStarts() {
        when(validator.validate(any())).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "invalid", Map.of("name", "taken")));

        assertThatThrownBy(() -> service.create(request(true), admin)).isInstanceOf(ApiException.class);
        verify(dockerClient, never()).createContainerCmd(any());
    }

    private CreationJobResponse awaitFinished(String jobId, AuthenticatedUser viewer) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            CreationJobResponse job = service.getJob(jobId, viewer);
            if (job.status().isFinished()) {
                return job;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Job did not finish within 5 seconds");
    }

    private static PullResponseItem pullItem(String json) throws Exception {
        return DOCKER_JSON.readValue(json, PullResponseItem.class);
    }

    private static ContainerCreateRequest request(boolean start) {
        return new ContainerCreateRequest("web", "nginx:1.27",
                List.of(new PortSpec(8080, 80, Protocol.TCP)),
                List.of(new MountSpec(ContainerCreateRequest.MountType.VOLUME, "web-data", "/usr/share/nginx/html", true)),
                List.of(new EnvironmentVariable("MODE", "production")),
                new RestartSpec("unless-stopped", null), "bridge", 512, null, start);
    }
}
