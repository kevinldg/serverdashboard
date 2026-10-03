export type Theme = "light" | "dark";

/** Also read by public/theme-init.js before the page renders. */
const STORAGE_KEY = "theme";
const CHANGE_EVENT = "themechange";

const systemDark = window.matchMedia("(prefers-color-scheme: dark)");

function isTheme(value: unknown): value is Theme {
    return value === "light" || value === "dark";
}

/** The chosen theme (`data-theme` on <html>), otherwise the operating system's setting. */
export function currentTheme(): Theme {
    const chosen = document.documentElement.dataset.theme;
    if (isTheme(chosen)) {
        return chosen;
    }
    return systemDark.matches ? "dark" : "light";
}

/** Applies the theme and remembers it in this browser. */
export function setTheme(theme: Theme) {
    document.documentElement.dataset.theme = theme;
    try {
        localStorage.setItem(STORAGE_KEY, theme);
    } catch {
        // Storage blocked: the theme only applies until the page is reloaded
    }
    window.dispatchEvent(new Event(CHANGE_EVENT));
}

/** Notifies about theme changes in this tab, in other tabs, and of the operating system's setting. */
export function subscribeToTheme(onChange: () => void) {
    const onStorage = (event: StorageEvent) => {
        if (event.key === STORAGE_KEY && isTheme(event.newValue)) {
            document.documentElement.dataset.theme = event.newValue;
            onChange();
        }
    };
    systemDark.addEventListener("change", onChange);
    window.addEventListener(CHANGE_EVENT, onChange);
    window.addEventListener("storage", onStorage);
    return () => {
        systemDark.removeEventListener("change", onChange);
        window.removeEventListener(CHANGE_EVENT, onChange);
        window.removeEventListener("storage", onStorage);
    };
}
