import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
    createContainer,
    type CreationJob,
    type CreationOptions,
    followCreationJob,
    getCreationJob,
    getCreationOptions,
    type TemplateInfo,
} from "../../api/creation";
import { getErrorMessage, getFieldErrors } from "../../api/problem";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { ErrorAlert } from "../../components/ErrorAlert";
import { useApiData } from "../../hooks/useApiData";
import { ContainerFormView } from "./ContainerFormView";
import { type ContainerForm, emptyForm, formFromTemplate, toRequest } from "./formModel";

type Step = { kind: "choose" } | { kind: "form" } | { kind: "progress"; jobId: string };

export function CreateContainerPage() {
    const options = useApiData(getCreationOptions);
    const [step, setStep] = useState<Step>({ kind: "choose" });
    const [form, setForm] = useState<ContainerForm>(emptyForm);

    return (
        <div className="flex flex-col gap-6">
            <div>
                <Link to="/" className="text-sm text-sky-400 hover:underline">
                    ← Dashboard
                </Link>
                <h1 className="mt-2 text-2xl font-semibold text-white">Create container</h1>
            </div>

            {options.error !== null && <ErrorAlert error={options.error} />}
            {!options.data && options.loading && <p className="text-slate-400">Loading…</p>}

            {options.data && step.kind === "choose" && (
                <StartingPoints
                    templates={options.data.templates}
                    onChoose={(template) => {
                        setForm(template ? formFromTemplate(template) : emptyForm());
                        setStep({ kind: "form" });
                    }}
                />
            )}
            {options.data && step.kind === "form" && (
                <FormStep
                    form={form}
                    options={options.data}
                    onChange={setForm}
                    onBack={() => setStep({ kind: "choose" })}
                    onStarted={(jobId) => setStep({ kind: "progress", jobId })}
                />
            )}
            {step.kind === "progress" && (
                <ProgressStep jobId={step.jobId} onBackToForm={() => setStep({ kind: "form" })} />
            )}
        </div>
    );
}

function StartingPoints({ templates, onChoose }: { templates: TemplateInfo[]; onChoose: (template: TemplateInfo | null) => void }) {
    return (
        <div className="flex flex-col gap-3">
            <p className="text-sm text-slate-400">Choose a starting point. Every value can be changed in the next step.</p>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
                <button
                    type="button"
                    onClick={() => onChoose(null)}
                    className="rounded-lg border border-slate-800 bg-slate-900 p-5 text-left hover:border-sky-600"
                >
                    <h2 className="font-semibold text-white">Empty container</h2>
                    <p className="mt-1 text-sm text-slate-400">Any image, configured manually.</p>
                </button>
                {templates.map((info) => (
                    <button
                        key={info.template.id}
                        type="button"
                        onClick={() => onChoose(info)}
                        className="rounded-lg border border-violet-900 bg-slate-900 p-5 text-left hover:border-violet-500"
                    >
                        <span className="text-xs font-medium uppercase tracking-wide text-violet-300">Game server</span>
                        <h2 className="font-semibold text-white">{info.template.name}</h2>
                        <p className="mt-1 text-sm text-slate-400">{info.template.description}</p>
                        <p className="mt-2 font-mono text-xs text-slate-500">{info.template.image}</p>
                    </button>
                ))}
            </div>
        </div>
    );
}

function FormStep({ form, options, onChange, onBack, onStarted }: {
    form: ContainerForm;
    options: CreationOptions;
    onChange: (form: ContainerForm) => void;
    onBack: () => void;
    onStarted: (jobId: string) => void;
}) {
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    async function submit() {
        setError(null);
        if (form.requiresEula && !form.eulaAccepted) {
            setErrors({ eula: "The EULA must be accepted to create this server." });
            return;
        }
        setSubmitting(true);
        try {
            const job = await createContainer(toRequest(form));
            onStarted(job.id);
        } catch (submitError) {
            setErrors(getFieldErrors(submitError));
            setError(getErrorMessage(submitError));
            window.scrollTo({ top: 0, behavior: "smooth" });
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="flex flex-col gap-4">
            {error && <Alert variant="error">{error}</Alert>}
            <ContainerFormView form={form} options={options} errors={errors} onChange={onChange} />
            <div className="flex justify-between gap-3">
                <button type="button" onClick={onBack} className={buttonStyles.secondary}>
                    Back
                </button>
                <button type="button" onClick={() => void submit()} disabled={submitting} className={buttonStyles.primary}>
                    {submitting ? "Checking…" : "Create container"}
                </button>
            </div>
        </div>
    );
}

function ProgressStep({ jobId, onBackToForm }: { jobId: string; onBackToForm: () => void }) {
    const [job, setJob] = useState<CreationJob | null>(null);
    const [connectionLost, setConnectionLost] = useState(false);

    useEffect(() => {
        return followCreationJob(jobId, setJob, () => {
            setConnectionLost(true);
            // The job continues on the server; show its last known state.
            getCreationJob(jobId).then(setJob).catch(() => undefined);
        });
    }, [jobId]);

    if (!job) {
        return <p className="text-slate-400">Starting…</p>;
    }

    const running = job.status === "PULLING_IMAGE" || job.status === "CREATING" || job.status === "STARTING";
    return (
        <div className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-800 bg-slate-900 p-6">
            <h2 className="text-lg font-semibold text-white">{job.containerName}</h2>
            <p className={job.status === "COMPLETED" ? "text-emerald-300" : running ? "text-slate-200" : "text-amber-300"}>
                {job.message}
            </p>
            {job.status === "PULLING_IMAGE" && (
                <div className="h-2 overflow-hidden rounded-full bg-slate-800" role="progressbar" aria-valuenow={job.progress ?? 0}>
                    <div className="h-full bg-sky-500 transition-all" style={{ width: `${job.progress ?? 0}%` }} />
                </div>
            )}
            {job.technicalError && (
                <details className="text-xs text-slate-400">
                    <summary className="cursor-pointer">Technical details</summary>
                    <p className="mt-1 font-mono break-all">{job.technicalError}</p>
                </details>
            )}
            {connectionLost && running && (
                <Alert variant="info">The connection was lost. The container is still being created on the server.</Alert>
            )}
            <div className="flex gap-3">
                {job.containerId && !running && (
                    <Link to={`/containers/${job.containerId}`} className={buttonStyles.primary}>
                        Open container
                    </Link>
                )}
                {job.status === "FAILED" && (
                    <button type="button" onClick={onBackToForm} className={buttonStyles.secondary}>
                        Back to the form
                    </button>
                )}
            </div>
        </div>
    );
}
