/**
 * InsightPanel — displays AI-generated insight cards for a team week
 * (mode='team') or a personal plan (mode='personal').
 */
import { Bot } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { Skeleton } from "../ui/Skeleton.js";
import { cn } from "../../lib/utils.js";
import { AiFeedbackButtons } from "./AiFeedbackButtons.js";
import { useTeamInsights, usePlanInsights } from "../../api/ragHooks.js";
import type { InsightCard } from "../../api/ragApi.js";

// ── Severity helpers ──────────────────────────────────────────────────────────

type SeverityLevel = "HIGH" | "MEDIUM" | "LOW";

function severityBadgeVariant(
  severity: string,
): "danger" | "warning" | "primary" | "default" {
  switch (severity.toUpperCase() as SeverityLevel) {
    case "HIGH":
      return "danger";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "primary";
    default:
      return "default";
  }
}

function severityLabel(severity: string): string {
  return severity.charAt(0).toUpperCase() + severity.slice(1).toLowerCase();
}

// ── Individual card ───────────────────────────────────────────────────────────

function InsightCardItem({
  card,
  sourceHrefBase,
}: {
  card: InsightCard;
  sourceHrefBase: string;
}) {
  return (
    <div
      className={cn(
        "rounded-default border border-border bg-surface px-4 py-3 flex flex-col gap-2",
      )}
      data-testid={`insight-card-${card.suggestionId}`}
    >
      {/* Header row: severity + feedback buttons */}
      <div className="flex items-center justify-between gap-2">
        <Badge
          variant={severityBadgeVariant(card.severity)}
          className="text-[0.65rem] capitalize"
          data-testid={`insight-severity-${card.suggestionId}`}
        >
          {severityLabel(card.severity)}
        </Badge>
        <AiFeedbackButtons suggestionId={card.suggestionId} />
      </div>

      {/* Insight text */}
      <p
        className="m-0 text-sm text-foreground leading-relaxed"
        data-testid={`insight-text-${card.suggestionId}`}
      >
        {card.insightText}
      </p>

      {/* Action suggestion */}
      {card.actionSuggestion && (
        <p
          className="m-0 text-xs text-muted italic"
          data-testid={`insight-action-${card.suggestionId}`}
        >
          → {card.actionSuggestion}
        </p>
      )}

      {/* Source entity IDs */}
      {card.sourceEntityIds.length > 0 && (
        <div className="flex flex-wrap gap-1" data-testid={`insight-sources-${card.suggestionId}`}>
          {card.sourceEntityIds.map((id) => (
            <a
              key={id}
              href={`${sourceHrefBase}#${encodeURIComponent(id)}`}
              className="rounded-sm bg-muted/10 px-1.5 py-0.5 text-[0.6rem] font-mono text-muted hover:underline"
              data-testid={`insight-source-link-${card.suggestionId}-${id}`}
            >
              {id}
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Skeleton loading cards ────────────────────────────────────────────────────

function InsightSkeletons() {
  return (
    <div
      className="flex flex-col gap-3"
      data-testid="insight-panel-loading"
      aria-busy="true"
    >
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="rounded-default border border-border px-4 py-3 flex flex-col gap-2"
        >
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-4/5" />
          <Skeleton className="h-3 w-2/3" />
        </div>
      ))}
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface InsightPanelTeamProps {
  mode: "team";
  teamId?: string;
  weekStart?: string;
  planId?: never;
}

interface InsightPanelPersonalProps {
  mode: "personal";
  planId?: string;
  teamId?: never;
  weekStart?: never;
}

type InsightPanelProps = InsightPanelTeamProps | InsightPanelPersonalProps;

function TeamInsightPanel({
  teamId,
  weekStart,
}: {
  teamId?: string;
  weekStart?: string;
}) {
  const { data, loading, error } = useTeamInsights(
    teamId ?? null,
    weekStart ?? "",
  );

  if (loading) return <InsightSkeletons />;

  if (error) {
    return (
      <p
        className="text-sm text-danger"
        data-testid="insight-panel-error"
      >
        Failed to load insights: {error.message}
      </p>
    );
  }

  if (!data?.aiAvailable) {
    return (
      <div
        className="flex items-center gap-2 rounded-default border border-dashed border-border bg-background/60 px-3 py-2.5 text-xs text-muted italic"
        data-testid="insight-panel-unavailable"
      >
        <Bot className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        AI insights are currently unavailable.
      </div>
    );
  }

  if (data.insights.length === 0) {
    return (
      <p
        className="text-sm text-muted"
        data-testid="insight-panel-empty"
      >
        No AI insights available for this period.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {data.insights.map((card) => (
        <InsightCardItem
          key={card.suggestionId}
          card={card}
          sourceHrefBase={teamId ? `/team/${encodeURIComponent(teamId)}` : "/team"}
        />
      ))}
    </div>
  );
}

function PersonalInsightPanel({ planId }: { planId?: string }) {
  const { data, loading, error } = usePlanInsights(planId ?? null);

  if (loading) return <InsightSkeletons />;

  if (error) {
    return (
      <p
        className="text-sm text-danger"
        data-testid="insight-panel-error"
      >
        Failed to load insights: {error.message}
      </p>
    );
  }

  if (!data?.aiAvailable) {
    return (
      <div
        className="flex items-center gap-2 rounded-default border border-dashed border-border bg-background/60 px-3 py-2.5 text-xs text-muted italic"
        data-testid="insight-panel-unavailable"
      >
        <Bot className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        AI insights are currently unavailable.
      </div>
    );
  }

  if (data.insights.length === 0) {
    return (
      <p
        className="text-sm text-muted"
        data-testid="insight-panel-empty"
      >
        No AI insights available for this period.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {data.insights.map((card) => (
        <InsightCardItem
          key={card.suggestionId}
          card={card}
          sourceHrefBase="/my-week"
        />
      ))}
    </div>
  );
}

/**
 * InsightPanel renders AI-generated insight cards.
 *
 * Set `mode="team"` with `teamId` + `weekStart` for team-level insights, or
 * `mode="personal"` with `planId` for plan-level personal insights.
 */
export function InsightPanel(props: InsightPanelProps) {
  return (
    <div data-testid="insight-panel">
      {props.mode === "team" ? (
        <TeamInsightPanel
          {...(props.teamId !== undefined && { teamId: props.teamId })}
          {...(props.weekStart !== undefined && { weekStart: props.weekStart })}
        />
      ) : (
        <PersonalInsightPanel
          {...(props.planId !== undefined && { planId: props.planId })}
        />
      )}
    </div>
  );
}
