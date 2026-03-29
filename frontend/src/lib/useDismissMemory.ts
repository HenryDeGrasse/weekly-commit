import { useState, useCallback } from "react";

/**
 * Tracks how many times a user has dismissed a specific AI component.
 *
 * After `threshold` consecutive dismissals the hook reports
 * `shouldAutoCollapse: true` so callers can default the component to
 * collapsed without the user having to dismiss it again.
 *
 * Usage:
 *   const { shouldAutoCollapse, recordDismiss } = useDismissMemory("ai-lint");
 */
export function useDismissMemory(
  componentId: string,
  threshold = 3,
): { shouldAutoCollapse: boolean; recordDismiss: () => void } {
  const key = `wc-dismiss-${componentId}`;

  const [count, setCount] = useState<number>(() => {
    try {
      return parseInt(localStorage.getItem(key) ?? "0", 10) || 0;
    } catch {
      return 0;
    }
  });

  const shouldAutoCollapse = count >= threshold;

  const recordDismiss = useCallback(() => {
    const next = count + 1;
    setCount(next);
    try {
      localStorage.setItem(key, String(next));
    } catch {
      // Silently ignore — localStorage may be unavailable.
    }
  }, [count, key]);

  return { shouldAutoCollapse, recordDismiss };
}
