package com.github.kevinldg.backend.container.creation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Network;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.EnvironmentVariable;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.MountSpec;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.MountType;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.PortSpec;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.Protocol;
import com.github.kevinldg.backend.container.creation.ContainerCreateRequest.RestartSpec;
import com.github.kevinldg.backend.gameserver.GameServerService;
import com.github.kevinldg.backend.gameserver.profile.MinecraftJavaProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContainerCreationValidatorTest {

    private static final ObjectMapper DOCKER_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DockerClient dockerClient = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final GameServerService gameServerService = mock(GameServerService.class);

    @BeforeEach
    void setUp() throws Exception {
        List<Network> networks = DOCKER_JSON.readValue("""
                [{"Name": "bridge", "Driver": "bridge"}, {"Name": "host", "Driver": "host"}, {"Name": "games", "Driver": "bridge"}]
                """, new TypeReference<>() {
        });
        List<Container> containers = DOCKER_JSON.readValue("""
                [{"Id": "1", "Names": ["/minecraft-server01"], "State": "running",
                  "Ports": [{"PrivatePort": 25565, "PublicPort": 25565, "Type": "tcp", "IP": "0.0.0.0"}]},
                 {"Id": "2", "Names": ["/stopped"], "State": "exited", "Ports": []}]
                """, new TypeReference<>() {
        });
        when(dockerClient.listNetworksCmd().exec()).thenReturn(networks);
        when(dockerClient.listContainersCmd().withShowAll(true).exec()).thenReturn(containers);
        when(gameServerService.findTemplate(any())).thenReturn(Optional.empty());
        MinecraftJavaProfile minecraft = new MinecraftJavaProfile();
        when(gameServerService.findTemplate("minecraft-java")).thenReturn(Optional.of(
                new GameServerService.TemplateInfo(minecraft.id(), minecraft.displayName(), minecraft.templates().getFirst())));
    }

    @Test
    void validRequestPasses() {
        assertThatCode(() -> validator("/srv/gameservers").validate(request(
                List.of(new PortSpec(25566, 25565, Protocol.TCP)),
                List.of(new MountSpec(MountType.VOLUME, "mc2-data", "/data", false),
                        new MountSpec(MountType.BIND, "/srv/gameservers/mc2/backups", "/backups", true)),
                List.of(new EnvironmentVariable("EULA", "TRUE")), "games"))).doesNotThrowAnyException();
    }

    @Test
    void bindMountsAreDisabledWithoutRoot() {
        assertFieldError(validator(null), mount(MountType.BIND, "/srv/gameservers/mc", "/data"), "mounts[0].type");
    }

    @Test
    void bindMountsMustStayBelowRoot() {
        ContainerCreationValidator validator = validator("/srv/gameservers");
        assertFieldError(validator, mount(MountType.BIND, "/etc", "/data"), "mounts[0].source");
        assertFieldError(validator, mount(MountType.BIND, "/", "/data"), "mounts[0].source");
        assertFieldError(validator, mount(MountType.BIND, "/srv/gameservers", "/data"), "mounts[0].source");
        assertFieldError(validator, mount(MountType.BIND, "/srv/gameservers/../../etc", "/data"), "mounts[0].source");
        assertFieldError(validator, mount(MountType.BIND, "/srv/gameservers-other/x", "/data"), "mounts[0].source");
        assertFieldError(validator, mount(MountType.BIND, "relative", "/data"), "mounts[0].source");
    }

    @Test
    void dockerSocketCanNeverBeMounted() {
        assertFieldError(validator("/var"), mount(MountType.BIND, "/var/run/docker.sock", "/var/run/docker.sock"),
                "mounts[0].source");
        assertFieldError(validator("/srv/gameservers"), mount(MountType.BIND, "/srv/gameservers/docker.sock", "/s"),
                "mounts[0].source");
    }

    @Test
    void mountTargetsMustBeAbsoluteAndUnique() {
        ContainerCreationValidator validator = validator(null);
        assertFieldError(validator, mount(MountType.VOLUME, "data", "data"), "mounts[0].target");
        assertFieldError(validator, mount(MountType.VOLUME, "data", "/"), "mounts[0].target");
        assertFieldError(validator, request(List.of(), List.of(
                new MountSpec(MountType.VOLUME, "a", "/data", false),
                new MountSpec(MountType.VOLUME, "b", "/data/", false)), List.of(), "bridge"), "mounts[1].target");
        assertFieldError(validator, mount(MountType.VOLUME, "../evil", "/data"), "mounts[0].source");
    }

    @Test
    void hostNetworkAndUnknownNetworksAreRejected() {
        assertFieldError(validator(null), request(List.of(), List.of(), List.of(), "host"), "network");
        assertFieldError(validator(null), request(List.of(), List.of(), List.of(), "container:abc"), "network");
        assertFieldError(validator(null), request(List.of(), List.of(), List.of(), "missing"), "network");
    }

    @Test
    void duplicatesInRequestAreRejected() {
        assertFieldError(validator(null), request(List.of(
                new PortSpec(7777, 7777, Protocol.UDP), new PortSpec(7777, 7778, Protocol.UDP)), List.of(), List.of(), "bridge"),
                "ports[1].hostPort");
        assertFieldError(validator(null), request(List.of(), List.of(), List.of(
                new EnvironmentVariable("A", "1"), new EnvironmentVariable("A", "2")), "bridge"), "environment[1].name");
    }

    @Test
    void sameHostPortWithDifferentProtocolsIsAllowed() {
        assertThatCode(() -> validator(null).validate(request(List.of(
                new PortSpec(7777, 7777, Protocol.TCP), new PortSpec(7777, 7777, Protocol.UDP)), List.of(), List.of(), "bridge")))
                .doesNotThrowAnyException();
    }

    @Test
    void eulaMustBeAcceptedForTemplatesThatRequireIt() {
        ContainerCreateRequest withoutEula = withTemplate(request(List.of(), List.of(), List.of(), "bridge"), "minecraft-java");
        assertFieldError(validator(null), withoutEula, "eula");

        ContainerCreateRequest withEula = withTemplate(request(List.of(), List.of(),
                List.of(new EnvironmentVariable("EULA", "true")), "bridge"), "minecraft-java");
        assertThatCode(() -> validator(null).validate(withEula)).doesNotThrowAnyException();

        assertFieldError(validator(null), withTemplate(request(List.of(), List.of(), List.of(), "bridge"), "valheim"), "templateId");
    }

    @Test
    void retryCountOnlyForOnFailure() {
        ContainerCreateRequest base = request(List.of(), List.of(), List.of(), "bridge");
        ContainerCreateRequest invalid = new ContainerCreateRequest(base.name(), base.image(), base.ports(), base.mounts(),
                base.environment(), new RestartSpec("always", 3), base.network(), null, null, true);
        assertFieldError(validator(null), invalid, "restartPolicy.maximumRetryCount");
    }

    @Test
    void conflictsWithExistingContainersAreReported() {
        ContainerCreateRequest taken = new ContainerCreateRequest("minecraft-server01", "nginx", List.of(
                new PortSpec(25565, 25565, Protocol.TCP)), List.of(), List.of(), new RestartSpec("no", null), "bridge",
                null, null, true);

        assertThatThrownBy(() -> validator(null).validate(taken))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getFieldErrors()).containsEntry("name", "A container with this name already exists.");
                    assertThat(ex.getFieldErrors().get("ports[0].hostPort")).contains("minecraft-server01");
                });
    }

    private static void assertFieldError(ContainerCreationValidator validator, ContainerCreateRequest request, String field) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getFieldErrors()).containsKey(field);
                });
    }

    private ContainerCreationValidator validator(String bindMountRoot) {
        return new ContainerCreationValidator(dockerClient, new ContainerCreationProperties(bindMountRoot), gameServerService);
    }

    private static ContainerCreateRequest mount(MountType type, String source, String target) {
        return request(List.of(), List.of(new MountSpec(type, source, target, false)), List.of(), "bridge");
    }

    private static ContainerCreateRequest request(List<PortSpec> ports, List<MountSpec> mounts,
                                                  List<EnvironmentVariable> environment, String network) {
        return new ContainerCreateRequest("new-container", "itzg/minecraft-server:latest", ports, mounts, environment,
                new RestartSpec("unless-stopped", null), network, 3072, null, true);
    }

    private static ContainerCreateRequest withTemplate(ContainerCreateRequest request, String templateId) {
        return new ContainerCreateRequest(request.name(), request.image(), request.ports(), request.mounts(),
                request.environment(), request.restartPolicy(), request.network(), request.memoryLimitMb(), templateId,
                request.start());
    }

}
