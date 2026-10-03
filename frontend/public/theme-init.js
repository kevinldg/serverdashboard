// Applies the saved theme before the page renders, so it does not flash in the wrong colors.
// Without a saved choice, the CSS follows the operating system's setting. See src/theme/theme.ts.
try {
    var theme = localStorage.getItem("theme");
    if (theme === "light" || theme === "dark") {
        document.documentElement.dataset.theme = theme;
    }
} catch (e) {
    // Storage blocked: the operating system's setting applies
}
