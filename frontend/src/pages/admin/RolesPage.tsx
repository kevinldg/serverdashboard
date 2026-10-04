import { type FormEvent, useState } from "react";
import type { CurrentUser, Permission } from "../../api/auth";
import { getErrorMessage, getFieldErrors } from "../../api/problem";
import {
    createRole,
    deleteRole,
    listPermissions,
    listRoles,
    type ManagedRole,
    PERMISSION_GROUP_LABELS,
    type PermissionGroup,
    type PermissionInfo,
    updateRole,
} from "../../api/roles";
import { useAuth } from "../../auth/useAuth";
import { Alert } from "../../components/Alert";
import { buttonStyles } from "../../components/buttonStyles";
import { ConfirmModal } from "../../components/ConfirmModal";
import { ErrorAlert } from "../../components/ErrorAlert";
import { Modal } from "../../components/Modal";
import { RefreshButton } from "../../components/RefreshButton";
import { RowButton } from "../../components/RowButton";
import { TextField } from "../../components/TextField";
import { useApiData } from "../../hooks/useApiData";

type Dialog = { type: "create" } | { type: "edit"; role: ManagedRole } | { type: "delete"; role: ManagedRole };

export function RolesPage() {
    const { user: currentUser } = useAuth();
    const roles = useApiData(listRoles);
    const permissions = useApiData(listPermissions);
    const [dialog, setDialog] = useState<Dialog | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [actionError, setActionError] = useState<unknown>(null);

    function handleSaved(message: string) {
        setDialog(null);
        setNotice(message);
        setActionError(null);
        void roles.reload();
    }

    async function handleDelete(role: ManagedRole) {
        setDialog(null);
        setNotice(null);
        setActionError(null);
        try {
            await deleteRole(role.id);
            setNotice(`Role "${role.name}" was deleted.`);
        } catch (error) {
            setActionError(error);
        }
        await roles.reload();
    }

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                    <h2 className="text-lg font-semibold text-fg-strong">Roles & permissions</h2>
                    <p className="text-sm text-fg-muted">Changes apply to users with the role on their next request.</p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                    <RefreshButton onRefresh={() => void roles.reload()} loading={roles.loading} lastUpdated={roles.lastUpdated} />
                    <button type="button" onClick={() => setDialog({ type: "create" })} className={buttonStyles.primary}>
                        Create role
                    </button>
                </div>
            </div>

            {notice && <Alert variant="success">{notice}</Alert>}
            {actionError !== null && <ErrorAlert error={actionError} />}
            {roles.error !== null && <ErrorAlert error={roles.error} />}
            {permissions.error !== null && <ErrorAlert error={permissions.error} />}

            {roles.data && (
                <div className="overflow-x-auto rounded-lg border border-line">
                    <table className="table-stack w-full text-left text-sm">
                        <thead className="bg-surface text-xs uppercase tracking-wide text-fg-muted">
                            <tr>
                                <th className="px-4 py-3 font-medium">Role</th>
                                <th className="px-4 py-3 font-medium">Permissions</th>
                                <th className="px-4 py-3 font-medium">Users</th>
                                <th className="px-4 py-3 font-medium">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {roles.data.map((role) => (
                                <tr key={role.id} className="hover:bg-surface/60">
                                    <td className="px-4 py-3 font-medium">
                                        {role.name}
                                        {role.admin ? (
                                            <Badge className="bg-accent-soft text-accent-fg ring-accent-line">full access</Badge>
                                        ) : role.builtIn ? (
                                            <Badge className="bg-raised text-fg-secondary ring-line-strong">built-in</Badge>
                                        ) : null}
                                    </td>
                                    <td data-label="Permissions" className="px-4 py-3 text-fg-secondary">
                                        {role.admin ? "All" : `${role.permissions.length} of ${permissions.data?.length ?? "–"}`}
                                    </td>
                                    <td data-label="Users" className="px-4 py-3 text-fg-secondary">{role.userCount}</td>
                                    <td className="px-4 py-3">
                                        <div className="flex flex-wrap gap-2">
                                            <RowButton onClick={() => setDialog({ type: "edit", role })}>
                                                {role.admin ? "View" : "Edit"}
                                            </RowButton>
                                            {!role.builtIn && (
                                                <RowButton
                                                    danger
                                                    onClick={() => setDialog({ type: "delete", role })}
                                                    disabledReason={
                                                        role.userCount > 0
                                                            ? "Assign the role's users a different role before deleting it."
                                                            : undefined
                                                    }
                                                >
                                                    Delete
                                                </RowButton>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            {!roles.data && roles.loading && <p className="text-fg-muted">Loading roles…</p>}

            {(dialog?.type === "create" || dialog?.type === "edit") && permissions.data && (
                <RoleEditorModal
                    role={dialog.type === "edit" ? dialog.role : null}
                    permissions={permissions.data}
                    currentUser={currentUser}
                    onSaved={handleSaved}
                    onClose={() => setDialog(null)}
                />
            )}

            {dialog?.type === "delete" && (
                <ConfirmModal
                    title={`Delete role "${dialog.role.name}"?`}
                    confirmLabel="Delete role"
                    destructive
                    onCancel={() => setDialog(null)}
                    onConfirm={() => void handleDelete(dialog.role)}
                >
                    <p>The role will be permanently deleted. No users are assigned to it.</p>
                </ConfirmModal>
            )}
        </div>
    );
}

function Badge({ children, className }: { children: string; className: string }) {
    return <span className={`ml-2 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${className}`}>{children}</span>;
}

interface RoleEditorModalProps {
    /** Null to create a new role. */
    role: ManagedRole | null;
    permissions: PermissionInfo[];
    currentUser: CurrentUser | null;
    onSaved: (message: string) => void;
    onClose: () => void;
}

function RoleEditorModal({ role, permissions, currentUser, onSaved, onClose }: RoleEditorModalProps) {
    const isNew = role === null;
    const readOnly = role?.admin === true;
    const [name, setName] = useState(role?.name ?? "");
    const [selected, setSelected] = useState<Set<Permission>>(new Set(role?.permissions ?? []));
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const groups = Object.keys(PERMISSION_GROUP_LABELS) as PermissionGroup[];
    const initiallyGranted = new Set(role?.permissions ?? []);
    // Non-admins cannot grant permissions they do not have (also enforced by the backend); they may keep or remove them.
    const mayGrant = (permission: Permission) =>
        currentUser?.admin === true || currentUser?.permissions.includes(permission) === true || initiallyGranted.has(permission);

    function toggle(permission: Permission) {
        const next = new Set(selected);
        if (next.has(permission)) {
            next.delete(permission);
        } else {
            next.add(permission);
        }
        setSelected(next);
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (readOnly) {
            onClose();
            return;
        }
        setError(null);
        const trimmedName = name.trim();
        if (trimmedName.length < 2 || trimmedName.length > 32) {
            setFieldErrors({ name: "The name must be 2–32 characters long." });
            return;
        }
        setFieldErrors({});

        setSaving(true);
        try {
            // Keep the order of the permission list for readable requests and logs
            const permissionList = permissions.map((info) => info.name).filter((permission) => selected.has(permission));
            const saved = isNew
                ? await createRole(trimmedName, permissionList)
                : await updateRole(role.id, trimmedName, permissionList);
            onSaved(`Role "${saved.name}" was ${isNew ? "created" : "updated"}.`);
        } catch (saveError) {
            setFieldErrors(getFieldErrors(saveError));
            setError(getErrorMessage(saveError));
        } finally {
            setSaving(false);
        }
    }

    const title = isNew ? "Create role" : readOnly ? `Role "${role.name}"` : `Edit role "${role.name}"`;

    return (
        <Modal title={title} onClose={onClose} size="lg">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                {error && <Alert variant="error">{error}</Alert>}
                {readOnly && <Alert variant="info">The Admin role always has full access to everything and cannot be changed.</Alert>}

                <TextField
                    label="Name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    disabled={role?.builtIn === true}
                    error={fieldErrors.name}
                    required
                />
                {role?.builtIn && !readOnly && (
                    <p className="-mt-2 text-xs text-fg-muted">Built-in roles cannot be renamed, but their permissions can be changed.</p>
                )}

                <div className="flex max-h-[50vh] flex-col gap-4 overflow-y-auto pr-1">
                    {groups.map((group) => (
                        <fieldset key={group}>
                            <legend className="mb-2 text-sm font-semibold text-fg">{PERMISSION_GROUP_LABELS[group]}</legend>
                            <div className="flex flex-col gap-2">
                                {permissions
                                    .filter((info) => info.group === group)
                                    .map((info) => {
                                        const blocked = !readOnly && !mayGrant(info.name);
                                        return (
                                            <label
                                                key={info.name}
                                                className={`flex items-start gap-3 rounded-md px-2 py-1.5 text-sm ${blocked ? "opacity-50" : "hover:bg-raised/60"}`}
                                                title={blocked ? "You cannot grant a permission you do not have yourself." : undefined}
                                            >
                                                <input
                                                    type="checkbox"
                                                    className="mt-0.5"
                                                    checked={readOnly || selected.has(info.name)}
                                                    disabled={readOnly || blocked}
                                                    onChange={() => toggle(info.name)}
                                                />
                                                <span>
                                                    <span className="block text-fg">{info.description}</span>
                                                    <span className="font-mono text-xs text-fg-subtle">{info.name}</span>
                                                </span>
                                            </label>
                                        );
                                    })}
                            </div>
                        </fieldset>
                    ))}
                </div>

                <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
                    <span className="text-xs text-fg-muted">
                        {readOnly ? "All" : selected.size} of {permissions.length} permissions
                    </span>
                    <div className="flex gap-3">
                        {!readOnly && (
                            <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                                Cancel
                            </button>
                        )}
                        <button type="submit" disabled={saving} className={buttonStyles.primary}>
                            {readOnly ? "Close" : saving ? "Saving…" : isNew ? "Create role" : "Save"}
                        </button>
                    </div>
                </div>
            </form>
        </Modal>
    );
}
