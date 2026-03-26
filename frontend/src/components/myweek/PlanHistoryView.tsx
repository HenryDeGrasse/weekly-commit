/**
 * PlanHistoryView — week-by-week plan history for the current user.
 */
import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Badge } from "../ui/Badge.js";
import { Button } from "../ui/Button.js";
import type { WeeklyPlanHistoryEntry } from "../../api/ticketTypes.js";
import type { PlanState } from "../../api/planTypes.js";
import { cn } from "../../lib/utils.js";

export interface PlanHistoryViewProps {
  readonly entries: WeeklyPlanHistoryEntry[];
  readonly loading: boolean;
  readonly onViewPlan?: (planId: string, weekStart: string) => void;
}

const STATE_VARIANT: Record<PlanState, "draft" | "locked" | "reconciling" | "reconciled"> = {
  DRAFT: "draft",
  LOCKED: "locked",
  RECONCILING: "reconciling",
  RECONCILED: "reconciled",
};

function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

const thCls = "px-2.5 py-2 text-left text-[0.7rem] font-bold uppercase tracking-widest text-muted border-b-2 border-border whitespace-nowrap";
const tdCls = "px-2.5 py-2 text-sm border-b border-border align-middle";

export function PlanHistoryView({ entries, loading, onViewPlan }: PlanHistoryViewProps) {
  const [expandedPlanId, setExpandedPlanId] = useState<string | null>(null);

  if (loading) {
    return (
      <div data-testid="plan-history-loading" role="status" aria-label="Loading plan history" className="text-sm text-muted">
        Loading history…
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div data-testid="plan-history-empty" className="py-6 text-center text-sm text-muted">
        No historical plans found.
      </div>
    );
  }

  const sorted = [...entries].sort((a, b) => new Date(b.weekStartDate).getTime() - new Date(a.weekStartDate).getTime());

  return (
    <section data-testid="plan-history-view" aria-labelledby="plan-history-heading">
      <h3 id="plan-history-heading" className="m-0 mb-3 text-sm font-bold">Plan History</h3>
      <div className="overflow-x-auto">
        <table data-testid="plan-history-table" className="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th className={thCls}>Week</th>
              <th className={thCls}>State</th>
              <th className={thCls}>Compliant</th>
              <th className={cn(thCls, "text-center")}>Commits</th>
              <th className={cn(thCls, "text-center")}>Planned Pts</th>
              <th className={cn(thCls, "text-center")}>Achieved Pts</th>
              <th className={cn(thCls, "text-center")}>Carry-fwd</th>
              <th className={thCls}></th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((entry) => (
              <>
                <tr
                  key={entry.planId}
                  data-testid={`plan-history-row-${entry.planId}`}
                  className={cn("cursor-pointer hover:bg-background/60 transition-colors", expandedPlanId === entry.planId && "bg-background")}
                  onClick={() => setExpandedPlanId(expandedPlanId === entry.planId ? null : entry.planId)}
                >
                  <td className={tdCls}>
                    <span data-testid={`plan-history-week-${entry.planId}`}>{formatWeekLabel(entry.weekStartDate)}</span>
                  </td>
                  <td className={tdCls}>
                    <Badge variant={STATE_VARIANT[entry.planState]}>{entry.planState}</Badge>
                  </td>
                  <td className={tdCls}>
                    {entry.compliant ? (
                      <span data-testid={`plan-history-compliant-${entry.planId}`} className="font-bold text-success">✓</span>
                    ) : (
                      <span data-testid={`plan-history-noncompliant-${entry.planId}`} className="text-danger">✗</span>
                    )}
                  </td>
                  <td className={cn(tdCls, "text-center")} data-testid={`plan-history-commits-${entry.planId}`}>{entry.commitCount}</td>
                  <td className={cn(tdCls, "text-center")} data-testid={`plan-history-planned-${entry.planId}`}>{entry.plannedPoints}</td>
                  <td className={cn(tdCls, "text-center")}>
                    <span data-testid={`plan-history-achieved-${entry.planId}`} className={entry.planState === "RECONCILED" ? "text-success" : "text-muted"}>
                      {entry.achievedPoints}
                    </span>
                  </td>
                  <td className={cn(tdCls, "text-center")}>
                    {entry.carryForwardCount > 0 ? (
                      <Badge data-testid={`plan-history-cf-${entry.planId}`} variant="draft">{entry.carryForwardCount} CF</Badge>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td className={cn(tdCls, "text-right")}>
                    {expandedPlanId === entry.planId
                      ? <ChevronDown className="h-3.5 w-3.5 text-muted inline" />
                      : <ChevronRight className="h-3.5 w-3.5 text-muted inline" />}
                  </td>
                </tr>
                {expandedPlanId === entry.planId && (
                  <tr key={`${entry.planId}-expanded`} data-testid={`plan-history-expanded-${entry.planId}`}>
                    <td colSpan={8} className="px-5 py-3 bg-background border-b border-border">
                      <div className="flex gap-4 items-center flex-wrap">
                        <span className="text-sm">
                          Week of <strong>{formatWeekLabel(entry.weekStartDate)}</strong> — <strong>{entry.planState}</strong>
                        </span>
                        {onViewPlan && (
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={(e) => { e.stopPropagation(); onViewPlan(entry.planId, entry.weekStartDate); }}
                            data-testid={`plan-history-view-btn-${entry.planId}`}
                          >
                            View Full Plan →
                          </Button>
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
