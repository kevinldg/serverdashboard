import { createContext } from "react";
import type { PublicMaintenanceStatus } from "../api/maintenance";

export interface MaintenanceContextValue {
    /** Null while the initial check is running. */
    status: PublicMaintenanceStatus | null;
    refresh: () => Promise<void>;
}

export const MaintenanceContext = createContext<MaintenanceContextValue | null>(null);
