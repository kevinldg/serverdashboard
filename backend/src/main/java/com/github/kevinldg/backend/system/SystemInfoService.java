package com.github.kevinldg.backend.system;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.container.ContainerService;
import com.github.kevinldg.backend.docker.DockerCalls;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Optional;

/**
 * Collects system information for the admin area. Docker problems do not fail the whole page: the application and
 * database sections are still returned, together with the reason.
 */
@Service
public class SystemInfoService {

    private final DockerClient dockerClient;
    private final MongoTemplate mongoTemplate;
    private final ContainerService containerService;
    private final Optional<BuildProperties> buildProperties;
    private final String commit;

    public SystemInfoService(DockerClient dockerClient, MongoTemplate mongoTemplate, ContainerService containerService,
                             ObjectProvider<BuildProperties> buildProperties, @Value("${app.commit:unknown}") String commit) {
        this.dockerClient = dockerClient;
        this.mongoTemplate = mongoTemplate;
        this.containerService = containerService;
        this.buildProperties = Optional.ofNullable(buildProperties.getIfAvailable());
        this.commit = commit;
    }

    public SystemInfoResponse getSystemInfo() {
        SystemInfoResponse.Application application = new SystemInfoResponse.Application(
                buildProperties.map(BuildProperties::getVersion).orElse("development"),
                commit,
                buildProperties.map(BuildProperties::getTime).orElse(null),
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),
                Runtime.version().toString(),
                System.getProperty("java.vendor"));

        try {
            Info info = DockerCalls.call(() -> dockerClient.infoCmd().exec(), "Docker information is unavailable.",
                    "Docker information is unavailable.");
            Version version = DockerCalls.call(() -> dockerClient.versionCmd().exec(), "Docker information is unavailable.",
                    "Docker information is unavailable.");
            int volumes = DockerCalls.call(() -> {
                var response = dockerClient.listVolumesCmd().exec();
                return response.getVolumes() == null ? 0 : response.getVolumes().size();
            }, "Docker information is unavailable.", "Docker information is unavailable.");
            int gameServers = containerService.getOverview().statistics().gameServers();

            return new SystemInfoResponse(application, database(),
                    new SystemInfoResponse.DockerHost(info.getName(), info.getServerVersion(), version.getApiVersion(),
                            info.getOperatingSystem(), info.getKernelVersion(), info.getArchitecture(), info.getNCPU(),
                            info.getMemTotal(), info.getDriver(), info.getDockerRootDir()),
                    new SystemInfoResponse.Resources(info.getContainers(), info.getContainersRunning(),
                            info.getContainersStopped(), info.getImages(), volumes, gameServers),
                    null);
        } catch (ApiException e) {
            return new SystemInfoResponse(application, database(), null, null, e.getMessage());
        }
    }

    private SystemInfoResponse.Database database() {
        String name = mongoTemplate.getDb().getName();
        long start = System.nanoTime();
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return new SystemInfoResponse.Database(name, true, (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            return new SystemInfoResponse.Database(name, false, null);
        }
    }
}
