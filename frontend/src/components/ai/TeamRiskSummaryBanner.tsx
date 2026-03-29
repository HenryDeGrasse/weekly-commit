/**
 * TeamRiskSummaryBanner — aggregates critical risk signals across all team
 * members' plans and renders them as prominent banners at the top of Team Week.
 *
 * Architecture: Each member plan gets a `<PlanRiskRow>` that calls
 * `useRiskSignals`. This avoids the "hooks in a loop" problem while still
 * fetching signals for every plan in parallel.
 *
 * Returns null (per-row) when no critical signals exist or AI is unavailable.
 */
import { AlertTriangle, ShieldAlert, TrendingDown, RotateCcw } from "lucide-react";
import { cn } from "../../lib/utils.js";
import { useRiskSignals } from "../../api/aiHooks.js";
import type { RiskSignal } from "../../api/aiApi.js";

// ── Config ────────────────────────────────────────────────────────────────────

const CRITICAL_SIGNAL_TYPES = new Set([
  "OVERCOMMIT",
  "BLOCKED_CRITICAL",
  "REPEATED_CARRY_FORWARD",
]);

interface SignalConfig {
  Icon: typeof AlertTriangle;
  cls: string;
}

const SIGNAL_CONFIG: Record<string, SignalConfig> = {
  OVERCOMMIT: { Icon: TrendingDown, cls: "border-warning-border bg-warning-bg" },
  BLOCKED_CRITICAL: { Icon: ShieldAlert, cls: "border-danger-border bg-danger-bg" },
  REPEATED_CARRY_FORWARD: { Icon: RotateCcw, cls: "border-warning-border bg-warning-bg" },
};

// ── Single signal banner ──────────────────────────────────────────────────────

function TeamRiskBannerItem({
  signal,
  displayName,
}: {
  signal: RiskSignal;
  displayName: string;
}) {
  const config = SIGNAL_CONFIG[signal.signalType];
  const Icon = config?.Icon ?? AlertTriangle;
  const cls = config?.cls ?? "border-border bg-background";

  return (
    <div
      className={cn("rounded-default border px-3 py-2.5 flex items-start gap-2.5", cls)}
      role="alert"
      data-testid={`team-risk-signal-${signal.id}`}
    >
      <Icon className="h-4 w-4 mt-0.5 shrink-0 text-foreground" aria-hidden="true" />
      <div className="flex-1 min-w-0">
        <p className="m-0 text-sm text-foreground">
          <span className="font-semibold">{displayName}</span>
          {" — "}
          <span className="font-semibold">{signal.signalType.replace(/_/g, " ")}</span>
        </p>
        <p className="m-0 text-xs text-muted mt-0.5">{signal.rationale}</p>
      </div>
    </div>
  );
}

// ── Per-plan row (each one is its own hook call) ──────────────────────────────

function PlanRiskRow({
  planId,
  displayName,
}: {
  planId: string;
  displayName: string;
}) {
  const { data, loading } = useRiskSignals(planId);

  if (loading || !data?.aiAvailable) return null;

  const criticalSignals = data.signals.filter((s) =>
    CRITICAL_SIGNAL_TYPES.has(s.signalType),
  );

  if (criticalSignals.length === 0) return null;

  return (
    <>
      {criticalSignals.map((signal) => (
        <TeamRiskBannerItem
          key={signal.id}
          signal={signal}
          displayName={displayName}
        />
      ))}
    </>
  );
}

// ── Main wrapper ──────────────────────────────────────────────────────────────

interface MemberPlan {
  planId: string;
  displayName: string;
}

interface TeamRiskSummaryBannerProps {
  /** List of { planId, displayName } for each team member with a locked plan. */
  memberPlans: MemberPlan[];
}

/**
 * Renders aggregated critical risk signals for the entire team.
 * Each member's plan is fetched independently via `useRiskSignals`.
 */
export function TeamRiskSummaryBanner({ memberPlans }: TeamRiskSummaryBannerProps) {
  if (memberPlans.length === 0) return null;

  return (
    <div
      className="flex flex-col gap-1.5"
      data-testid="team-risk-summary-banners"
      aria-label="Team risk signals"
    >
      {memberPlans.map((mp) => (
        <PlanRiskRow
          key={mp.planId}
          planId={mp.planId}
          displayName={mp.displayName}
        />
      ))}
    </div>
  );
}
