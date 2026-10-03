package com.github.kevinldg.backend.system;

import java.time.Instant;

/**
 * Information about the application and the managed Docker host.
 *
 * @param docker      null if the Docker host could not be reached (see {@code dockerError})
 * @param resources   null if the Docker host could not be reached
 * @param dockerError user-friendly reason why Docker information is missing
 */
public record SystemInfoResponse(Application application, Database database, DockerHost docker, Resources resources,
                                 String dockerError) {

    /**
     * @param commit Git commit the running version was built from, "unknown" if not provided at build time
     */
    public record Application(String version, String commit, Instant buildTime, Instant startedAt, String javaVersion,
                              String javaVendor) {
    }

    /** Never contains the connection string. */
    public record Database(String name, boolean reachable, Long latencyMs) {
    }

    public record DockerHost(String hostname, String serverVersion, String apiVersion, String operatingSystem,
                             String kernelVersion, String architecture, Integer cpus, Long memoryBytes,
                             String storageDriver, String rootDirectory) {
    }

    public record Resources(Integer containers, Integer running, Integer stopped, Integer images, Integer volumes,
                            Integer gameServers) {
    }
}
