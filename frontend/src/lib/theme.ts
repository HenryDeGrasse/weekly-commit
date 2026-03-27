/**
 * Theme persistence and system-preference detection.
 *
 * Three modes:
 *   "light"  — force light
 *   "dark"   — force dark
 *   "system" — follow OS prefers-color-scheme
 *
 * The resolved theme ("light" | "dark") is applied as `data-theme` on <html>.
 * CSS custom properties in index.css respond to [data-theme="dark"].
 */

export type ThemeMode = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

const STORAGE_KEY = "wc-theme";

/** Read stored preference, defaulting to "system". */
export function getStoredTheme(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark" || stored === "system") {
      return stored;
    }
  } catch {
    // SSR or restricted storage
  }
  return "system";
}

/** Persist the preference. */
export function setStoredTheme(mode: ThemeMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    // ignore
  }
}

/** Resolve "system" to the actual OS preference. */
export function resolveTheme(mode: ThemeMode): ResolvedTheme {
  if (mode === "light" || mode === "dark") return mode;
  try {
    if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
      return window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";
    }
  } catch {
    // jsdom or restricted environment
  }
  return "light";
}

/** Apply the resolved theme to <html data-theme="...">. */
export function applyTheme(resolved: ResolvedTheme): void {
  if (typeof document === "undefined") return;
  document.documentElement.setAttribute("data-theme", resolved);
}
