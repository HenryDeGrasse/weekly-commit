/**
 * ReconcileAssistPanel — "AI Assist Reconciliation" button that pre-fills
 * likely outcomes, generates a draft summary, and suggests carry-forwards.
 *
 * Phase 3b: Wires existing ReconcileAssistService backend to the Reconcile page.
 */
import { useState, useCallback } from "react";
import { Bot, Check, Loader2, RotateCcw, ArrowRight } from "lucide-react";
import { Button } from "../ui/Button.js";
import { Badge } from "../ui/Badge.js";
import { Card, CardContent, CardHeader, CardTitle } from "../ui/Card.js";
import { cn } from "../../lib/utils.js";
import { useAiApi, useAiStatus } from "../../api/aiHooks.js";
import { useHostBridge } from "../../host/HostProvider.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import type {
  ReconcileAssistResponse,
  CommitOutcomeSuggestion,
  CarryForwardRecommendation,
} from "../../api/aiApi.js";
import type { CommitOutcome } from "../../api/planTypes.js";

const OUTCOME_COLORS: Record<string, string> = {
  ACHIEVED: "border-success-border bg-success-bg text-success font-bold",
  PARTIALLY_ACHIEVED: "border-warning-border bg-warning-bg text-warning",
  NOT_ACHIEVED: "border-danger-border bg-danger-bg text-danger",
  CANCELED: "border-border bg-muted-bg text-muted",
};

const OUTCOME_LABELS: Record<string, string> = {
  ACHIEVED: "Achieved",
  PARTIALLY_ACHIEVED: "Partial",
  NOT_ACHIEVED: "Not Achieved",
  CANCELED: "Canceled",
};

interface ReconcileAssistPanelProps {
  planId: string;
  /** Callback when the user accepts an outcome suggestion. */
  onAcceptOutcome?: ((commitId: string, outcome: CommitOutcome) => void) | undefined;
  /** Callback when the user accepts a carry-forward recommendation. */
  onAcceptCarryForward?: ((commitId: string) => void) | undefined;
  className?: string | undefined;
}

export function ReconcileAssistPanel({
  planId,
  onAcceptOutcome,
  onAcceptCarryForward,
  className,
}: ReconcileAssistPanelProps) {
  const aiApi = useAiApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const { data: aiStatus } = useAiStatus();
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ReconcileAssistResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [appliedOutcomes, setAppliedOutcomes] = useState<Set<string>>(new Set());
  const [appliedCarryForwards, setAppliedCarryForwards] = useState<Set<string>>(new Set());

  const isAvailable = aiStatus?.available ?? false;

  const handleRequest = useCallback(async () => {
    setLoading(true);
    setError(null);
    setResult(null);
    setAppliedOutcomes(new Set());
    setAppliedCarryForwards(new Set());
    try {
      const response = await aiApi.reconcileAssist({ planId, userId });
      if (!response.aiAvailable) {
        setError("AI is currently unavailable.");
        return;
      }
      setResult(response);
    } catch {
      setError("Failed to get reconciliation suggestions.");
    } finally {
      setLoading(false);
    }
  }, [aiApi, planId, userId]);

  const handleApplyOutcome = useCallback(
    (suggestion: CommitOutcomeSuggestion) => {
      onAcceptOutcome?.(suggestion.commitId, suggestion.suggestedOutcome as CommitOutcome);
      setAppliedOutcomes((prev) => new Set(prev).add(suggestion.commitId));
    },
    [onAcceptOutcome],
  );

  const handleApplyCarryForward = useCallback(
    (rec: CarryForwardRecommendation) => {
      onAcceptCarryForward?.(rec.commitId);
      setAppliedCarryForwards((prev) => new Set(prev).add(rec.commitId));
    },
    [onAcceptCarryForward],
  );

  // Not yet requested
  if (!result && !loading && !error) {
    return (
      <div className={cn("flex items-center gap-2", className)}>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => void handleRequest()}
          disabled={!isAvailable}
          className="text-primary hover:text-primary/80 border border-dashed border-primary/30"
          data-testid="reconcile-assist-btn"
          title={!isAvailable ? "AI unavailable" : "Get AI suggestions for reconciliation"}
        >
          <Bot className="h-3.5 w-3.5 mr-1.5" />
          AI Assist Reconciliation
        </Button>
      </div>
    );
  }

  // Loading
  if (loading) {
    return (
      <div
        className={cn("flex items-center gap-2 text-sm text-muted animate-pulse", className)}
        data-testid="reconcile-assist-loading"
      >
        <Loader2 className="h-4 w-4 animate-spin" />
        Analyzing your week…
      </div>
    );
  }

  // Error
  if (error) {
    return (
      <div
        className={cn(
          "flex items-center justify-between gap-2 rounded-default border border-danger/30 bg-danger/5 px-3 py-2",
          className,
        )}
        data-testid="reconcile-assist-error"
      >
        <span className="text-xs text-danger">{error}</span>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => void handleRequest()}
          className="h-6 text-xs"
          data-testid="reconcile-assist-retry"
        >
          <RotateCcw className="h-3 w-3 mr-1" />
          Retry
        </Button>
      </div>
    );
  }

  if (!result) return null;

  const hasOutcomes = result.likelyOutcomes.length > 0;
  const hasCarryForwards = result.carryForwardRecommendations.length > 0;
  const hasSummary = result.draftSummary && result.draftSummary.trim().length > 0;

  return (
    <Card className={cn("w-full", className)} data-testid="reconcile-assist-panel">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <Bot className="h-4 w-4 text-primary" />
          AI Reconciliation Suggestions
        </CardTitle>
        <div className="flex items-center gap-2">
          {result.suggestionId && (
            <AiFeedbackButtons suggestionId={result.suggestionId} />
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => void handleRequest()}
            className="h-6 text-xs"
            data-testid="reconcile-assist-refresh"
          >
            <RotateCcw className="h-3 w-3" />
          </Button>
        </div>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        {/* Draft summary */}
        {hasSummary && (
          <div data-testid="reconcile-assist-summary">
            <p className="m-0 mb-1 text-xs font-semibold text-muted uppercase tracking-wider">
              Week Summary
            </p>
            <p className="m-0 text-sm leading-relaxed text-foreground bg-background rounded-default px-3 py-2 border border-border">
              {result.draftSummary}
            </p>
          </div>
        )}

        {/* Outcome suggestions */}
        {hasOutcomes && (
          <div data-testid="reconcile-assist-outcomes">
            <p className="m-0 mb-2 text-xs font-semibold text-muted uppercase tracking-wider">
              Suggested Outcomes
            </p>
            <div className="flex flex-col gap-1.5">
              {result.likelyOutcomes.map((suggestion) => {
                const isApplied = appliedOutcomes.has(suggestion.commitId);
                const colorCls =
                  OUTCOME_COLORS[suggestion.suggestedOutcome] ?? "border-border bg-muted-bg text-muted";
                return (
                  <div
                    key={suggestion.commitId}
                    className={cn(
                      "flex items-start gap-2 rounded-sm border px-2.5 py-2 text-xs",
                      isApplied ? "border-foreground/20 bg-foreground/5" : "border-border",
                    )}
                    data-testid={`outcome-suggestion-${suggestion.commitId}`}
                  >
                    <div className="flex-1 min-w-0">
                      <span className="font-semibold">{suggestion.commitTitle}</span>
                      <div className="flex items-center gap-1.5 mt-1">
                        <Badge
                          variant="default"
                          className={cn("text-[0.6rem]", colorCls)}
                        >
                          {OUTCOME_LABELS[suggestion.suggestedOutcome] ??
                            suggestion.suggestedOutcome}
                        </Badge>
                        <span className="text-muted">{suggestion.rationale}</span>
                      </div>
                    </div>
                    {!isApplied && onAcceptOutcome && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => handleApplyOutcome(suggestion)}
                        className="h-6 w-6 text-primary hover:bg-primary/10 shrink-0"
                        aria-label={`Apply ${suggestion.suggestedOutcome} to ${suggestion.commitTitle}`}
                        data-testid={`apply-outcome-${suggestion.commitId}`}
                      >
                        <Check className="h-3.5 w-3.5" />
                      </Button>
                    )}
                    {isApplied && (
                      <span className="text-[0.6rem] text-foreground font-semibold shrink-0">
                        ✓ Applied
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Carry-forward recommendations */}
        {hasCarryForwards && (
          <div data-testid="reconcile-assist-carry-forwards">
            <p className="m-0 mb-2 text-xs font-semibold text-muted uppercase tracking-wider">
              Carry Forward Recommendations
            </p>
            <div className="flex flex-col gap-1.5">
              {result.carryForwardRecommendations.map((rec) => {
                const isApplied = appliedCarryForwards.has(rec.commitId);
                return (
                  <div
                    key={rec.commitId}
                    className={cn(
                      "flex items-start gap-2 rounded-sm border px-2.5 py-2 text-xs",
                      isApplied
                        ? "border-foreground/15 bg-foreground/[0.03]"
                        : "border-border",
                    )}
                    data-testid={`carry-forward-rec-${rec.commitId}`}
                  >
                    <ArrowRight className="h-3.5 w-3.5 mt-0.5 text-muted shrink-0" />
                    <div className="flex-1 min-w-0">
                      <span className="font-semibold">{rec.commitTitle}</span>
                      <p className="m-0 mt-0.5 text-muted">{rec.rationale}</p>
                    </div>
                    {!isApplied && onAcceptCarryForward && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => handleApplyCarryForward(rec)}
                        className="h-6 text-xs text-foreground hover:bg-foreground/8 shrink-0"
                        aria-label={`Carry forward ${rec.commitTitle}`}
                        data-testid={`apply-carry-forward-${rec.commitId}`}
                      >
                        <RotateCcw className="h-3 w-3 inline-block" aria-hidden="true" /> Carry Forward
                      </Button>
                    )}
                    {isApplied && (
                      <span className="text-[0.6rem] text-foreground font-semibold shrink-0">
                        ✓ Queued
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {!hasOutcomes && !hasCarryForwards && !hasSummary && (
          <p className="text-xs text-muted italic" data-testid="reconcile-assist-empty">
            No additional suggestions — your reconciliation looks straightforward.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
