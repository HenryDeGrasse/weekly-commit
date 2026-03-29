/**
 * PlanRecommendationCard — renders a single AI-generated plan recommendation.
 *
 * Shows:
 * - Risk type badge (colored by severity)
 * - Description and suggested action
 * - What-if narrative with before/after numbers (when available)
 * - Confidence tier via ConfidenceBadge
 * - "Apply suggestion" button (shows a confirmation dialog — actual mutation
 *   is out of scope; the dialog is informational only)
 * - AiFeedbackButtons using the stable suggestionId for feedback tracking
 * - Dismiss button that persists the dismissal in localStorage
 */
import { useState, useCallback } from "react";
import { X, TrendingDown, ShieldAlert, RotateCcw, Sparkles, ArrowRight } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "../ui/Card.js";
import { Badge } from "../ui/Badge.js";
import { Button } from "../ui/Button.js";
import {
  Dialog,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "../ui/Dialog.js";
import { ConfidenceBadge } from "./ConfidenceBadge.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { cn } from "../../lib/utils.js";
import type { PlanRecommendation } from "../../api/recommendationApi.js";

// ── Risk type display config ──────────────────────────────────────────────────

interface RiskConfig {
  Icon: typeof TrendingDown;
  variant: "warning" | "danger" | "default";
  label: string;
}

const RISK_CONFIG: Record<string, RiskConfig> = {
  OVERCOMMIT: {
    Icon: TrendingDown,
    variant: "warning",
    label: "Overcommit",
  },
  BLOCKED_CRITICAL: {
    Icon: ShieldAlert,
    variant: "danger",
    label: "Blocked Critical",
  },
  REPEATED_CARRY_FORWARD: {
    Icon: RotateCcw,
    variant: "warning",
    label: "Repeated Carry-Forward",
  },
  UNDERCOMMIT: {
    Icon: TrendingDown,
    variant: "default",
    label: "Undercommit",
  },
  SCOPE_VOLATILITY: {
    Icon: Sparkles,
    variant: "warning",
    label: "Scope Volatility",
  },
};

function getRiskConfig(riskType: string): RiskConfig {
  return (
    RISK_CONFIG[riskType] ?? {
      Icon: Sparkles,
      variant: "default" as const,
      label: riskType.replace(/_/g, " "),
    }
  );
}

// ── What-If summary ───────────────────────────────────────────────────────────

function WhatIfSummary({
  whatIfResult,
}: {
  whatIfResult: NonNullable<PlanRecommendation["whatIfResult"]>;
}) {
  const { currentState, projectedState, capacityDelta, riskDelta } = whatIfResult;

  if (!currentState || !projectedState) return null;

  const pointsBefore = currentState.totalPoints;
  const pointsAfter = projectedState.totalPoints;
  const resolvedRisks = riskDelta?.resolvedRisks ?? [];
  const newRisks = riskDelta?.newRisks ?? [];

  return (
    <div
      className="mt-3 rounded-sm border border-border bg-muted-bg px-3 py-2.5 text-xs flex flex-col gap-1.5"
      data-testid="what-if-summary"
    >
      <p className="font-semibold text-foreground m-0">What-if simulation</p>

      <div className="flex items-center gap-4">
        <span className="text-muted" data-testid="what-if-points-before">
          Before: <strong className="text-foreground">{pointsBefore} pts</strong>
        </span>
        <ArrowRight className="h-3 w-3 text-muted shrink-0" aria-hidden="true" />
        <span className="text-muted" data-testid="what-if-points-after">
          After: <strong className="text-foreground">{pointsAfter} pts</strong>
        </span>
        {capacityDelta !== 0 && (
          <span
            className={cn(
              "font-semibold",
              capacityDelta < 0 ? "text-success" : "text-warning",
            )}
            data-testid="what-if-delta"
          >
            ({capacityDelta > 0 ? "+" : ""}
            {capacityDelta})
          </span>
        )}
      </div>

      {resolvedRisks.length > 0 && (
        <p className="text-success m-0" data-testid="what-if-resolved-risks">
          ✓ Resolves: {resolvedRisks.join(", ")}
        </p>
      )}
      {newRisks.length > 0 && (
        <p className="text-warning m-0" data-testid="what-if-new-risks">
          ⚠ Introduces: {newRisks.join(", ")}
        </p>
      )}
    </div>
  );
}

// ── Apply suggestion dialog ───────────────────────────────────────────────────

function ApplySuggestionDialog({
  riskType,
  suggestedAction,
  onClose,
}: {
  riskType: string;
  suggestedAction: string;
  onClose: () => void;
}) {
  return (
    <Dialog
      open
      onClose={onClose}
      aria-label="Apply suggestion"
      data-testid="apply-suggestion-dialog"
    >
      <DialogHeader>
        <DialogTitle>Apply suggestion</DialogTitle>
        <DialogDescription>
          <strong>{riskType.replace(/_/g, " ")}</strong> — {suggestedAction}
        </DialogDescription>
      </DialogHeader>
      <p className="text-sm text-muted mt-2 mb-0">
        Review the suggested action above. Apply changes manually to your
        plan, then refresh recommendations to see updated guidance.
      </p>
      <DialogFooter>
        <Button variant="secondary" onClick={onClose} data-testid="apply-suggestion-close-btn">
          Close
        </Button>
      </DialogFooter>
    </Dialog>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface PlanRecommendationCardProps {
  recommendation: PlanRecommendation;
  /** Called when the user dismisses this card. */
  onDismiss: (suggestionId: string) => void;
  className?: string;
}

export function PlanRecommendationCard({
  recommendation,
  onDismiss,
  className,
}: PlanRecommendationCardProps) {
  const [showApplyDialog, setShowApplyDialog] = useState(false);

  const {
    suggestionId,
    riskType,
    description,
    suggestedAction,
    whatIfResult,
    narrative,
    confidence,
  } = recommendation;

  const config = getRiskConfig(riskType);
  const Icon = config.Icon;

  const handleDismiss = useCallback(() => {
    onDismiss(suggestionId);
  }, [onDismiss, suggestionId]);

  return (
    <>
      <Card
        className={cn("w-full", className)}
        data-testid={`recommendation-card-${suggestionId}`}
      >
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm flex-wrap">
            <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
            <Badge
              variant={config.variant}
              data-testid={`recommendation-risk-badge-${suggestionId}`}
            >
              {config.label}
            </Badge>
            <ConfidenceBadge
              tier={confidence ?? "INSUFFICIENT"}
              className="ml-auto"
            />
          </CardTitle>
          <div className="flex items-center gap-1 shrink-0">
            <AiFeedbackButtons suggestionId={suggestionId} />
            <Button
              variant="ghost"
              size="icon"
              onClick={handleDismiss}
              aria-label="Dismiss recommendation"
              className="h-6 w-6 text-muted hover:text-foreground"
              data-testid={`recommendation-dismiss-btn-${suggestionId}`}
            >
              <X className="h-3.5 w-3.5" />
            </Button>
          </div>
        </CardHeader>

        <CardContent className="flex flex-col gap-2">
          {/* Risk description */}
          <p
            className="text-sm text-foreground m-0"
            data-testid={`recommendation-description-${suggestionId}`}
          >
            {description}
          </p>

          {/* Suggested action */}
          <p
            className="text-sm text-muted italic m-0"
            data-testid={`recommendation-action-${suggestionId}`}
          >
            → {suggestedAction}
          </p>

          {/* What-if simulation (when available) */}
          {whatIfResult?.available && (
            <WhatIfSummary whatIfResult={whatIfResult} />
          )}

          {/* LLM narrative (when present) */}
          {narrative && (
            <p
              className="text-xs text-muted m-0 mt-1 leading-relaxed"
              data-testid={`recommendation-narrative-${suggestionId}`}
            >
              {narrative}
            </p>
          )}

          {/* Apply suggestion button */}
          <div className="flex justify-end mt-1">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setShowApplyDialog(true)}
              data-testid={`recommendation-apply-btn-${suggestionId}`}
            >
              Apply suggestion
            </Button>
          </div>
        </CardContent>
      </Card>

      {showApplyDialog && (
        <ApplySuggestionDialog
          riskType={riskType}
          suggestedAction={suggestedAction}
          onClose={() => setShowApplyDialog(false)}
        />
      )}
    </>
  );
}
