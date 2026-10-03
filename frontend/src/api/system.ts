import { apiClient } from "./client";

export interface SystemInfo {
    application: {
        version: string;
        /** Git commit the running version was built from, "unknown" if not provided. */
        commit: string;
        buildTime: string | null;
        startedAt: string;
        javaVersion: string;
        javaVendor: string;
    };
    database: { name: string; reachable: boolean; latencyMs: number | null };
    /** Null if the Docker host could not be reached (see dockerError). */
    docker: {
        hostname: string;
        serverVersion: string;
        apiVersion: string;
        operatingSystem: string;
        kernelVersion: string;
        architecture: string;
        cpus: number;
        memoryBytes: number;
        storageDriver: string;
        rootDirectory: string;
    } | null;
    resources: { containers: number; running: number; stopped: number; images: number; volumes: number; gameServers: number } | null;
    dockerError: string | null;
}

export async function getSystemInfo(): Promise<SystemInfo> {
    const response = await apiClient.get<SystemInfo>("/admin/system");
    return response.data;
}
