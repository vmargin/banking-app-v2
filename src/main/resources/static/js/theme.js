(() => {
    const preferenceKey = "nexa-bank-theme";
    const legacyPreferenceKey = "cash-g-theme";
    const root = document.documentElement;

    function readSavedTheme() {
        try {
            const savedTheme = window.localStorage.getItem(preferenceKey)
                || window.localStorage.getItem(legacyPreferenceKey);
            return savedTheme === "light" || savedTheme === "dark" ? savedTheme : null;
        } catch {
            return null;
        }
    }

    function applyTheme(theme, persist = false) {
        root.dataset.theme = theme;
        document.querySelectorAll("[data-theme-toggle]").forEach((toggle) => {
            toggle.setAttribute("aria-checked", String(theme === "light"));
        });

        if (persist) {
            try {
                window.localStorage.setItem(preferenceKey, theme);
            } catch {
                // The current page still changes theme if browser storage is unavailable.
            }
        }
    }

    applyTheme(readSavedTheme() || root.dataset.theme || "dark");

    function connectThemeToggle() {
        document.querySelectorAll("[data-theme-toggle]").forEach((toggle) => {
            toggle.addEventListener("click", () => {
                const nextTheme = root.dataset.theme === "light" ? "dark" : "light";
                applyTheme(nextTheme, true);
            });
        });
        applyTheme(root.dataset.theme || "dark");
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", connectThemeToggle, { once: true });
    } else {
        connectThemeToggle();
    }
})();
