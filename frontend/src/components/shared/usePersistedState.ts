import { useState, useEffect } from "react";

/**
 * Generic hook for localStorage-backed state.
 *
 * Reads the persisted value from localStorage on first render; falls back to
 * `defaultValue` when the key is absent or the stored value can't be parsed.
 * Writes back to localStorage whenever the value changes.
 *
 * Usage:
 *   const [open, setOpen] = usePersistedState("wc-section-lint", true);
 */
export function usePersistedState<T>(
  key: string,
  defaultValue: T,
): [T, React.Dispatch<React.SetStateAction<T>>] {
  const [state, setState] = useState<T>(() => {
    try {
      const stored = localStorage.getItem(key);
      if (stored === null) return defaultValue;
      return JSON.parse(stored) as T;
    } catch {
      return defaultValue;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(state));
    } catch {
      // Silently ignore — localStorage may be unavailable (private browsing, etc.)
    }
  }, [key, state]);

  return [state, setState];
}
