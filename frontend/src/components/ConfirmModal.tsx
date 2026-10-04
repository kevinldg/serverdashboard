import { type ReactNode, useRef } from "react";
import { buttonStyles } from "./buttonStyles";
import { Modal } from "./Modal";

interface ConfirmModalProps {
    title: string;
    children: ReactNode;
    confirmLabel: string;
    /** Red confirm button for actions that may cause data loss. */
    destructive?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

/**
 * Confirmation dialog for destructive or disruptive actions.
 * Render it only while it should be open. Escape and the Cancel button both cancel; Cancel has initial focus.
 */
export function ConfirmModal({ title, children, confirmLabel, destructive = false, onConfirm, onCancel }: ConfirmModalProps) {
    const cancelRef = useRef<HTMLButtonElement>(null);

    return (
        <Modal title={title} onClose={onCancel} initialFocusRef={cancelRef}>
            <div className="flex flex-col gap-3 text-sm text-fg-secondary">{children}</div>
            <div className="mt-6 flex flex-wrap justify-end gap-3">
                <button ref={cancelRef} type="button" onClick={onCancel} className={buttonStyles.secondary}>
                    Cancel
                </button>
                <button
                    type="button"
                    onClick={onConfirm}
                    className={destructive ? buttonStyles.danger : buttonStyles.primary}
                >
                    {confirmLabel}
                </button>
            </div>
        </Modal>
    );
}
