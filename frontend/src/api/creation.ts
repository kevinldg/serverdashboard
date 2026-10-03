import { apiClient } from "./client";

export type Protocol = "TCP" | "UDP";
export type MountType = "VOLUME" | "BIND";
export type RestartPolicyName = "no" | "always" | "unless-stopped" | "on-failure";

export interface ContainerTemplate {
    id: string;
    name: string;
    description: string;
    image: string;
    ports: { containerPort: number; protocol: string; description: string }[];
    volumes: { target: string; volumeSuffix: string; description: string }[];
    environment: { name: string; defaultValue: string; description: string; options: string[] }[];
    restartPolicy: RestartPolicyName;
    memoryLimitMb: number | null;
    requiresEula: boolean;
    notes: string[];
}

export interface TemplateInfo {
    profileId: string;
    profileName: string;
    template: ContainerTemplate;
}

export interface CreationOptions {
    /** Null if bind mounts are disabled on the server. */
    bindMountRoot: string | null;
    networks: { name: string; driver: string }[];
    templates: TemplateInfo[];
}

export interface ContainerCreateRequest {
    name: string;
    image: string;
    ports: { hostPort: number; containerPort: number; protocol: Protocol }[];
    mounts: { type: MountType; source: string; target: string; readOnly: boolean }[];
    environment: { name: string; value: string }[];
    restartPolicy: { name: RestartPolicyName; maximumRetryCount: number | null };
    network: string;
    memoryLimitMb: number | null;
    templateId: string | null;
    start: boolean;
}

export type CreationJobStatus = "PULLING_IMAGE" | "CREATING" | "STARTING" | "COMPLETED" | "START_FAILED" | "FAILED";

export interface CreationJob {
    id: string;
    status: CreationJobStatus;
    message: string;
    /** Image download progress in percent. */
    progress: number | null;
    containerName: string;
    containerId: string | null;
    error: string | null;
    /** Only for administrators. */
    technicalError: string | null;
}

export const isFinished = (status: CreationJobStatus) =>
    status === "COMPLETED" || status === "START_FAILED" || status === "FAILED";

export async function getCreationOptions(): Promise<CreationOptions> {
    const response = await apiClient.get<CreationOptions>("/containers/creation-options");
    return response.data;
}

/** Validates the request and starts the creation job. */
export async function createContainer(request: ContainerCreateRequest): Promise<CreationJob> {
    const response = await apiClient.post<CreationJob>("/containers", request);
    return response.data;
}

export async function getCreationJob(id: string): Promise<CreationJob> {
    const response = await apiClient.get<CreationJob>(`/container-jobs/${encodeURIComponent(id)}`);
    return response.data;
}

/**
 * Follows a creation job (Server-Sent Events). Calls onUpdate for every change; the stream ends when the job is
 * finished. onError is called if the connection is lost before that.
 *
 * @returns a function that stops following
 */
export function followCreationJob(id: string, onUpdate: (job: CreationJob) => void, onError: () => void): () => void {
    const source = new EventSource(`/api/container-jobs/${encodeURIComponent(id)}/events`);
    let finished = false;
    source.addEventListener("update", (event) => {
        const job = JSON.parse((event as MessageEvent<string>).data) as CreationJob;
        if (isFinished(job.status)) {
            finished = true;
            source.close();
        }
        onUpdate(job);
    });
    source.onerror = () => {
        source.close();
        if (!finished) {
            onError();
        }
    };
    return () => source.close();
}
