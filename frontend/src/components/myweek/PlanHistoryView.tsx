/**
 * PlanHistoryView — week-by-week plan history for the current user.
 *
 * Each row shows: week start, plan state, compliance, commit count,
 * total planned/achieved points, carry-forward count.
 * Clicking a row expands to show full plan detail via linked navigation.
 */
import { useState } from "react";
import type { WeeklyPlanHistoryEntry } from "../../api/ticketTypes.js";
import type { PlanState } from "../../api/planTypes.js";

export interface PlanHistoryViewProps {
  readonly entries: WeeklyPlanHistoryEntry[];
  readonly loading: boolean;
  /** Called when user wants to navigate to a historical plan. */
  readonly onViewPlan?: (planId: string, weekStart: string) => void;
}

const STATE_BADGE_COLORS: Record<
  PlanState,
  { background: string; color: string }
> = {
  DRAFT: { background: "#fef3c7", color: "#92400e" },
  LOCKED: { background: "#dbeafe", color: "#1e40af" },
  RECONCILING: { background: "#fde68a", color: "#78350f" },
  RECONCILED: { background: "#d1fae5", color: "#065f46" },
};

function PlanStateBadge({ state }: { readonly state: PlanState }) {
  const { background, color } = STATE_BADGE_COLORS[state];
  return (
    <span
      style={{
        padding: "2px 8px",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: 700,
        background,
        color,
        display: "inline-block",
      }}
    >
      {state}
    </span>
  );
}

function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function PlanHistoryView({
  entries,
  loading,
  onViewPlan,
}: PlanHistoryViewProps) {
  const [expandedPlanId, setExpandedPlanId] = useState<string | null>(null);

  if (loading) {
    return (
      <div
        data-testid="plan-history-loading"
        role="status"
        aria-label="Loading plan history"
        style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
      >
        Loading history…
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div
        data-testid="plan-history-empty"
        style={{
          padding: "1.5rem",
          textAlign: "center",
          color: "var(--color-text-muted)",
          fontSize: "0.9rem",
        }}
      >
        No historical plans found.
      </div>
    );
  }

  // Sort chronologically (newest first)
  const sorted = [...entries].sort(
    (a, b) =>
      new Date(b.weekStartDate).getTime() - new Date(a.weekStartDate).getTime(),
  );

  return (
    <section
      data-testid="plan-history-view"
      aria-labelledby="plan-history-heading"
    >
      <h3
        id="plan-history-heading"
        style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}
      >
        Plan History
      </h3>
      <div style={{ overflowX: "auto" }}>
        <table
          data-testid="plan-history-table"
          style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}
        >
          <thead>
            <tr>
              <th style={thStyle}>Week</th>
              <th style={thStyle}>State</th>
              <th style={thStyle}>Compliant</th>
              <th style={thStyle}>Commits</th>
              <th style={thStyle}>Planned Pts</th>
              <th style={thStyle}>Achieved Pts</th>
              <th style={thStyle}>Carry-fwd</th>
              <th style={thStyle}></th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((entry) => (
              <>
                <tr
                  key={entry.planId}
                  data-testid={`plan-history-row-${entry.planId}`}
                  onClick={() =>
                    setExpandedPlanId(
                      expandedPlanId === entry.planId ? null : entry.planId,
                    )
                  }
                  style={{
                    cursor: "pointer",
                    background:
                      expandedPlanId === entry.planId
                        ? "var(--color-background)"
                        : undefined,
                  }}
                >
                  <td style={tdStyle}>
                    <span data-testid={`plan-history-week-${entry.planId}`}>
                      {formatWeekLabel(entry.weekStartDate)}
                    </span>
                  </td>
                  <td style={tdStyle}>
                    <PlanStateBadge state={entry.planState} />
                  </td>
                  <td style={tdStyle}>
                    {entry.compliant ? (
                      <span
                        data-testid={`plan-history-compliant-${entry.planId}`}
                        style={{ color: "var(--color-success)", fontWeight: 700 }}
                      >
                        ✓
                      </span>
                    ) : (
                      <span
                        data-testid={`plan-history-noncompliant-${entry.planId}`}
                        style={{ color: "var(--color-danger)" }}
                      >
                        ✗
                      </span>
                    )}
                  </td>
                  <td
                    style={{ ...tdStyle, textAlign: "center" }}
                    data-testid={`plan-history-commits-${entry.planId}`}
                  >
                    {entry.commitCount}
                  </td>
                  <td
                    style={{ ...tdStyle, textAlign: "center" }}
                    data-testid={`plan-history-planned-${entry.planId}`}
                  >
                    {entry.plannedPoints}
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    <span
                      data-testid={`plan-history-achieved-${entry.planId}`}
                      style={{
                        color:
                          entry.planState === "RECONCILED"
                            ? "var(--color-success)"
                            : "var(--color-text-muted)",
                      }}
                    >
                      {entry.achievedPoints}
                    </span>
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    {entry.carryForwardCount > 0 ? (
                      <span
                        data-testid={`plan-history-cf-${entry.planId}`}
                        style={{
                          background: "#fef3c7",
                          color: "#92400e",
                          padding: "1px 7px",
                          borderRadius: "999px",
                          fontSize: "0.75rem",
                          fontWeight: 600,
                        }}
                      >
                        {entry.carryForwardCount} CF
                      </span>
                    ) : (
                      <span style={{ color: "var(--color-text-muted)" }}>—</span>
                    )}
                  </td>
                  <td style={{ ...tdStyle, textAlign: "right" }}>
                    <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>
                      {expandedPlanId === entry.planId ? "▲" : "▼"}
                    </span>
                  </td>
                </tr>

                {/* Expanded detail row */}
                {expandedPlanId === entry.planId && (
                  <tr
                    key={`${entry.planId}-expanded`}
                    data-testid={`plan-history-expanded-${entry.planId}`}
                  >
                    <td
                      colSpan={8}
                      style={{
                        padding: "0.875rem 1.25rem",
                        background: "var(--color-background)",
                        borderBottom: "1px solid var(--color-border)",
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          gap: "1rem",
                          alignItems: "center",
                          flexWrap: "wrap",
                        }}
                      >
                        <span style={{ fontSize: "0.85rem" }}>
                          Week of <strong>{formatWeekLabel(entry.weekStartDate)}</strong>
                          {" — "}
                          <strong>{entry.planState}</strong>
                        </span>
                        {onViewPlan && (
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              onViewPlan(entry.planId, entry.weekStartDate);
                            }}
                            data-testid={`plan-history-view-btn-${entry.planId}`}
                            style={{
                              padding: "0.3rem 0.75rem",
                              border: "1px solid var(--color-border)",
                              borderRadius: "var(--border-radius)",
                              background: "var(--color-surface)",
                              cursor: "pointer",
                              fontFamily: "inherit",
                              fontSize: "0.8rem",
                            }}
                          >
                            View Full Plan →
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

const thStyle: React.CSSProperties = {
  padding: "0.5rem 0.625rem",
  textAlign: "left",
  fontSize: "0.75rem",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.04em",
  color: "var(--color-text-muted)",
  borderBottom: "2px solid var(--color-border)",
  whiteSpace: "nowrap",
};

const tdStyle: React.CSSProperties = {
  padding: "0.5rem 0.625rem",
  fontSize: "0.875rem",
  borderBottom: "1px solid var(--color-border)",
  verticalAlign: "middle",
};
