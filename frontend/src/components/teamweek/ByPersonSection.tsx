/**
 * ByPersonSection — table of direct reports with key weekly plan metrics.
 *
 * Columns: name, plan state, points committed/budget, chess piece summary,
 * risk flags, carry-forward streak, post-lock changes indicator.
 * Expandable rows show individual commit lists.
 * Click-through to view full plan detail.
 */
import { useState, Fragment } from "react";
import type { MemberWeekView, MemberComplianceSummary, ChessPiece, PlanState } from "../../api/teamTypes.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = {
  KING: "♔",
  QUEEN: "♕",
  ROOK: "♖",
  BISHOP: "♗",
  KNIGHT: "♘",
  PAWN: "♙",
};

const STATE_COLORS: Record<PlanState, { bg: string; color: string }> = {
  DRAFT: { bg: "#fef3c7", color: "#92400e" },
  LOCKED: { bg: "#dbeafe", color: "#1e40af" },
  RECONCILING: { bg: "#fde68a", color: "#78350f" },
  RECONCILED: { bg: "#d1fae5", color: "#065f46" },
};

export interface ByPersonSectionProps {
  readonly memberViews: MemberWeekView[];
  readonly complianceSummary: MemberComplianceSummary[];
  /** Called when the user clicks a member's name to navigate to their plan. */
  readonly onViewMemberPlan?: (userId: string, planId: string | null) => void;
}

function PlanStatePill({ state }: { readonly state: PlanState }) {
  const { bg, color } = STATE_COLORS[state];
  return (
    <span
      style={{
        padding: "1px 8px",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: 700,
        background: bg,
        color,
        letterSpacing: "0.02em",
      }}
    >
      {state}
    </span>
  );
}

function ChessPieceSummary({ commits }: { readonly commits: MemberWeekView["commits"] }) {
  const counts: Partial<Record<ChessPiece, number>> = {};
  for (const c of commits) {
    counts[c.chessPiece] = (counts[c.chessPiece] ?? 0) + 1;
  }

  const pieces: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  const active = pieces.filter((p) => (counts[p] ?? 0) > 0);
  if (active.length === 0) return <span style={{ color: "var(--color-text-muted)", fontSize: "0.8rem" }}>—</span>;

  return (
    <span style={{ fontSize: "0.9rem", display: "inline-flex", gap: "0.125rem", alignItems: "center" }}>
      {active.map((p) => (
        <span key={p} title={`${p} × ${counts[p]}`}>
          {CHESS_PIECE_ICONS[p]}
          {(counts[p] ?? 0) > 1 && (
            <sup style={{ fontSize: "0.6rem", fontWeight: 700 }}>{counts[p]}</sup>
          )}
        </span>
      ))}
    </span>
  );
}

function RiskFlags({
  member,
  compliance,
}: {
  readonly member: MemberWeekView;
  readonly compliance: MemberComplianceSummary | undefined;
}) {
  const flags: Array<{ icon: string; title: string; testId: string }> = [];

  if (compliance && !compliance.lockCompliant) {
    flags.push({ icon: "🔓", title: "Not locked on time", testId: "flag-not-locked" });
  }
  if (compliance && !compliance.reconcileCompliant && compliance.planState === "RECONCILED") {
    // Only show reconcile flag when reconciliation was expected
    flags.push({ icon: "⏰", title: "Late reconcile", testId: "flag-late-reconcile" });
  }
  const maxStreak = Math.max(0, ...member.commits.map((c) => c.carryForwardStreak));
  if (maxStreak >= 2) {
    flags.push({ icon: "🔁", title: `Carry-forward streak (${maxStreak})`, testId: "flag-carry-forward" });
  }

  if (flags.length === 0) return <span style={{ color: "var(--color-text-muted)", fontSize: "0.8rem" }}>—</span>;

  return (
    <span style={{ display: "inline-flex", gap: "0.25rem" }}>
      {flags.map((f) => (
        <span key={f.testId} title={f.title} style={{ fontSize: "1rem" }}>
          {f.icon}
        </span>
      ))}
    </span>
  );
}

function ExpandedCommitList({ commits }: { readonly commits: MemberWeekView["commits"] }) {
  if (commits.length === 0) {
    return (
      <div style={{ padding: "0.5rem 0", color: "var(--color-text-muted)", fontSize: "0.8rem" }}>
        No commits this week.
      </div>
    );
  }

  const sorted = [...commits].sort((a, b) => a.priorityOrder - b.priorityOrder);

  return (
    <ol
      style={{ margin: "0.5rem 0 0", padding: "0 0 0 1rem", display: "flex", flexDirection: "column", gap: "0.25rem" }}
      aria-label="Commits"
    >
      {sorted.map((c) => (
        <li key={c.id} style={{ fontSize: "0.8rem", display: "flex", alignItems: "center", gap: "0.375rem" }}>
          <span title={c.chessPiece}>{CHESS_PIECE_ICONS[c.chessPiece]}</span>
          <span style={{ fontWeight: 500 }}>{c.title}</span>
          {c.estimatePoints != null && (
            <span style={{ color: "var(--color-text-muted)" }}>({c.estimatePoints} pt)</span>
          )}
          {c.outcome && (
            <span
              style={{
                fontSize: "0.7rem",
                padding: "1px 6px",
                borderRadius: "999px",
                background: c.outcome === "ACHIEVED" ? "#d1fae5" : c.outcome === "PARTIALLY_ACHIEVED" ? "#fef3c7" : "#fee2e2",
                color: c.outcome === "ACHIEVED" ? "#065f46" : c.outcome === "PARTIALLY_ACHIEVED" ? "#92400e" : "#991b1b",
              }}
            >
              {c.outcome}
            </span>
          )}
          {c.carryForwardStreak >= 1 && (
            <span style={{ fontSize: "0.7rem", color: "#1e40af" }} title={`Carried forward ${c.carryForwardStreak}×`}>
              🔁{c.carryForwardStreak}
            </span>
          )}
        </li>
      ))}
    </ol>
  );
}

export function ByPersonSection({
  memberViews,
  complianceSummary,
  onViewMemberPlan,
}: ByPersonSectionProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const complianceMap = new Map(complianceSummary.map((c) => [c.userId, c]));

  const toggleExpand = (userId: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
  };

  if (memberViews.length === 0) {
    return (
      <section aria-labelledby="by-person-heading" data-testid="by-person-section">
        <h3 id="by-person-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
          By Person
        </h3>
        <p style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}>No team members found.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="by-person-heading" data-testid="by-person-section">
      <h3 id="by-person-heading" style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}>
        By Person
      </h3>
      <div
        style={{
          background: "var(--color-surface)",
          border: "1px solid var(--color-border)",
          borderRadius: "var(--border-radius)",
          overflow: "hidden",
        }}
      >
        <table
          data-testid="by-person-table"
          style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}
          aria-label="Team member weekly plans"
        >
          <thead>
            <tr style={{ background: "var(--color-background)", borderBottom: "1px solid var(--color-border)" }}>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Name
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "left", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                State
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "right", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Points
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "center", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Pieces
              </th>
              <th style={{ padding: "0.5rem 0.75rem", textAlign: "center", fontWeight: 600, fontSize: "0.75rem", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Flags
              </th>
            </tr>
          </thead>
          <tbody>
            {memberViews.map((member) => {
              const compliance = complianceMap.get(member.userId);
              const isExpanded = expanded.has(member.userId);
              const maxStreak = Math.max(0, ...member.commits.map((c) => c.carryForwardStreak));

              return (
                <Fragment key={member.userId}>
                  <tr
                    data-testid={`member-row-${member.userId}`}
                    style={{
                      borderBottom: isExpanded ? "none" : "1px solid var(--color-border)",
                      cursor: "pointer",
                    }}
                    onClick={() => toggleExpand(member.userId)}
                  >
                    <td style={{ padding: "0.625rem 0.75rem" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "0.375rem" }}>
                        <span
                          aria-expanded={isExpanded}
                          style={{ fontSize: "0.7rem", color: "var(--color-text-muted)", width: "12px" }}
                        >
                          {isExpanded ? "▼" : "▶"}
                        </span>
                        {member.planId && onViewMemberPlan ? (
                          <button
                            type="button"
                            data-testid={`view-member-plan-${member.userId}`}
                            onClick={(e) => {
                              e.stopPropagation();
                              onViewMemberPlan(member.userId, member.planId);
                            }}
                            style={{
                              background: "none",
                              border: "none",
                              cursor: "pointer",
                              padding: 0,
                              fontWeight: 600,
                              color: "var(--color-primary)",
                              fontFamily: "inherit",
                              fontSize: "inherit",
                              textDecoration: "underline",
                            }}
                          >
                            {member.displayName}
                          </button>
                        ) : (
                          <span style={{ fontWeight: 600 }}>{member.displayName}</span>
                        )}
                        {maxStreak >= 1 && (
                          <span
                            data-testid={`cf-streak-${member.userId}`}
                            title={`Max carry-forward streak: ${maxStreak}`}
                            style={{
                              fontSize: "0.7rem",
                              padding: "1px 6px",
                              borderRadius: "999px",
                              background: maxStreak >= 2 ? "#fee2e2" : "#eff6ff",
                              color: maxStreak >= 2 ? "#991b1b" : "#1e40af",
                              fontWeight: 700,
                            }}
                          >
                            🔁{maxStreak}
                          </span>
                        )}
                      </div>
                    </td>
                    <td style={{ padding: "0.625rem 0.75rem" }}>
                      {member.planState ? (
                        <PlanStatePill state={member.planState} />
                      ) : (
                        <span style={{ color: "var(--color-text-muted)", fontSize: "0.8rem" }}>No plan</span>
                      )}
                    </td>
                    <td style={{ padding: "0.625rem 0.75rem", textAlign: "right" }}>
                      <span
                        data-testid={`member-points-${member.userId}`}
                        style={{
                          fontWeight: 600,
                          color: member.totalCommittedPoints > member.capacityBudgetPoints
                            ? "var(--color-danger)"
                            : "var(--color-text)",
                        }}
                      >
                        {member.totalCommittedPoints}/{member.capacityBudgetPoints}
                      </span>
                    </td>
                    <td style={{ padding: "0.625rem 0.75rem", textAlign: "center" }}>
                      <ChessPieceSummary commits={member.commits} />
                    </td>
                    <td style={{ padding: "0.625rem 0.75rem", textAlign: "center" }}>
                      <RiskFlags member={member} compliance={compliance} />
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr
                      data-testid={`member-row-expanded-${member.userId}`}
                      style={{ borderBottom: "1px solid var(--color-border)" }}
                    >
                      <td
                        colSpan={5}
                        style={{ padding: "0 0.75rem 0.75rem 2rem" }}
                      >
                        <ExpandedCommitList commits={member.commits} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
