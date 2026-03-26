/**
 * Risk signals panel — displays AI-detected and rules-based risk signals
 * for a plan. Used on the Team Week page's risk tab.
 */
import { AlertTriangle, ShieldAlert, TrendingDown, RotateCcw, Zap, Activity } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { Skeleton } from "../ui/Skeleton.js";
import { cn } from "../../lib/utils.js";
import { useRiskSignals } from "../../api/aiHooks.js";
import type { RiskSignal } from "../../api/aiApi.js";

const SIGNAL_ICONS: Record<string, typeof AlertTriangle> = {
  OVERCOMMIT: TrendingDown,
  UNDERCOMMIT: TrendingDown,
  REPEATED_CARRY_FORWARD: RotateCcw,
  BLOCKED_CRITICAL: ShieldAlert,
  SCOPE_VOLATILITY: Zap,
};

const SIGNAL_CLS: Record<string, string> = {
  OVERCOMMIT: "border-red-200 bg-red-50 text-red-800",
  UNDERCOMMIT: "border-amber-200 bg-amber-50 text-amber-800",
  REPEATED_CARRY_FORWARD: "border-blue-200 bg-blue-50 text-blue-800",
  BLOCKED_CRITICAL: "border-red-200 bg-red-50 text-red-800",
  SCOPE_VOLATILITY: "border-violet-200 bg-violet-50 text-violet-800",
};

function SignalCard({ signal }: { signal: RiskSignal }) {
  const Icon = SIGNAL_ICONS[signal.signalType] ?? Activity;
  const cls = SIGNAL_CLS[signal.signalType] ?? "border-border bg-background text-foreground";

  return (
    <div className={cn("rounded-default border px-3 py-2.5 flex items-start gap-2.5", cls)} data-testid={`risk-signal-${signal.id}`}>
      <Icon className="h-4 w-4 mt-0.5 shrink-0" aria-hidden="true" />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <Badge variant="default" className="text-[0.6rem]">{signal.signalType}</Badge>
          <span className="text-[0.65rem] text-muted">
            {new Date(signal.createdAt).toLocaleDateString()}
          </span>
        </div>
        <p className="m-0 text-xs">{signal.rationale}</p>
      </div>
    </div>
  );
}

interface RiskSignalsPanelProps {
  planId: string | null;
}

export function RiskSignalsPanel({ planId }: RiskSignalsPanelProps) {
  const { data, loading, error } = useRiskSignals(planId);

  if (!planId) {
    return (
      <div className="text-sm text-muted py-4 text-center" data-testid="risk-signals-no-plan">
        Select a member's plan to view risk signals.
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-2" data-testid="risk-signals-loading">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-sm text-danger" data-testid="risk-signals-error">
        Failed to load risk signals: {error.message}
      </div>
    );
  }

  if (!data?.aiAvailable) {
    return (
      <div className="text-sm text-muted italic" data-testid="risk-signals-unavailable">
        Risk detection is currently unavailable.
      </div>
    );
  }

  if (data.signals.length === 0) {
    return (
      <div className="flex items-center gap-2 text-sm text-success py-4" data-testid="risk-signals-clear">
        <ShieldAlert className="h-4 w-4" />
        No risk signals detected for this plan.
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2" data-testid="risk-signals-list">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted uppercase tracking-wider">
          {data.signals.length} Risk Signal{data.signals.length !== 1 ? "s" : ""}
        </span>
      </div>
      {data.signals.map((signal) => (
        <SignalCard key={signal.id} signal={signal} />
      ))}
    </div>
  );
}
