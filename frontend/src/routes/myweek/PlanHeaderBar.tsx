/**
 * Plan header card — state badge, compliance badge, action buttons.
 */
import { Link } from "react-router-dom";
import { Lock, AlertTriangle, Check, ArrowRight, Sparkles } from "lucide-react";
import { Button } from "../../components/ui/Button.js";
import { Badge } from "../../components/ui/Badge.js";
import { Card, CardHeader } from "../../components/ui/Card.js";
import type { PlanState } from "../../api/planTypes.js";

// ── PlanStateBadge ──────────────────────────────────────────────────────────

const STATE_VARIANT: Record<PlanState, "draft" | "locked" | "reconciling" | "reconciled"> = {
  DRAFT: "draft",
  LOCKED: "locked",
  RECONCILING: "reconciling",
  RECONCILED: "reconciled",
};

export function PlanStateBadge({ state }: { readonly state: PlanState }) {
  return (
    <Badge data-testid="plan-state-badge" variant={STATE_VARIANT[state]}>
      {state}
    </Badge>
  );
}

// ── ComplianceBadge ─────────────────────────────────────────────────────────

export function ComplianceBadge({ compliant }: { readonly compliant: boolean }) {
  return compliant ? (
    <span
      data-testid="compliance-badge-ok"
      title="Plan meets all lock requirements"
      className="flex items-center gap-1 text-sm font-medium text-success"
    >
      <Check className="h-3.5 w-3.5" aria-hidden="true" />
      Compliant
    </span>
  ) : (
    <span
      data-testid="compliance-badge-warn"
      title="Plan has unresolved compliance issues"
      className="flex items-center gap-1 text-sm font-medium text-warning"
    >
      <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
      Not compliant
    </span>
  );
}

// ── PlanHeaderBar ───────────────────────────────────────────────────────────

interface PlanHeaderBarProps {
  readonly plan: { id: string; state: PlanState; compliant: boolean; systemLockedWithErrors: boolean };
  readonly isDraft: boolean;
  readonly isLocked: boolean;
  readonly lockLoading: boolean;
  readonly actionError: string | null;
  readonly aiComposerEnabled: boolean;
  readonly onLockClick: () => void;
  readonly onAddCommit: () => void;
}

export function PlanHeaderBar({
  plan,
  isDraft,
  isLocked,
  lockLoading,
  actionError,
  aiComposerEnabled,
  onLockClick,
  onAddCommit,
}: PlanHeaderBarProps) {
  return (
    <>
      <Card data-testid="plan-header">
        <CardHeader>
          <div className="flex items-center gap-2.5">
            <PlanStateBadge state={plan.state} />
            <ComplianceBadge compliant={plan.compliant} />
          </div>
          <div className="flex items-center gap-2">
            {actionError && (
              <span role="alert" data-testid="plan-action-error" className="text-xs text-danger font-semibold">
                {actionError}
              </span>
            )}
            {isDraft && (
              <Button
                variant="primary"
                size="sm"
                onClick={onLockClick}
                disabled={lockLoading}
                data-testid="lock-plan-btn"
              >
                <Lock className="h-3.5 w-3.5" />
                {lockLoading ? "Locking…" : "Lock Plan"}
              </Button>
            )}
            {isLocked && (
              <Button variant="primary" size="sm" onClick={onAddCommit} data-testid="post-lock-add-commit-btn">
                {aiComposerEnabled && <Sparkles className="h-3.5 w-3.5 mr-1" aria-hidden="true" />}
                + Add Commit
              </Button>
            )}
            {(plan.state === "LOCKED" || plan.state === "RECONCILING") && (
              <Link to={`/weekly/reconcile/${plan.id}`} data-testid="reconcile-hint">
                <Button variant="primary" size="sm" type="button">
                  <ArrowRight className="h-3.5 w-3.5 mr-1" />
                  {plan.state === "RECONCILING" ? "Continue Reconciliation" : "Reconcile This Week"}
                </Button>
              </Link>
            )}
          </div>
        </CardHeader>
      </Card>

      {/* Auto-lock system banner */}
      {plan.systemLockedWithErrors && (
        <div
          data-testid="auto-lock-banner"
          role="alert"
          className="flex items-start gap-2 rounded-default border border-border bg-foreground/5 px-4 py-3 text-sm text-foreground"
        >
          <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0 text-muted" aria-hidden="true" />
          <span>
            <strong>System-locked with errors</strong> — this plan was automatically locked at the deadline. Some
            validation issues were present at lock time. Please review and reconcile.
          </span>
        </div>
      )}
    </>
  );
}
