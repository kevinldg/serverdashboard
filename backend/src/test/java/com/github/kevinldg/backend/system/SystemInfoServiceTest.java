package com.github.kevinldg.backend.system;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import com.github.kevinldg.backend.container.ContainerOverviewResponse;
import com.github.kevinldg.backend.container.ContainerService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.net.ConnectException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemInfoServiceTest {

    private static final ObjectMapper DOCKER_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final DockerClient dockerClient = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class, RETURNS_DEEP_STUBS);
    private final ContainerService containerService = mock(ContainerService.class);
    private SystemInfoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Properties build = new Properties();
        build.setProperty("version", "1.2.3");
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new BuildProperties(build));
        service = new SystemInfoService(dockerClient, mongoTemplate, containerService, provider, "abc1234");
        when(mongoTemplate.getDb().getName()).thenReturn("serverdashboard");
        when(containerService.getOverview()).thenReturn(new ContainerOverviewResponse(
                new ContainerOverviewResponse.Statistics(3, 3, 0, 1), List.of()));
    }

    @Test
    void collectsApplicationDatabaseAndDockerInformation() throws Exception {
        Info info = DOCKER_JSON.readValue("""
                {"Name": "debian", "ServerVersion": "27.3.1", "OperatingSystem": "Debian GNU/Linux 12 (bookworm)",
                 "KernelVersion": "6.1.0-25-amd64", "Architecture": "x86_64", "NCPU": 16, "MemTotal": 33554432000,
                 "Driver": "overlay2", "DockerRootDir": "/var/lib/docker",
                 "Containers": 3, "ContainersRunning": 3, "ContainersStopped": 0, "Images": 5}
                """, Info.class);
        when(dockerClient.infoCmd().exec()).thenReturn(info);
        when(dockerClient.versionCmd().exec()).thenReturn(DOCKER_JSON.readValue("{\"ApiVersion\": \"1.47\"}", Version.class));
        when(dockerClient.listVolumesCmd().exec().getVolumes()).thenReturn(List.of());

        SystemInfoResponse system = service.getSystemInfo();

        assertThat(system.application().version()).isEqualTo("1.2.3");
        assertThat(system.application().commit()).isEqualTo("abc1234");
        assertThat(system.application().javaVersion()).isNotBlank();
        assertThat(system.database().name()).isEqualTo("serverdashboard");
        assertThat(system.database().reachable()).isTrue();
        assertThat(system.docker().serverVersion()).isEqualTo("27.3.1");
        assertThat(system.docker().apiVersion()).isEqualTo("1.47");
        assertThat(system.docker().cpus()).isEqualTo(16);
        assertThat(system.resources().images()).isEqualTo(5);
        assertThat(system.resources().gameServers()).isEqualTo(1);
        assertThat(system.dockerError()).isNull();
    }

    @Test
    void dockerProblemsDoNotFailThePage() {
        when(dockerClient.infoCmd().exec()).thenThrow(new RuntimeException(new ConnectException("refused")));
        when(mongoTemplate.executeCommand(any(Document.class))).thenThrow(new RuntimeException("timeout"));

        SystemInfoResponse system = service.getSystemInfo();

        assertThat(system.application()).isNotNull();
        assertThat(system.database().reachable()).isFalse();
        assertThat(system.docker()).isNull();
        assertThat(system.dockerError()).isEqualTo("The Docker host is currently unavailable.");
    }
}
