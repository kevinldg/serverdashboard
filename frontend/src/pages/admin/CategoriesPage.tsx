import { type FormEvent, useState } from "react";
import {
    type CategoryColor,
    type CategoryInput,
    createCategory,
    deleteCategory,
    listCategories,
    type ManagedCategory,
    MAX_CATEGORY_NAME_LENGTH,
    updateCategory,
} from "../../api/categories";
import { getErrorMessage, getFieldErrors } from "../../api/problem";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { CategoryBadge } from "../../components/CategoryBadge";
import { CATEGORY_COLORS } from "../../components/categoryColors";
import { ConfirmModal } from "../../components/ConfirmModal";
import { ErrorAlert } from "../../components/ErrorAlert";
import { Modal } from "../../components/Modal";
import { RefreshButton } from "../../components/RefreshButton";
import { RowButton } from "../../components/RowButton";
import { TextField } from "../../components/TextField";
import { useApiData } from "../../hooks/useApiData";

type Dialog = { type: "create" } | { type: "edit"; category: ManagedCategory } | { type: "delete"; category: ManagedCategory };

const containers = (count: number) => `${count} ${count === 1 ? "container" : "containers"}`;

export function CategoriesPage() {
    const categories = useApiData(listCategories);
    const [dialog, setDialog] = useState<Dialog | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [actionError, setActionError] = useState<unknown>(null);

    async function runAction(action: () => Promise<string>) {
        setDialog(null);
        setNotice(null);
        setActionError(null);
        try {
            setNotice(await action());
        } catch (error) {
            setActionError(error);
        }
        await categories.reload();
    }

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <h2 className="text-lg font-semibold text-fg-strong">Categories</h2>
                    <p className="text-sm text-fg-muted">
                        Categories such as "System" or "Communication" can be assigned to containers that are not game
                        servers, in the container details under "Change classification".
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                    <RefreshButton
                        onRefresh={() => void categories.reload()}
                        loading={categories.loading}
                        lastUpdated={categories.lastUpdated}
                    />
                    <button type="button" onClick={() => setDialog({ type: "create" })} className={buttonStyles.primary}>
                        Create category
                    </button>
                </div>
            </div>

            {notice && <Alert variant="success">{notice}</Alert>}
            {actionError !== null && <ErrorAlert error={actionError} />}
            {categories.error !== null && <ErrorAlert error={categories.error} />}

            {categories.data && (
                <div className="overflow-x-auto rounded-lg border border-line">
                    <table className="table-stack w-full text-left text-sm">
                        <thead className="bg-surface text-xs uppercase tracking-wide text-fg-muted">
                            <tr>
                                <th className="px-4 py-3 font-medium">Category</th>
                                <th className="px-4 py-3 font-medium">Color</th>
                                <th className="px-4 py-3 font-medium">Assigned to</th>
                                <th className="px-4 py-3 font-medium">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {categories.data.map((category) => (
                                <tr key={category.id} className="hover:bg-surface/60">
                                    <td className="px-4 py-3">
                                        <CategoryBadge category={category} />
                                    </td>
                                    <td data-label="Color" className="px-4 py-3 text-fg-muted">
                                        {CATEGORY_COLORS[category.color].label}
                                    </td>
                                    <td data-label="Assigned to" className="px-4 py-3 text-fg-muted">
                                        {containers(category.containerCount)}
                                    </td>
                                    <td className="px-4 py-3">
                                        <div className="flex flex-wrap gap-2">
                                            <RowButton onClick={() => setDialog({ type: "edit", category })}>Edit</RowButton>
                                            <RowButton danger onClick={() => setDialog({ type: "delete", category })}>
                                                Delete
                                            </RowButton>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {categories.data.length === 0 && (
                                <tr>
                                    <td colSpan={4} className="px-4 py-6 text-center text-fg-muted">
                                        No categories yet.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            )}
            {!categories.data && categories.loading && <p className="text-fg-muted">Loading categories…</p>}

            {(dialog?.type === "create" || dialog?.type === "edit") && (
                <CategoryFormModal
                    category={dialog.type === "edit" ? dialog.category : null}
                    onSaved={(message) => {
                        setDialog(null);
                        setNotice(message);
                        setActionError(null);
                        void categories.reload();
                    }}
                    onClose={() => setDialog(null)}
                />
            )}

            {dialog?.type === "delete" && (
                <ConfirmModal
                    title={`Delete category "${dialog.category.name}"?`}
                    confirmLabel="Delete category"
                    destructive
                    onCancel={() => setDialog(null)}
                    onConfirm={() => {
                        const target = dialog.category;
                        void runAction(async () => {
                            await deleteCategory(target.id);
                            return `"${target.name}" was deleted.`;
                        });
                    }}
                >
                    {dialog.category.containerCount > 0 ? (
                        <p>
                            The category is assigned to {containers(dialog.category.containerCount)}. Their classification
                            will be reset to automatic detection.
                        </p>
                    ) : (
                        <p>The category is not assigned to any container.</p>
                    )}
                    <p className="text-fg-muted">Containers and their data are not affected.</p>
                </ConfirmModal>
            )}
        </div>
    );
}

interface CategoryFormModalProps {
    /** Null to create a new category. */
    category: ManagedCategory | null;
    onSaved: (message: string) => void;
    onClose: () => void;
}

function CategoryFormModal({ category, onSaved, onClose }: CategoryFormModalProps) {
    const isNew = category === null;
    const [name, setName] = useState(category?.name ?? "");
    const [color, setColor] = useState<CategoryColor>(category?.color ?? "BLUE");
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        if (!name.trim()) {
            setFieldErrors({ name: "Please enter a name." });
            return;
        }
        setFieldErrors({});

        const input: CategoryInput = { name: name.trim(), color };
        setSaving(true);
        try {
            const saved = isNew ? await createCategory(input) : await updateCategory(category.id, input);
            onSaved(`"${saved.name}" was ${isNew ? "created" : "updated"}.`);
        } catch (saveError) {
            setFieldErrors(getFieldErrors(saveError));
            setError(getErrorMessage(saveError));
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal title={isNew ? "Create category" : "Edit category"} onClose={onClose}>
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                {error && <Alert variant="error">{error}</Alert>}

                <TextField
                    label="Name"
                    value={name}
                    maxLength={MAX_CATEGORY_NAME_LENGTH}
                    placeholder="e.g. System"
                    onChange={(event) => setName(event.target.value)}
                    error={fieldErrors.name}
                    required
                />

                <fieldset className="flex flex-col gap-2">
                    <legend className="mb-1.5 text-sm font-medium text-fg-secondary">Color</legend>
                    <div className="flex flex-wrap gap-2">
                        {(Object.keys(CATEGORY_COLORS) as CategoryColor[]).map((option) => (
                            <label
                                key={option}
                                className={`flex cursor-pointer items-center rounded-full p-0.5 ring-2 has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-sky-500 ${
                                    color === option ? "ring-sky-500" : "ring-transparent"
                                }`}
                            >
                                <input
                                    type="radio"
                                    name="category-color"
                                    value={option}
                                    checked={color === option}
                                    onChange={() => setColor(option)}
                                    className="sr-only"
                                />
                                <CategoryBadge category={{ name: CATEGORY_COLORS[option].label, color: option }} />
                            </label>
                        ))}
                    </div>
                </fieldset>

                <div className="flex items-center gap-2 text-sm text-fg-muted">
                    Preview:
                    <CategoryBadge category={{ name: name.trim() || "Category", color }} />
                </div>

                <div className="mt-2 flex flex-wrap justify-end gap-3">
                    <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                        Cancel
                    </button>
                    <button type="submit" disabled={saving} className={buttonStyles.primary}>
                        {saving ? "Saving…" : isNew ? "Create category" : "Save"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
