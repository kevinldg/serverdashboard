import type {
    ContainerCreateRequest,
    MountType,
    Protocol,
    RestartPolicyName,
    TemplateInfo,
} from "../../api/creation";

/** Editable form state; numbers are kept as strings while typing. */
export interface ContainerForm {
    templateId: string | null;
    name: string;
    image: string;
    ports: { hostPort: string; containerPort: string; protocol: Protocol; description?: string }[];
    mounts: {
        type: MountType;
        source: string;
        target: string;
        readOnly: boolean;
        description?: string;
        /** Template volume suffix; the volume name follows the container name until edited manually. */
        volumeSuffix?: string;
    }[];
    environment: { name: string; value: string; description?: string; options?: string[] }[];
    restartPolicy: RestartPolicyName;
    maximumRetryCount: string;
    network: string;
    memoryLimitMb: string;
    start: boolean;
    requiresEula: boolean;
    eulaAccepted: boolean;
    notes: string[];
}

export function emptyForm(): ContainerForm {
    return {
        templateId: null,
        name: "",
        image: "",
        ports: [],
        mounts: [],
        environment: [],
        restartPolicy: "unless-stopped",
        maximumRetryCount: "",
        network: "bridge",
        memoryLimitMb: "",
        start: true,
        requiresEula: false,
        eulaAccepted: false,
        notes: [],
    };
}

export function formFromTemplate({ template }: TemplateInfo): ContainerForm {
    const name = template.id;
    return {
        ...emptyForm(),
        templateId: template.id,
        name,
        image: template.image,
        ports: template.ports.map((port) => ({
            hostPort: String(port.containerPort),
            containerPort: String(port.containerPort),
            protocol: port.protocol.toUpperCase() as Protocol,
            description: port.description,
        })),
        mounts: template.volumes.map((volume) => ({
            type: "VOLUME",
            source: `${name}-${volume.volumeSuffix}`,
            target: volume.target,
            readOnly: false,
            description: volume.description,
            volumeSuffix: volume.volumeSuffix,
        })),
        environment: template.environment.map((variable) => ({
            name: variable.name,
            value: variable.defaultValue,
            description: variable.description,
            options: variable.options,
        })),
        restartPolicy: template.restartPolicy,
        memoryLimitMb: template.memoryLimitMb ? String(template.memoryLimitMb) : "",
        requiresEula: template.requiresEula,
        notes: template.notes,
    };
}

/** Renames the container and the volumes that still follow its name. */
export function renameContainer(form: ContainerForm, name: string): ContainerForm {
    return {
        ...form,
        name,
        mounts: form.mounts.map((mount) =>
            mount.volumeSuffix && mount.source === `${form.name}-${mount.volumeSuffix}`
                ? { ...mount, source: `${name}-${mount.volumeSuffix}` }
                : mount,
        ),
    };
}

const toNumber = (value: string) => (value.trim() === "" ? NaN : Number(value));

export function toRequest(form: ContainerForm): ContainerCreateRequest {
    const environment = form.environment.filter((variable) => variable.name.trim() !== "")
        .map((variable) => ({ name: variable.name.trim(), value: variable.value }));
    if (form.requiresEula && form.eulaAccepted) {
        environment.push({ name: "EULA", value: "TRUE" });
    }
    return {
        name: form.name.trim(),
        image: form.image.trim(),
        ports: form.ports.map((port) => ({
            hostPort: toNumber(port.hostPort),
            containerPort: toNumber(port.containerPort),
            protocol: port.protocol,
        })),
        mounts: form.mounts.map((mount) => ({
            type: mount.type,
            source: mount.source.trim(),
            target: mount.target.trim(),
            readOnly: mount.readOnly,
        })),
        environment,
        restartPolicy: {
            name: form.restartPolicy,
            maximumRetryCount: form.restartPolicy === "on-failure" && form.maximumRetryCount !== ""
                ? toNumber(form.maximumRetryCount)
                : null,
        },
        network: form.network,
        memoryLimitMb: form.memoryLimitMb.trim() === "" ? null : toNumber(form.memoryLimitMb),
        templateId: form.templateId,
        start: form.start,
    };
}
