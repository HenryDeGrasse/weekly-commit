/**
 * ByPersonSection — table of direct reports with key weekly plan metrics.
 */
import { useState, Fragment } from "react";
import { ChevronDown, ChevronRight, LockOpen, Clock, RefreshCw } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { cn } from "../../lib/utils.js";
import type { MemberWeekView, MemberComplianceSummary, ChessPiece, PlanState } from "../../api/teamTypes.js";

const CHESS_PIECE_ICONS: Record<ChessPiece, string> = { KING: "♔", QUEEN: "♕", ROOK: "♖", BISHOP: "♗", KNIGHT: "♘", PAWN: "♙" };
const STATE_VARIANT: Record<PlanState, "draft" | "locked" | "reconciling" | "reconciled"> = {
  DRAFT: "draft", LOCKED: "locked", RECONCILING: "reconciling", RECONCILED: "reconciled",
};

const thCls = "px-3 py-2 text-left text-[0.7rem] font-semibold uppercase tracking-wider border-b border-border";

export interface ByPersonSectionProps {
  readonly memberViews: MemberWeekView[];
  readonly complianceSummary: MemberComplianceSummary[];
  readonly onViewMemberPlan?: (userId: string, planId: string | null) => void;
}

function ChessPieceSummary({ commits }: { commits: MemberWeekView["commits"] }) {
  const counts: Partial<Record<ChessPiece, number>> = {};
  for (const c of commits) { counts[c.chessPiece] = (counts[c.chessPiece] ?? 0) + 1; }
  const pieces: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  const active = pieces.filter((p) => (counts[p] ?? 0) > 0);
  if (active.length === 0) return <span className="text-muted text-xs">—</span>;
  return (
    <span className="inline-flex gap-0.5 items-center text-base">
      {active.map((p) => (
        <span key={p} title={`${p} × ${counts[p]}`}>
          {CHESS_PIECE_ICONS[p]}
          {(counts[p] ?? 0) > 1 && <sup className="text-[0.55rem] font-bold">{counts[p]}</sup>}
        </span>
      ))}
    </span>
  );
}

function RiskFlags({ member, compliance }: { member: MemberWeekView; compliance: MemberComplianceSummary | undefined }) {
  const maxStreak = Math.max(0, ...member.commits.map((c) => c.carryForwardStreak));
  const notLocked = compliance && !compliance.lockCompliant;
  const lateReconcile = compliance && !compliance.reconcileCompliant && compliance.planState === "RECONCILED";
  const hasCarryForward = maxStreak >= 2;
  if (!notLocked && !lateReconcile && !hasCarryForward) return <span className="text-muted text-xs">—</span>;
  return (
    <span className="inline-flex items-center gap-1.5">
      {notLocked && (
        <span title="Not locked on time" data-testid="flag-not-locked" className="text-foreground">
          <LockOpen className="h-3.5 w-3.5" />
        </span>
      )}
      {lateReconcile && (
        <span title="Late reconcile" data-testid="flag-late-reconcile" className="text-foreground">
          <Clock className="h-3.5 w-3.5" />
        </span>
      )}
      {hasCarryForward && (
        <span title={`Carry-forward streak (${maxStreak})`} data-testid="flag-carry-forward" className="text-muted">
          <RefreshCw className="h-3.5 w-3.5" />
        </span>
      )}
    </span>
  );
}

function ExpandedCommitList({ commits }: { commits: MemberWeekView["commits"] }) {
  if (commits.length === 0) return <div className="py-2 text-xs text-muted">No commits this week.</div>;
  const sorted = [...commits].sort((a, b) => a.priorityOrder - b.priorityOrder);
  return (
    <ol className="m-0 mt-2 pl-4 flex flex-col gap-1" aria-label="Commits">
      {sorted.map((c) => (
        <li key={c.id} className="text-xs flex items-center gap-1.5">
          <span title={c.chessPiece}>{CHESS_PIECE_ICONS[c.chessPiece]}</span>
          <span className="font-medium">{c.title}</span>
          {c.estimatePoints != null && <span className="text-muted">({c.estimatePoints} pt)</span>}
          {c.outcome && (
            <span className={cn("text-[0.65rem] px-1.5 py-px rounded-full font-medium",
              c.outcome === "ACHIEVED" ? "bg-foreground/10 text-foreground font-bold" : c.outcome === "PARTIALLY_ACHIEVED" ? "bg-foreground/10 text-muted" : "bg-foreground/8 text-foreground font-bold underline",
            )}>
              {c.outcome}
            </span>
          )}
          {c.carryForwardStreak >= 1 && <span className="text-[0.65rem] text-muted" title={`Carried forward ${c.carryForwardStreak}×`}>🔁{c.carryForwardStreak}</span>}
        </li>
      ))}
    </ol>
  );
}

export function ByPersonSection({ memberViews, complianceSummary, onViewMemberPlan }: ByPersonSectionProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const complianceMap = new Map(complianceSummary.map((c) => [c.userId, c]));

  const toggleExpand = (userId: string) => {
    setExpanded((prev) => { const next = new Set(prev); if (next.has(userId)) { next.delete(userId); } else { next.add(userId); } return next; });
  };

  if (memberViews.length === 0) {
    return (
      <section aria-labelledby="by-person-heading" data-testid="by-person-section">
        <h3 id="by-person-heading" className="m-0 mb-3 text-sm font-bold">By Person</h3>
        <p className="text-sm text-muted">No team members found.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="by-person-heading" data-testid="by-person-section">
      <h3 id="by-person-heading" className="m-0 mb-3 text-sm font-bold">By Person</h3>
      <div className="rounded-default border border-border bg-surface overflow-hidden">
        <table data-testid="by-person-table" className="w-full border-collapse text-sm" aria-label="Team member weekly plans">
          <thead>
            <tr className="bg-background border-b border-border">
              <th className={thCls}>Name</th>
              <th className={thCls}>State</th>
              <th className={cn(thCls, "text-right")}>Points</th>
              <th className={cn(thCls, "text-center")}>Pieces</th>
              <th className={cn(thCls, "text-center")}>Flags</th>
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
                    className={cn("cursor-pointer hover:bg-background/60 transition-colors", !isExpanded && "border-b border-border")}
                    onClick={() => toggleExpand(member.userId)}
                  >
                    <td className="px-3 py-2.5">
                      <div className="flex items-center gap-1.5">
                        <span aria-expanded={isExpanded} className="text-muted shrink-0">
                          {isExpanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
                        </span>
                        {member.planId && onViewMemberPlan ? (
                          <button type="button" data-testid={`view-member-plan-${member.userId}`} onClick={(e) => { e.stopPropagation(); onViewMemberPlan(member.userId, member.planId); }} className="bg-transparent border-none cursor-pointer p-0 font-semibold text-primary underline underline-offset-2 text-sm hover:text-primary/80">
                            {member.displayName}
                          </button>
                        ) : (
                          <span className="font-semibold">{member.displayName}</span>
                        )}
                        {maxStreak >= 1 && (
                          <span data-testid={`cf-streak-${member.userId}`} title={`Max carry-forward streak: ${maxStreak}`} className={cn("text-[0.65rem] px-1.5 py-px rounded-full font-bold", maxStreak >= 2 ? "bg-foreground/10 text-foreground" : "bg-foreground/10 text-muted")}>
                            🔁{maxStreak}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-3 py-2.5">
                      {member.planState ? <Badge variant={STATE_VARIANT[member.planState]}>{member.planState}</Badge> : <span className="text-muted text-xs">No plan</span>}
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      <span data-testid={`member-points-${member.userId}`} className={cn("font-semibold", member.totalCommittedPoints > member.capacityBudgetPoints ? "text-foreground underline" : "text-foreground")}>
                        {member.totalCommittedPoints}/{member.capacityBudgetPoints}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-center"><ChessPieceSummary commits={member.commits} /></td>
                    <td className="px-3 py-2.5 text-center"><RiskFlags member={member} compliance={compliance} /></td>
                  </tr>
                  {isExpanded && (
                    <tr data-testid={`member-row-expanded-${member.userId}`} className="border-b border-border">
                      <td colSpan={5} className="px-3 pb-3 pl-8"><ExpandedCommitList commits={member.commits} /></td>
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
