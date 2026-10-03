import { useContext } from "react";
import { MaintenanceContext, type MaintenanceContextValue } from "./MaintenanceContext";

export function useMaintenance(): MaintenanceContextValue {
    const context = useContext(MaintenanceContext);
    if (!context) {
        throw new Error("useMaintenance must be used within a MaintenanceProvider");
    }
    return context;
}
