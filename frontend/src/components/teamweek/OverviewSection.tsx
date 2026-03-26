/**
 * OverviewSection — top-level team week summary cards.
 */
import { cn } from "../../lib/utils.js";
import type { MemberComplianceSummary, MemberWeekView, ExceptionResponse, ExceptionSeverity } from "../../api/teamTypes.js";

export interface OverviewSectionProps {
  readonly complianceSummary: MemberComplianceSummary[];
  readonly memberViews: MemberWeekView[];
  readonly exceptions: ExceptionResponse[];
}

function ComplianceCard({ label, compliantCount, totalCount, testId }: { label: string; compliantCount: number; totalCount: number; testId: string }) {
  const pct = totalCount > 0 ? Math.round((compliantCount / totalCount) * 100) : 100;
  const valueCls = pct >= 80 ? "text-success" : pct >= 50 ? "text-warning" : "text-danger";

  return (
    <div data-testid={testId} className="rounded-default border border-border bg-surface p-4 flex-1 min-w-[180px]">
      <div className="text-[0.7rem] text-muted font-semibold uppercase tracking-wider mb-2">{label}</div>
      <div className={cn("text-2xl font-bold", valueCls)} data-testid={`${testId}-count`}>{compliantCount}/{totalCount}</div>
      <div className="text-xs text-muted mt-1" data-testid={`${testId}-pct`}>{pct}% on-time</div>
    </div>
  );
}

function PointsCard({ plannedPoints, achievedPoints }: { plannedPoints: number; achievedPoints: number }) {
  const delta = achievedPoints - plannedPoints;
  return (
    <div data-testid="points-summary-card" className="rounded-default border border-border bg-surface p-4 flex-1 min-w-[180px]">
      <div className="text-[0.7rem] text-muted font-semibold uppercase tracking-wider mb-2">Points</div>
      <div className="flex gap-4 items-baseline flex-wrap">
        <div>
          <span className="text-2xl font-bold" data-testid="planned-points">{plannedPoints}</span>
          <span className="text-xs text-muted ml-1">planned</span>
        </div>
        <div>
          <span className={cn("text-2xl font-bold", achievedPoints > 0 ? "text-success" : "text-muted")} data-testid="achieved-points">{achievedPoints}</span>
          <span className="text-xs text-muted ml-1">achieved</span>
        </div>
      </div>
      {achievedPoints > 0 && (
        <div className={cn("text-xs mt-1", delta >= 0 ? "text-success" : "text-danger")} data-testid="points-delta">
          {delta >= 0 ? "+" : ""}{delta} delta
        </div>
      )}
    </div>
  );
}

const SEVERITY_BADGE: Record<ExceptionSeverity, string> = {
  HIGH: "bg-red-100 text-red-800",
  MEDIUM: "bg-amber-100 text-amber-800",
  LOW: "bg-blue-100 text-blue-800",
};

function ExceptionsCard({ exceptions }: { exceptions: ExceptionResponse[] }) {
  const open = exceptions.filter((e) => !e.resolved);
  const highCount = open.filter((e) => e.severity === "HIGH").length;
  const medCount = open.filter((e) => e.severity === "MEDIUM").length;
  const lowCount = open.filter((e) => e.severity === "LOW").length;

  return (
    <div data-testid="exceptions-overview-card" className="rounded-default border border-border bg-surface p-4 flex-1 min-w-[180px]">
      <div className="text-[0.7rem] text-muted font-semibold uppercase tracking-wider mb-2">Open Exceptions</div>
      <div className={cn("text-2xl font-bold", open.length > 0 ? "text-danger" : "text-success")} data-testid="open-exceptions-count">{open.length}</div>
      {open.length > 0 && (
        <div className="flex gap-1.5 mt-2 flex-wrap">
          {highCount > 0 && <span data-testid="exceptions-high-count" className={cn("text-[0.65rem] font-bold px-1.5 py-px rounded-full", SEVERITY_BADGE.HIGH)}>{highCount} HIGH</span>}
          {medCount > 0 && <span data-testid="exceptions-medium-count" className={cn("text-[0.65rem] font-bold px-1.5 py-px rounded-full", SEVERITY_BADGE.MEDIUM)}>{medCount} MED</span>}
          {lowCount > 0 && <span data-testid="exceptions-low-count" className={cn("text-[0.65rem] font-bold px-1.5 py-px rounded-full", SEVERITY_BADGE.LOW)}>{lowCount} LOW</span>}
        </div>
      )}
    </div>
  );
}

export function OverviewSection({ complianceSummary, memberViews, exceptions }: OverviewSectionProps) {
  const total = complianceSummary.length;
  const lockedCount = complianceSummary.filter((m) => m.lockCompliant).length;
  const reconciledCount = complianceSummary.filter((m) => m.reconcileCompliant).length;
  const plannedPoints = memberViews.reduce((sum, m) => sum + m.totalCommittedPoints, 0);
  const achievedPoints = memberViews.reduce((sum, m) => sum + m.commits.filter((c) => c.outcome === "ACHIEVED").reduce((s, c) => s + (c.estimatePoints ?? 0), 0), 0);

  return (
    <section aria-labelledby="overview-heading" data-testid="overview-section">
      <h3 id="overview-heading" className="m-0 mb-3 text-sm font-bold">Overview</h3>
      <div className="flex gap-3 flex-wrap">
        <ComplianceCard label="Lock Compliance" compliantCount={lockedCount} totalCount={total} testId="lock-compliance-card" />
        <ComplianceCard label="Reconcile Compliance" compliantCount={reconciledCount} totalCount={total} testId="reconcile-compliance-card" />
        <PointsCard plannedPoints={plannedPoints} achievedPoints={achievedPoints} />
        <ExceptionsCard exceptions={exceptions} />
      </div>
    </section>
  );
}
