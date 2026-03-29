/**
 * ManagerAiSummaryCard — AI-generated weekly team summary displayed
 * prominently at the top of Team Week for managers.
 *
 * Shows the AI prose summary, top RCDO branches, carry-forward patterns,
 * and critical blocked item / unresolved exception counts. Every piece of
 * data cites underlying domain objects so the summary is never a black box.
 */
import { useEffect } from "react";
import { Bot, AlertTriangle, RotateCcw, ShieldAlert } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { Skeleton } from "../ui/Skeleton.js";
import { Card, CardHeader, CardContent } from "../ui/Card.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { useManagerAiSummary } from "../../api/aiHooks.js";

interface ManagerAiSummaryCardProps {
  teamId: string;
  weekStart: string;
  /**
   * Called once the summary loads with the first sentence of the prose
   * summary (useful for the parent to show a preview badge in a
   * CollapsibleSection header). Called with null when unavailable/errored.
   */
  onSummaryText?: ((text: string | null) => void) | undefined;
}

// ── Loading skeleton ──────────────────────────────────────────────────────────

function ManagerAiSummarySkeleton() {
  return (
    <Card data-testid="manager-ai-summary-loading">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Skeleton className="h-4 w-4 rounded-full" />
          <Skeleton className="h-4 w-32" />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-5/6" />
        <Skeleton className="h-4 w-4/5" />
        <div className="flex gap-2 mt-1">
          <Skeleton className="h-5 w-20 rounded-sm" />
          <Skeleton className="h-5 w-24 rounded-sm" />
          <Skeleton className="h-5 w-16 rounded-sm" />
        </div>
      </CardContent>
    </Card>
  );
}

// ── Main card ─────────────────────────────────────────────────────────────────

export function ManagerAiSummaryCard({ teamId, weekStart, onSummaryText }: ManagerAiSummaryCardProps) {
  const { data, loading, error } = useManagerAiSummary(teamId, weekStart);

  // Report first-sentence preview to parent for badge use.
  useEffect(() => {
    if (loading) return;
    if (error || !data?.aiAvailable || !data?.summaryText) {
      onSummaryText?.(null);
      return;
    }
    const firstSentence = data.summaryText.split(/[.!?]/)[0]?.trim() ?? null;
    onSummaryText?.(firstSentence);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, data?.summaryText, error]);

  if (loading) {
    return <ManagerAiSummarySkeleton />;
  }

  if (error) {
    return (
      <div
        className="flex items-center gap-2 rounded-default border border-dashed border-border bg-background/60 px-3 py-2.5 text-xs text-muted italic"
        data-testid="manager-ai-summary-error"
      >
        <Bot className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        AI team summary unavailable: {error.message}
      </div>
    );
  }

  if (!data || !data.aiAvailable) {
    return (
      <div
        className="flex items-center gap-2 rounded-default border border-dashed border-border bg-background/60 px-3 py-2.5 text-xs text-muted italic"
        data-testid="manager-ai-summary-unavailable"
      >
        <Bot className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        AI team summary is currently unavailable.
      </div>
    );
  }

  const hasTopRcdo = data.topRcdoBranches.length > 0;
  const hasCarryForward = data.carryForwardPatterns.length > 0;
  const hasExceptions = data.unresolvedExceptionIds.length > 0;
  const hasCriticalBlocked = data.criticalBlockedItemIds.length > 0;

  return (
    <Card data-testid="manager-ai-summary-card">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Bot className="h-4 w-4 text-primary shrink-0" aria-hidden="true" />
          <span className="text-sm font-semibold text-foreground">AI Team Summary</span>
        </div>
        {data.suggestionId && (
          <AiFeedbackButtons suggestionId={data.suggestionId} />
        )}
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {/* Prose summary */}
        {data.summaryText && (
          <p
            className="m-0 text-sm leading-relaxed text-foreground"
            data-testid="manager-ai-summary-text"
          >
            {data.summaryText}
          </p>
        )}

        {/* Top RCDO branches */}
        {hasTopRcdo && (
          <div data-testid="manager-ai-summary-rcdo-branches">
            <p className="m-0 mb-1.5 text-[0.65rem] font-semibold uppercase tracking-wider text-muted">
              Top Strategic Branches
            </p>
            <div className="flex flex-wrap gap-1.5">
              {data.topRcdoBranches.map((branch) => (
                <Badge key={branch} variant="primary" className="text-[0.65rem]">
                  {branch}
                </Badge>
              ))}
            </div>
          </div>
        )}

        {/* Carry-forward patterns */}
        {hasCarryForward && (
          <div data-testid="manager-ai-summary-carry-forward">
            <p className="m-0 mb-1 text-[0.65rem] font-semibold uppercase tracking-wider text-muted flex items-center gap-1">
              <RotateCcw className="h-3 w-3" aria-hidden="true" />
              Carry-Forward Patterns
            </p>
            <ul className="m-0 pl-4 flex flex-col gap-0.5">
              {data.carryForwardPatterns.map((pattern, i) => (
                <li key={i} className="text-xs text-foreground">
                  {pattern}
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Warnings row: unresolved exceptions + critical blocked items */}
        {(hasExceptions || hasCriticalBlocked) && (
          <div className="flex flex-wrap gap-3 mt-0.5" data-testid="manager-ai-summary-warnings">
            {hasExceptions && (
              <span
                className="flex items-center gap-1.5 text-xs font-semibold text-foreground"
                data-testid="manager-ai-summary-exception-count"
              >
                <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                {data.unresolvedExceptionIds.length} unresolved exception
                {data.unresolvedExceptionIds.length !== 1 ? "s" : ""}
              </span>
            )}
            {hasCriticalBlocked && (
              <span
                className="flex items-center gap-1.5 text-xs font-semibold text-foreground"
                data-testid="manager-ai-summary-blocked-count"
              >
                <ShieldAlert className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                {data.criticalBlockedItemIds.length} critical blocked item
                {data.criticalBlockedItemIds.length !== 1 ? "s" : ""}
              </span>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
