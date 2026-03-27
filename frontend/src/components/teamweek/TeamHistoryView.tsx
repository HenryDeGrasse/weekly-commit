/**
 * TeamHistoryView — weekly trend table for the team.
 */
import { cn } from "../../lib/utils.js";
import type { TeamWeekHistoryEntry } from "../../api/ticketTypes.js";

export interface TeamHistoryViewProps {
  readonly entries: TeamWeekHistoryEntry[];
  readonly loading: boolean;
}

function formatPct(value: number): string { return `${Math.round(value * 100)}%`; }
function formatWeekLabel(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function DeltaIndicator({ current, previous, higherIsBetter = true }: { current: number; previous: number | undefined; higherIsBetter?: boolean }) {
  if (previous === undefined) return null;
  const delta = current - previous;
  if (delta === 0) return null;
  const isGood = higherIsBetter ? delta > 0 : delta < 0;
  return (
    <span className={cn("ml-1 text-[0.65rem] font-semibold", isGood ? "text-foreground" : "text-foreground underline")}>
      {delta > 0 ? "▲" : "▼"}{Math.abs(Math.round(delta * 100) / 100)}
    </span>
  );
}

const thCls = "px-2.5 py-2 text-left text-[0.7rem] font-bold uppercase tracking-wider text-muted border-b-2 border-border whitespace-nowrap";
const tdCls = "px-2.5 py-2 text-sm border-b border-border align-middle";

export function TeamHistoryView({ entries, loading }: TeamHistoryViewProps) {
  if (loading) {
    return (
      <div data-testid="team-history-loading" role="status" aria-label="Loading team history" className="text-sm text-muted">
        Loading team history…
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div data-testid="team-history-empty" className="py-6 text-center text-sm text-muted">
        No team history available yet.
      </div>
    );
  }

  const sorted = [...entries].sort((a, b) => new Date(b.weekStartDate).getTime() - new Date(a.weekStartDate).getTime());

  return (
    <section data-testid="team-history-view" aria-labelledby="team-history-heading">
      <h3 id="team-history-heading" className="m-0 mb-3 text-sm font-bold">Team History</h3>
      <div className="overflow-x-auto">
        <table data-testid="team-history-table" className="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th className={thCls}>Week</th>
              <th className={thCls}>Members</th>
              <th className={thCls}>Compliance %</th>
              <th className={thCls}>Planned Pts</th>
              <th className={thCls}>Achieved Pts</th>
              <th className={thCls}>CF Rate</th>
              <th className={thCls}>Exceptions</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((entry, idx) => {
              const prev = sorted[idx + 1];
              return (
                <tr key={entry.weekStartDate} data-testid={`team-history-row-${entry.weekStartDate}`}>
                  <td className={tdCls}>
                    <span data-testid={`team-history-week-${entry.weekStartDate}`} className="font-semibold">{formatWeekLabel(entry.weekStartDate)}</span>
                  </td>
                  <td className={cn(tdCls, "text-center")}>{entry.memberCount}</td>
                  <td className={tdCls}>
                    <span
                      data-testid={`team-history-compliance-${entry.weekStartDate}`}
                      className={cn("font-semibold", entry.complianceRate >= 0.8 ? "text-foreground" : entry.complianceRate >= 0.5 ? "text-muted" : "text-foreground underline")}
                    >
                      {formatPct(entry.complianceRate)}
                    </span>
                    <DeltaIndicator current={entry.complianceRate} previous={prev?.complianceRate} />
                  </td>
                  <td className={cn(tdCls, "text-center")}>
                    <span data-testid={`team-history-planned-${entry.weekStartDate}`}>{entry.plannedPoints}</span>
                    <DeltaIndicator current={entry.plannedPoints} previous={prev?.plannedPoints} />
                  </td>
                  <td className={cn(tdCls, "text-center")}>
                    <span data-testid={`team-history-achieved-${entry.weekStartDate}`} className={entry.achievedPoints > 0 ? "text-foreground font-semibold" : "text-muted"}>
                      {entry.achievedPoints}
                    </span>
                    <DeltaIndicator current={entry.achievedPoints} previous={prev?.achievedPoints} />
                  </td>
                  <td className={tdCls}>
                    <span data-testid={`team-history-cf-rate-${entry.weekStartDate}`} className={entry.carryForwardRate > 0.3 ? "text-foreground underline" : "text-foreground"}>
                      {formatPct(entry.carryForwardRate)}
                    </span>
                    <DeltaIndicator current={entry.carryForwardRate} previous={prev?.carryForwardRate} higherIsBetter={false} />
                  </td>
                  <td className={cn(tdCls, "text-center")}>
                    <span data-testid={`team-history-exceptions-${entry.weekStartDate}`} className={cn(entry.exceptionCount > 0 ? "text-foreground font-bold underline" : "text-muted")}>
                      {entry.exceptionCount}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
