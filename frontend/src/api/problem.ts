import axios from "axios";

/** RFC 9457 error response returned by the backend. */
export interface ProblemDetail {
    type?: string;
    title?: string;
    status?: number;
    detail?: string;
    instance?: string;
    /** Field validation errors, keyed by field name. */
    errors?: Record<string, string>;
    /** Set when the request was rejected because maintenance mode is active. */
    maintenance?: boolean;
    maintenanceMessage?: string;
    /** Technical details, only included for administrators. */
    exception?: string;
    exceptionMessage?: string;
}

const DEFAULT_MESSAGE = "Something went wrong. Please try again.";

export function getProblem(error: unknown): ProblemDetail | undefined {
    if (axios.isAxiosError<ProblemDetail>(error) && typeof error.response?.data === "object") {
        return error.response.data;
    }
    return undefined;
}

export function getErrorMessage(error: unknown): string {
    if (axios.isAxiosError(error) && !error.response) {
        return "The server could not be reached. Please try again later.";
    }
    return getProblem(error)?.detail ?? DEFAULT_MESSAGE;
}

export function getFieldErrors(error: unknown): Record<string, string> {
    return getProblem(error)?.errors ?? {};
}
