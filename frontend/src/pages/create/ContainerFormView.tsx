import type { ReactNode } from "react";
import type { CreationOptions, MountType, Protocol, RestartPolicyName } from "../../api/creation";
import { type ContainerForm, renameContainer } from "./formModel";

const inputClass =
    "w-full rounded-md border border-line-strong bg-surface px-3 py-1.5 text-sm text-fg outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500 aria-invalid:border-red-500";

interface ContainerFormViewProps {
    form: ContainerForm;
    options: CreationOptions;
    errors: Record<string, string>;
    onChange: (form: ContainerForm) => void;
}

/** The container creation form; errors are keyed like the backend fields (e.g. "ports[0].hostPort"). */
export function ContainerFormView({ form, options, errors, onChange }: ContainerFormViewProps) {
    const update = (changes: Partial<ContainerForm>) => onChange({ ...form, ...changes });

    function updateRow<K extends "ports" | "mounts" | "environment">(key: K, index: number, changes: Partial<ContainerForm[K][number]>) {
        const rows = [...form[key]] as ContainerForm[K][number][];
        rows[index] = { ...rows[index], ...changes };
        update({ [key]: rows } as Partial<ContainerForm>);
    }

    function removeRow(key: "ports" | "mounts" | "environment", index: number) {
        update({ [key]: form[key].filter((_, i) => i !== index) } as Partial<ContainerForm>);
    }

    return (
        <div className="flex flex-col gap-6">
            {form.notes.length > 0 && (
                <ul className="rounded-md border border-warning-line bg-warning-soft/40 px-4 py-3 text-sm text-warning-fg-strong">
                    {form.notes.map((note) => (
                        <li key={note}>{note}</li>
                    ))}
                </ul>
            )}

            <FormSection title="General">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <Field label="Container name" error={errors.name}>
                        <input
                            className={inputClass}
                            value={form.name}
                            aria-invalid={errors.name ? true : undefined}
                            onChange={(event) => onChange(renameContainer(form, event.target.value))}
                        />
                    </Field>
                    <Field label="Image (with tag)" error={errors.image}>
                        <input
                            className={`${inputClass} font-mono`}
                            value={form.image}
                            placeholder="e.g. nginx:1.27"
                            aria-invalid={errors.image ? true : undefined}
                            onChange={(event) => update({ image: event.target.value })}
                        />
                    </Field>
                </div>
            </FormSection>

            <FormSection
                title="Ports"
                description="Host port on the server → port inside the container."
                onAdd={() => update({ ports: [...form.ports, { hostPort: "", containerPort: "", protocol: "TCP" }] })}
                addLabel="Add port"
            >
                {form.ports.length === 0 && <Empty>No published ports.</Empty>}
                {form.ports.map((port, index) => (
                    <Row key={index} onRemove={() => removeRow("ports", index)} description={port.description}
                         error={errors[`ports[${index}].hostPort`] ?? errors[`ports[${index}].containerPort`]}>
                        <input className={`${inputClass} w-28`} inputMode="numeric" placeholder="Host" aria-label="Host port"
                               value={port.hostPort} onChange={(event) => updateRow("ports", index, { hostPort: event.target.value })} />
                        <span className="text-fg-subtle">→</span>
                        <input className={`${inputClass} w-28`} inputMode="numeric" placeholder="Container" aria-label="Container port"
                               value={port.containerPort} onChange={(event) => updateRow("ports", index, { containerPort: event.target.value })} />
                        <select className={`${inputClass} w-24`} aria-label="Protocol" value={port.protocol}
                                onChange={(event) => updateRow("ports", index, { protocol: event.target.value as Protocol })}>
                            <option value="TCP">TCP</option>
                            <option value="UDP">UDP</option>
                        </select>
                    </Row>
                ))}
            </FormSection>

            <FormSection
                title="Storage"
                description={
                    options.bindMountRoot
                        ? `Volumes are managed by Docker. Bind mounts are allowed below ${options.bindMountRoot}. Data is kept when the container is deleted.`
                        : "Volumes are managed by Docker and kept when the container is deleted. Bind mounts are disabled on this server."
                }
                onAdd={() => update({ mounts: [...form.mounts, { type: "VOLUME", source: "", target: "", readOnly: false }] })}
                addLabel="Add volume"
            >
                {form.mounts.length === 0 && <Empty>No persistent storage. Data inside the container is lost when it is deleted.</Empty>}
                {form.mounts.map((mount, index) => (
                    <Row key={index} onRemove={() => removeRow("mounts", index)} description={mount.description}
                         error={errors[`mounts[${index}].type`] ?? errors[`mounts[${index}].source`] ?? errors[`mounts[${index}].target`]}>
                        <select className={`${inputClass} w-32`} aria-label="Type" value={mount.type}
                                onChange={(event) => updateRow("mounts", index, { type: event.target.value as MountType })}>
                            <option value="VOLUME">Volume</option>
                            <option value="BIND" disabled={!options.bindMountRoot}>Bind mount</option>
                        </select>
                        <input className={`${inputClass} font-mono`} aria-label="Source"
                               placeholder={mount.type === "VOLUME" ? "volume name" : `${options.bindMountRoot ?? ""}/…`}
                               value={mount.source} onChange={(event) => updateRow("mounts", index, { source: event.target.value })} />
                        <span className="text-fg-subtle">→</span>
                        <input className={`${inputClass} font-mono`} aria-label="Container path" placeholder="/data"
                               value={mount.target} onChange={(event) => updateRow("mounts", index, { target: event.target.value })} />
                        <label className="flex shrink-0 items-center gap-1 text-xs text-fg-muted">
                            <input type="checkbox" checked={mount.readOnly}
                                   onChange={(event) => updateRow("mounts", index, { readOnly: event.target.checked })} />
                            read-only
                        </label>
                    </Row>
                ))}
            </FormSection>

            <FormSection
                title="Environment variables"
                onAdd={() => update({ environment: [...form.environment, { name: "", value: "" }] })}
                addLabel="Add variable"
            >
                {form.environment.length === 0 && <Empty>No environment variables.</Empty>}
                {form.environment.map((variable, index) => (
                    <Row key={index} onRemove={() => removeRow("environment", index)} description={variable.description}
                         error={errors[`environment[${index}].name`] ?? errors[`environment[${index}].value`]}>
                        <input className={`${inputClass} w-56 font-mono`} aria-label="Name" placeholder="NAME"
                               value={variable.name} onChange={(event) => updateRow("environment", index, { name: event.target.value })} />
                        <span className="text-fg-subtle">=</span>
                        {variable.options && variable.options.length > 0 ? (
                            <select className={`${inputClass} font-mono`} aria-label="Value" value={variable.value}
                                    onChange={(event) => updateRow("environment", index, { value: event.target.value })}>
                                {variable.options.map((option) => (
                                    <option key={option} value={option}>{option}</option>
                                ))}
                            </select>
                        ) : (
                            <input className={`${inputClass} font-mono`} aria-label="Value"
                                   value={variable.value} onChange={(event) => updateRow("environment", index, { value: event.target.value })} />
                        )}
                    </Row>
                ))}
            </FormSection>

            <FormSection title="Runtime">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                    <Field label="Restart policy" error={errors["restartPolicy.name"]}>
                        <select className={inputClass} value={form.restartPolicy}
                                onChange={(event) => update({ restartPolicy: event.target.value as RestartPolicyName })}>
                            <option value="unless-stopped">unless-stopped (recommended)</option>
                            <option value="always">always</option>
                            <option value="on-failure">on-failure</option>
                            <option value="no">no</option>
                        </select>
                    </Field>
                    {form.restartPolicy === "on-failure" && (
                        <Field label="Max. retries (empty = unlimited)" error={errors["restartPolicy.maximumRetryCount"]}>
                            <input className={inputClass} inputMode="numeric" value={form.maximumRetryCount}
                                   onChange={(event) => update({ maximumRetryCount: event.target.value })} />
                        </Field>
                    )}
                    <Field label="Network" error={errors.network}>
                        <select className={inputClass} value={form.network} onChange={(event) => update({ network: event.target.value })}>
                            {options.networks.map((network) => (
                                <option key={network.name} value={network.name}>{network.name} ({network.driver})</option>
                            ))}
                        </select>
                    </Field>
                    <Field label="Memory limit in MB (optional)" error={errors.memoryLimitMb}>
                        <input className={inputClass} inputMode="numeric" placeholder="no limit" value={form.memoryLimitMb}
                               onChange={(event) => update({ memoryLimitMb: event.target.value })} />
                    </Field>
                </div>
                <label className="mt-4 flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={form.start} onChange={(event) => update({ start: event.target.checked })} />
                    Start the container after creating it
                </label>
            </FormSection>

            {form.requiresEula && (
                <div className={`rounded-md border px-4 py-3 text-sm ${errors.eula ? "border-danger-line bg-danger-soft/40" : "border-line-strong bg-surface"}`}>
                    <label className="flex items-start gap-2">
                        <input type="checkbox" className="mt-1" checked={form.eulaAccepted}
                               onChange={(event) => update({ eulaAccepted: event.target.checked })} />
                        <span>
                            I accept the{" "}
                            <a href="https://www.minecraft.net/eula" target="_blank" rel="noreferrer" className="text-accent-fg-vivid underline">
                                Minecraft End User License Agreement
                            </a>{" "}
                            (sets <code className="font-mono">EULA=TRUE</code>).
                        </span>
                    </label>
                    {errors.eula && <p className="mt-1 text-danger-fg-vivid">{errors.eula}</p>}
                </div>
            )}
        </div>
    );
}

function FormSection({ title, description, children, onAdd, addLabel }: {
    title: string;
    description?: string;
    children: ReactNode;
    onAdd?: () => void;
    addLabel?: string;
}) {
    return (
        <section className="rounded-lg border border-line bg-surface p-5">
            <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
                <div>
                    <h2 className="font-semibold text-fg-strong">{title}</h2>
                    {description && <p className="text-xs text-fg-muted">{description}</p>}
                </div>
                {onAdd && (
                    <button type="button" onClick={onAdd} className="text-sm text-accent-fg-vivid hover:underline">
                        + {addLabel}
                    </button>
                )}
            </div>
            <div className="flex flex-col gap-3">{children}</div>
        </section>
    );
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
    return (
        <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium text-fg-secondary">{label}</span>
            {children}
            {error && <span className="text-danger-fg-vivid">{error}</span>}
        </label>
    );
}

function Row({ children, onRemove, description, error }: {
    children: ReactNode;
    onRemove: () => void;
    description?: string;
    error?: string;
}) {
    return (
        <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
                {children}
                <button type="button" onClick={onRemove} aria-label="Remove" className="shrink-0 px-2 text-fg-subtle hover:text-danger-fg-vivid">
                    ✕
                </button>
            </div>
            {description && <p className="text-xs text-fg-subtle">{description}</p>}
            {error && <p className="text-xs text-danger-fg-vivid">{error}</p>}
        </div>
    );
}

function Empty({ children }: { children: string }) {
    return <p className="text-sm text-fg-subtle">{children}</p>;
}
