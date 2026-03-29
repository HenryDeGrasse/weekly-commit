/**
 * AI commit lint panel — replaces the placeholder in PreLockValidationPanel.
 * Calls POST /api/ai/commit-lint and displays hard + soft lint messages
 * with feedback buttons on each.
 *
 * When `autoRun` is true the lint API is called automatically on mount and
 * whenever `planId` changes — no button click required.  The manual refresh
 * button is still shown inside the results view so users can re-run after
 * editing commits.
 */
import { useState, useCallback, useEffect, useRef } from "react";
import { Skeleton } from "../ui/Skeleton.js";
import { Bot, RefreshCw } from "lucide-react";
import { Button } from "../ui/Button.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { useAiApi, useAiStatus } from "../../api/aiHooks.js";
import type { CommitLintResponse } from "../../api/aiApi.js";

interface AiLintPanelProps {
  planId: string;
  userId: string;
  /** When true, lint fires automatically on mount and on planId change (no button click required). */
  autoRun?: boolean;
  /**
   * Monotonically increasing counter. Every time this value changes the lint
   * re-fires (debounced). Callers should increment it after any commit
   * create / edit / delete / reorder operation so lint results stay current.
   */
  refreshKey?: number;
  /** Optional callback used by parents to display a collapsed summary badge. */
  onHintCountChange?: (count: number | null) => void;
}

export function AiLintPanel({ planId, userId, autoRun = false, refreshKey = 0, onHintCountChange }: AiLintPanelProps) {
  const aiApi = useAiApi();
  const { data: status } = useAiStatus();
  const [lintResult, setLintResult] = useState<CommitLintResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isAvailable = status?.available ?? false;

  useEffect(() => {
    if (!onHintCountChange) return;

    if (loading || error || !lintResult?.aiAvailable) {
      onHintCountChange(null);
      return;
    }

    onHintCountChange(
      lintResult.hardValidation.length + lintResult.softGuidance.length,
    );
  }, [error, lintResult, loading, onHintCountChange]);

  const runLint = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await aiApi.commitLint({ userId, planId });
      setLintResult(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Lint failed");
    } finally {
      setLoading(false);
    }
  }, [aiApi, planId, userId]);

  // Auto-run: fire lint on mount (and whenever planId changes) when AI is available.
  // Guard on isAvailable so we don't kick off a doomed request when AI is down.
  useEffect(() => {
    if (!autoRun || !isAvailable) return;
    void runLint();
  }, [autoRun, planId, isAvailable, runLint]);

  // Re-run with debounce when refreshKey changes (after commit CRUD operations).
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const prevRefreshKeyRef = useRef(refreshKey);
  useEffect(() => {
    // Skip the initial mount — the effect above already covers that.
    if (refreshKey === prevRefreshKeyRef.current) return;
    prevRefreshKeyRef.current = refreshKey;
    if (!autoRun || !isAvailable) return;

    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      void runLint();
    }, 800);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [refreshKey, autoRun, isAvailable, runLint]);

  // AI not available — show disabled state
  if (status && !status.available) {
    return (
      <div className="flex items-center gap-2 rounded-default border border-dashed border-border bg-background/60 px-3 py-2.5 text-xs text-muted italic" data-testid="ai-lint-unavailable">
        <Bot className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        AI commit quality hints unavailable ({status.providerName})
      </div>
    );
  }

  // Not yet run:
  //   - autoRun=false  → show the manual trigger button
  //   - autoRun=true   → lint is about to fire (or waiting for status); render nothing
  //                       so there is no flash of a button that immediately disappears
  if (!lintResult && !loading) {
    if (autoRun) {
      return null;
    }
    return (
      <div className="flex items-center gap-2" data-testid="ai-lint-ready">
        <Button variant="ghost" size="sm" onClick={() => void runLint()} className="h-7 text-xs border border-dashed border-border" data-testid="ai-lint-run-btn">
          <Bot className="h-3.5 w-3.5" />
          Run AI Quality Check
        </Button>
      </div>
    );
  }

  // Loading — shimmer skeleton with message
  if (loading) {
    return (
      <div className="flex flex-col gap-2" data-testid="ai-lint-loading">
        <div className="flex items-center gap-2 text-xs text-muted animate-pulse">
          <Bot className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          <span>Analyzing commit quality…</span>
        </div>
        <Skeleton className="h-3.5 w-full" />
        <Skeleton className="h-3.5 w-4/5" />
        <Skeleton className="h-3.5 w-3/5" />
      </div>
    );
  }

  // Error
  if (error) {
    return (
      <div className="flex items-center gap-2 text-xs text-danger" data-testid="ai-lint-error">
        <Bot className="h-3.5 w-3.5 shrink-0" />
        {error}
        <Button variant="ghost" size="sm" onClick={() => void runLint()} className="h-6 text-xs">
          <RefreshCw className="h-3 w-3" />Retry
        </Button>
      </div>
    );
  }

  // Results
  if (!lintResult?.aiAvailable) {
    return (
      <div className="text-xs text-muted italic" data-testid="ai-lint-unavailable">
        <Bot className="h-3.5 w-3.5 inline mr-1" />AI lint unavailable
      </div>
    );
  }

  const hasHard = lintResult.hardValidation.length > 0;
  const hasSoft = lintResult.softGuidance.length > 0;

  return (
    <div className="flex flex-col gap-2" data-testid="ai-lint-results">
      {/* Header with refresh */}
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-xs font-semibold text-muted">
          <Bot className="h-3.5 w-3.5" />AI Quality Check
        </span>
        <div className="flex items-center gap-1.5">
          {lintResult.suggestionId && <AiFeedbackButtons suggestionId={lintResult.suggestionId} />}
          <Button variant="ghost" size="sm" onClick={() => void runLint()} className="h-6 text-xs" data-testid="ai-lint-refresh">
            <RefreshCw className="h-3 w-3" />
          </Button>
        </div>
      </div>

      {/* Hard validations */}
      {hasHard && (
        <div className="rounded-sm border border-neutral-300 bg-neutral-100 px-3 py-2" data-testid="ai-lint-hard">
          <p className="m-0 mb-1 text-xs font-bold text-foreground underline">Hard Issues</p>
          <ul className="m-0 pl-4 flex flex-col gap-0.5">
            {lintResult.hardValidation.map((msg, i) => (
              <li key={i} className="text-xs text-foreground">{msg.message}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Soft guidance */}
      {hasSoft && (
        <div className="rounded-sm border border-neutral-200 bg-neutral-50 px-3 py-2" data-testid="ai-lint-soft">
          <p className="m-0 mb-1 text-xs font-semibold text-muted">Suggestions</p>
          <ul className="m-0 pl-4 flex flex-col gap-0.5">
            {lintResult.softGuidance.map((msg, i) => (
              <li key={i} className="text-xs text-muted">{msg.message}</li>
            ))}
          </ul>
        </div>
      )}

      {/* All clear */}
      {!hasHard && !hasSoft && (
        <div className="text-xs text-foreground flex items-center gap-1" data-testid="ai-lint-clear">
          ✓ No quality issues detected
        </div>
      )}
    </div>
  );
}
