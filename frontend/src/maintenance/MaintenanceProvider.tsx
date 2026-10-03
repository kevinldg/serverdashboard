import { type ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import { apiClient } from "../api/client";
import { getMaintenanceStatus, type PublicMaintenanceStatus } from "../api/maintenance";
import { getProblem } from "../api/problem";
import { MaintenanceContext, type MaintenanceContextValue } from "./MaintenanceContext";

const NOT_IN_MAINTENANCE: PublicMaintenanceStatus = { enabled: false, message: "" };

export function MaintenanceProvider({ children }: { children: ReactNode }) {
    const [status, setStatus] = useState<PublicMaintenanceStatus | null>(null);

    // Any API response rejected because of maintenance mode switches the app to the maintenance page.
    useEffect(() => {
        const interceptor = apiClient.interceptors.response.use(undefined, (error: unknown) => {
            const problem = getProblem(error);
            if (problem?.maintenance) {
                setStatus({ enabled: true, message: problem.maintenanceMessage ?? "" });
            }
            return Promise.reject(error);
        });
        return () => apiClient.interceptors.response.eject(interceptor);
    }, []);

    useEffect(() => {
        getMaintenanceStatus()
            .then(setStatus)
            // If the status cannot be loaded, the regular error handling of the pages applies.
            .catch(() => setStatus(NOT_IN_MAINTENANCE));
    }, []);

    const refresh = useCallback(async () => {
        try {
            setStatus(await getMaintenanceStatus());
        } catch {
            // Keep the last known status
        }
    }, []);

    const value = useMemo<MaintenanceContextValue>(() => ({ status, refresh }), [status, refresh]);
    return <MaintenanceContext.Provider value={value}>{children}</MaintenanceContext.Provider>;
}
