import { useSyncExternalStore } from "react";
import { currentTheme, setTheme, subscribeToTheme } from "./theme";

export function useTheme() {
    const theme = useSyncExternalStore(subscribeToTheme, currentTheme);
    const toggleTheme = () => setTheme(theme === "dark" ? "light" : "dark");
    return { theme, toggleTheme };
}
