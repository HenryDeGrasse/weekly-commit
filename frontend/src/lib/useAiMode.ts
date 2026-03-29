import { useState, useCallback } from "react";

/**
 * Global AI suggestion mode.
 *
 * - `'full'` (default): AI sections default to their component-configured
 *   expanded state and auto-run is enabled.
 * - `'on-demand'`: All AI sections default to collapsed; auto-run is
 *   disabled. User must explicitly open each section.
 *
 * Setting persists to localStorage under `wc-ai-mode`.
 */
export type AiMode = "full" | "on-demand";

const AI_MODE_KEY = "wc-ai-mode";

export function useAiMode(): {
  aiMode: AiMode;
  setAiMode: (mode: AiMode) => void;
} {
  const [aiMode, setAiModeState] = useState<AiMode>(() => {
    try {
      const stored = localStorage.getItem(AI_MODE_KEY);
      return stored === "on-demand" ? "on-demand" : "full";
    } catch {
      return "full";
    }
  });

  const setAiMode = useCallback((mode: AiMode) => {
    setAiModeState(mode);
    try {
      localStorage.setItem(AI_MODE_KEY, mode);
    } catch {
      // Silently ignore.
    }
  }, []);

  return { aiMode, setAiMode };
}
