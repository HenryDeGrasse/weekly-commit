/**
 * React hook for theme management.
 *
 * Returns the current mode, resolved theme, and a setter.
 * Listens to OS prefers-color-scheme changes when mode is "system".
 */
import { useState, useEffect, useCallback } from "react";
import {
  type ThemeMode,
  type ResolvedTheme,
  getStoredTheme,
  setStoredTheme,
  resolveTheme,
  applyTheme,
} from "./theme.js";

export interface UseThemeReturn {
  /** User-selected mode: "light" | "dark" | "system" */
  mode: ThemeMode;
  /** Resolved theme actually applied: "light" | "dark" */
  resolved: ResolvedTheme;
  /** Change the theme mode. Persists to localStorage and applies immediately. */
  setMode: (mode: ThemeMode) => void;
  /** Cycle through: light → dark → system → light */
  toggle: () => void;
}

const CYCLE: ThemeMode[] = ["light", "dark", "system"];

export function useTheme(): UseThemeReturn {
  const [mode, setModeState] = useState<ThemeMode>(getStoredTheme);
  const [resolved, setResolved] = useState<ResolvedTheme>(() =>
    resolveTheme(getStoredTheme()),
  );

  const setMode = useCallback((next: ThemeMode) => {
    setStoredTheme(next);
    setModeState(next);
    const r = resolveTheme(next);
    setResolved(r);
    applyTheme(r);
  }, []);

  const toggle = useCallback(() => {
    setModeState((prev) => {
      const idx = CYCLE.indexOf(prev);
      const next = CYCLE[(idx + 1) % CYCLE.length]!;
      setStoredTheme(next);
      const r = resolveTheme(next);
      setResolved(r);
      applyTheme(r);
      return next;
    });
  }, []);

  // Apply on mount
  useEffect(() => {
    applyTheme(resolveTheme(mode));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Listen for OS preference changes when in "system" mode
  useEffect(() => {
    if (mode !== "system") return;
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") return;
    let mq: MediaQueryList;
    try {
      mq = window.matchMedia("(prefers-color-scheme: dark)");
    } catch {
      return; // jsdom
    }
    const handler = (e: MediaQueryListEvent) => {
      const r = e.matches ? "dark" : "light";
      setResolved(r);
      applyTheme(r);
    };
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, [mode]);

  return { mode, resolved, setMode, toggle };
}
