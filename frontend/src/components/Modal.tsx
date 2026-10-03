import { type ReactNode, type RefObject, useEffect, useRef } from "react";

interface ModalProps {
    title: string;
    children: ReactNode;
    onClose: () => void;
    /** Element to focus when the modal opens; defaults to the browser's choice (first focusable element). */
    initialFocusRef?: RefObject<HTMLElement | null>;
    size?: "md" | "lg" | "xl";
}

/**
 * Modal dialog based on the native {@code <dialog>} element (focus trap and Escape handling included).
 * Render it only while it should be open.
 */
export function Modal({ title, children, onClose, initialFocusRef, size = "md" }: ModalProps) {
    const dialogRef = useRef<HTMLDialogElement>(null);

    useEffect(() => {
        const dialog = dialogRef.current;
        if (dialog && !dialog.open) {
            dialog.showModal();
            initialFocusRef?.current?.focus();
        }
        return () => dialog?.close();
    }, [initialFocusRef]);

    return (
        <dialog
            ref={dialogRef}
            aria-labelledby="modal-title"
            onCancel={(event) => {
                event.preventDefault();
                onClose();
            }}
            className={`m-auto w-[calc(100%-2rem)] ${size === "xl" ? "max-w-6xl" : size === "lg" ? "max-w-lg" : "max-w-md"} rounded-lg border border-line-strong bg-surface p-6 text-fg shadow-xl backdrop:bg-black/70`}
        >
            <h2 id="modal-title" className="text-lg font-semibold text-fg-strong">
                {title}
            </h2>
            <div className="mt-4">{children}</div>
        </dialog>
    );
}
