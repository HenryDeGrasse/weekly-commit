/**
 * ProactiveRiskBanner — displays critical AI-detected risk signals as
 * prominent, persistent banners on the IC's own My Week page.
 *
 * The parent (MyWeek) is responsible for only rendering this component
 * when the plan is LOCKED. Risk signals are filtered to the three critical
 * types that warrant immediate IC attention:
 *   OVERCOMMIT, BLOCKED_CRITICAL, REPEATED_CARRY_FORWARD
 *
 * Non-critical types (UNDERCOMMIT, SCOPE_VOLATILITY) are excluded — they
 * are surfaced on the Team Week risk tab instead.
 */
import { AlertTriangle, ShieldAlert, TrendingDown, RotateCcw } from "lucide-react";
import { cn } from "../../lib/utils.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { useRiskSignals } from "../../api/aiHooks.js";
import type { RiskSignal } from "../../api/aiApi.js";

// ── Critical signal type filter ───────────────────────────────────────────────

/** Signal types shown as proactive IC banners on My Week. */
const CRITICAL_SIGNAL_TYPES = new Set([
  "OVERCOMMIT",
  "BLOCKED_CRITICAL",
  "REPEATED_CARRY_FORWARD",
]);

// ── Per-type display config ───────────────────────────────────────────────────

interface SignalConfig {
  Icon: typeof AlertTriangle;
  /** Tailwind border + background classes for the banner. */
  cls: string;
  /** Short actionable hint shown below the rationale. */
  actionHint: string;
}

const SIGNAL_CONFIG: Record<string, SignalConfig> = {
  OVERCOMMIT: {
    Icon: TrendingDown,
    cls: "border-warning-border bg-warning-bg",
    actionHint: "Consider reducing scope before the week runs out.",
  },
  BLOCKED_CRITICAL: {
    Icon: ShieldAlert,
    cls: "border-danger-border bg-danger-bg",
    actionHint: "Resolve the blocked ticket or escalate to your manager.",
  },
  REPEATED_CARRY_FORWARD: {
    Icon: RotateCcw,
    cls: "border-warning-border bg-warning-bg",
    actionHint:
      "Review whether this work needs to be broken down or deprioritised.",
  },
};

// ── Individual banner ─────────────────────────────────────────────────────────

function RiskBannerItem({ signal }: { signal: RiskSignal }) {
  const config = SIGNAL_CONFIG[signal.signalType];
  const Icon = config?.Icon ?? AlertTriangle;
  const cls = config?.cls ?? "border-border bg-background";
  const actionHint = config?.actionHint;

  return (
    <div
      className={cn(
        "rounded-default border px-4 py-3 flex items-start gap-3",
        cls,
      )}
      role="alert"
      data-testid={`risk-banner-${signal.id}`}
    >
      <Icon
        className="h-4 w-4 mt-0.5 shrink-0 text-foreground"
        aria-hidden="true"
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <p
              className="m-0 text-sm font-semibold text-foreground"
              data-testid={`risk-banner-type-${signal.id}`}
            >
              {signal.signalType.replace(/_/g, " ")}
            </p>
            <p
              className="m-0 text-sm text-foreground leading-relaxed mt-0.5"
              data-testid={`risk-banner-rationale-${signal.id}`}
            >
              {signal.rationale}
            </p>
            {actionHint && (
              <p
                className="m-0 text-xs text-muted italic mt-1"
                data-testid={`risk-banner-action-${signal.id}`}
              >
                → {actionHint}
              </p>
            )}
          </div>
          {/* signal.id is the AiSuggestion PK — suitable as feedback target */}
          <AiFeedbackButtons suggestionId={signal.id} />
        </div>
      </div>
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface ProactiveRiskBannerProps {
  /** Plan UUID. The parent must only render this for LOCKED plans. */
  planId: string;
}

/**
 * Renders zero or more alert banners for critical AI risk signals.
 * Returns `null` when AI is unavailable, loading, errored, or when no
 * critical signals exist — so callers need no conditional guard.
 */
export function ProactiveRiskBanner({ planId }: ProactiveRiskBannerProps) {
  const { data, loading, error } = useRiskSignals(planId);

  // Keep the UI clean: no loading skeleton for proactive banners.
  if (loading) return null;

  // Silently degrade when AI is unavailable or the fetch failed.
  if (error || !data?.aiAvailable) return null;

  const criticalSignals = data.signals.filter((s) =>
    CRITICAL_SIGNAL_TYPES.has(s.signalType),
  );

  if (criticalSignals.length === 0) return null;

  return (
    <div
      className="flex flex-col gap-2"
      data-testid="proactive-risk-banners"
      aria-label="Active risk signals"
    >
      {criticalSignals.map((signal) => (
        <RiskBannerItem key={signal.id} signal={signal} />
      ))}
    </div>
  );
}
