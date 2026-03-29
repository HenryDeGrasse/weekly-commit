import { useState, useCallback } from "react";

/**
 * Tracks a set of dismissed item IDs in localStorage.
 *
 * Stores the set under {@code wc-dismissed-<key>} as a JSON array.
 * Returns the current set of dismissed IDs and a function to add a new one.
 *
 * Usage:
 *   const { dismissedIds, dismiss } = useDismissedIds("plan-recommendations");
 *   const visible = items.filter((i) => !dismissedIds.has(i.id));
 */
export function useDismissedIds(key: string): {
  dismissedIds: Set<string>;
  dismiss: (id: string) => void;
} {
  const storageKey = `wc-dismissed-${key}`;

  const [dismissed, setDismissed] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      if (!raw) return new Set<string>();
      const parsed = JSON.parse(raw) as unknown;
      if (Array.isArray(parsed)) return new Set<string>(parsed as string[]);
      return new Set<string>();
    } catch {
      return new Set<string>();
    }
  });

  const dismiss = useCallback(
    (id: string) => {
      setDismissed((prev) => {
        const next = new Set(prev);
        next.add(id);
        try {
          localStorage.setItem(storageKey, JSON.stringify([...next]));
        } catch {
          // Silently ignore — localStorage may be unavailable.
        }
        return next;
      });
    },
    [storageKey],
  );

  return { dismissedIds: dismissed, dismiss };
}
