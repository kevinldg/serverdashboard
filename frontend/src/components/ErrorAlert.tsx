import { getErrorMessage, getProblem } from "../api/problem";
import { Alert } from "./Alert";

/** Shows an API error; technical details are only present in responses for administrators. */
export function ErrorAlert({ error }: { error: unknown }) {
    const problem = getProblem(error);

    return (
        <Alert variant="error">
            <p>{getErrorMessage(error)}</p>
            {problem?.exception && (
                <details className="mt-2 text-xs text-danger-fg">
                    <summary className="cursor-pointer">Technical details</summary>
                    <p className="mt-1 font-mono break-all">
                        {problem.exception}: {problem.exceptionMessage}
                    </p>
                </details>
            )}
        </Alert>
    );
}
