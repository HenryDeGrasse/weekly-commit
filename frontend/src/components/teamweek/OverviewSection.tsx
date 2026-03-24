/**
 * OverviewSection — top-level team week summary cards.
 *
 * Shows:
 *   - Lock compliance: X/Y locked, % on-time
 *   - Reconcile compliance: X/Y reconciled, % on-time
 *   - Points summary: total planned points, total achieved points, delta
 *   - Open exceptions count with severity breakdown
 */
import type { MemberComplianceSummary, MemberWeekView, ExceptionResponse, ExceptionSeverity } from "../../api/teamTypes.js";

export interface OverviewSectionProps {
  readonly complianceSummary: MemberComplianceSummary[];
  readonly memberViews: MemberWeekView[];
  readonly exceptions: ExceptionResponse[];
}

const SEVERITY_COLORS: Record<ExceptionSeverity, { bg: string; color: string }> = {
  HIGH: { bg: "#fee2e2", color: "#991b1b" },
  MEDIUM: { bg: "#fef3c7", color: "#92400e" },
  LOW: { bg: "#eff6ff", color: "#1e40af" },
};

function ComplianceCard({
  label,
  compliantCount,
  totalCount,
  testId,
}: {
  readonly label: string;
  readonly compliantCount: number;
  readonly totalCount: number;
  readonly testId: string;
}) {
  const pct = totalCount > 0 ? Math.round((compliantCount / totalCount) * 100) : 100;
  const isGood = pct >= 80;
  const color = isGood ? "var(--color-success)" : pct >= 50 ? "var(--color-warning)" : "var(--color-danger)";

  return (
    <div
      data-testid={testId}
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        padding: "1rem",
        flex: "1 1 180px",
        minWidth: "180px",
      }}
    >
      <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.5rem" }}>
        {label}
      </div>
      <div style={{ fontSize: "1.5rem", fontWeight: 700, color }} data-testid={`${testId}-count`}>
        {compliantCount}/{totalCount}
      </div>
      <div style={{ fontSize: "0.8rem", color: "var(--color-text-muted)", marginTop: "0.25rem" }} data-testid={`${testId}-pct`}>
        {pct}% on-time
      </div>
    </div>
  );
}

function PointsCard({
  plannedPoints,
  achievedPoints,
}: {
  readonly plannedPoints: number;
  readonly achievedPoints: number;
}) {
  const delta = achievedPoints - plannedPoints;

  return (
    <div
      data-testid="points-summary-card"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        padding: "1rem",
        flex: "1 1 180px",
        minWidth: "180px",
      }}
    >
      <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.5rem" }}>
        Points
      </div>
      <div style={{ display: "flex", gap: "1rem", alignItems: "baseline", flexWrap: "wrap" }}>
        <div>
          <span style={{ fontSize: "1.5rem", fontWeight: 700 }} data-testid="planned-points">{plannedPoints}</span>
          <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)", marginLeft: "0.25rem" }}>planned</span>
        </div>
        <div>
          <span style={{ fontSize: "1.5rem", fontWeight: 700, color: achievedPoints > 0 ? "var(--color-success)" : "var(--color-text-muted)" }} data-testid="achieved-points">{achievedPoints}</span>
          <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)", marginLeft: "0.25rem" }}>achieved</span>
        </div>
      </div>
      {achievedPoints > 0 && (
        <div
          style={{ fontSize: "0.8rem", marginTop: "0.25rem", color: delta >= 0 ? "var(--color-success)" : "var(--color-danger)" }}
          data-testid="points-delta"
        >
          {delta >= 0 ? "+" : ""}{delta} delta
        </div>
      )}
    </div>
  );
}

function ExceptionsCard({
  exceptions,
}: {
  readonly exceptions: ExceptionResponse[];
}) {
  const open = exceptions.filter((e) => !e.resolved);
  const highCount = open.filter((e) => e.severity === "HIGH").length;
  const medCount = open.filter((e) => e.severity === "MEDIUM").length;
  const lowCount = open.filter((e) => e.severity === "LOW").length;

  return (
    <div
      data-testid="exceptions-overview-card"
      style={{
        background: "var(--color-surface)",
        border: "1px solid var(--color-border)",
        borderRadius: "var(--border-radius)",
        padding: "1rem",
        flex: "1 1 180px",
        minWidth: "180px",
      }}
    >
      <div style={{ fontSize: "0.75rem", color: "var(--color-text-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.5rem" }}>
        Open Exceptions
      </div>
      <div style={{ fontSize: "1.5rem", fontWeight: 700, color: open.length > 0 ? "var(--color-danger)" : "var(--color-success)" }} data-testid="open-exceptions-count">
        {open.length}
      </div>
      {open.length > 0 && (
        <div style={{ display: "flex", gap: "0.375rem", marginTop: "0.5rem", flexWrap: "wrap" }}>
          {highCount > 0 && (
            <span
              data-testid="exceptions-high-count"
              style={{ ...SEVERITY_COLORS.HIGH, fontSize: "0.7rem", fontWeight: 700, padding: "1px 7px", borderRadius: "999px" }}
            >
              {highCount} HIGH
            </span>
          )}
          {medCount > 0 && (
            <span
              data-testid="exceptions-medium-count"
              style={{ ...SEVERITY_COLORS.MEDIUM, fontSize: "0.7rem", fontWeight: 700, padding: "1px 7px", borderRadius: "999px" }}
            >
              {medCount} MED
            </span>
          )}
          {lowCount > 0 && (
            <span
              data-testid="exceptions-low-count"
              style={{ ...SEVERITY_COLORS.LOW, fontSize: "0.7rem", fontWeight: 700, padding: "1px 7px", borderRadius: "999px" }}
            >
              {lowCount} LOW
            </span>
          )}
        </div>
      )}
    </div>
  );
}

export function OverviewSection({
  complianceSummary,
  memberViews,
  exceptions,
}: OverviewSectionProps) {
  const total = complianceSummary.length;
  const lockedCount = complianceSummary.filter((m) => m.lockCompliant).length;
  const reconciledCount = complianceSummary.filter((m) => m.reconcileCompliant).length;

  const plannedPoints = memberViews.reduce((sum, m) => sum + m.totalCommittedPoints, 0);
  const achievedPoints = memberViews.reduce((sum, m) => {
    return (
      sum +
      m.commits
        .filter((c) => c.outcome === "ACHIEVED")
        .reduce((s, c) => s + (c.estimatePoints ?? 0), 0)
    );
  }, 0);

  return (
    <section aria-labelledby="overview-heading" data-testid="overview-section">
      <h3
        id="overview-heading"
        style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}
      >
        Overview
      </h3>
      <div
        style={{
          display: "flex",
          gap: "0.75rem",
          flexWrap: "wrap",
        }}
      >
        <ComplianceCard
          label="Lock Compliance"
          compliantCount={lockedCount}
          totalCount={total}
          testId="lock-compliance-card"
        />
        <ComplianceCard
          label="Reconcile Compliance"
          compliantCount={reconciledCount}
          totalCount={total}
          testId="reconcile-compliance-card"
        />
        <PointsCard
          plannedPoints={plannedPoints}
          achievedPoints={achievedPoints}
        />
        <ExceptionsCard exceptions={exceptions} />
      </div>
    </section>
  );
}
