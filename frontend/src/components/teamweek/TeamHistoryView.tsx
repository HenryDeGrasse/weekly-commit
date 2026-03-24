/**
 * TeamHistoryView — weekly trend table for the team.
 *
 * Columns: week, member count, compliance rate, planned pts, achieved pts,
 * carry-forward rate, exception count.
 * Shows week-over-week delta indicators for key metrics.
 */
import type { TeamWeekHistoryEntry } from "../../api/ticketTypes.js";

export interface TeamHistoryViewProps {
  readonly entries: TeamWeekHistoryEntry[];
  readonly loading: boolean;
}

function formatPct(value: number): string {
  return `${Math.round(value * 100)}%`;
}

function formatWeekLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

function DeltaIndicator({
  current,
  previous,
  higherIsBetter = true,
}: {
  readonly current: number;
  readonly previous: number | undefined;
  readonly higherIsBetter?: boolean;
}) {
  if (previous === undefined) return null;
  const delta = current - previous;
  if (delta === 0) return null;
  const isGood = higherIsBetter ? delta > 0 : delta < 0;
  return (
    <span
      style={{
        marginLeft: "0.25rem",
        fontSize: "0.7rem",
        color: isGood ? "var(--color-success)" : "var(--color-danger)",
        fontWeight: 600,
      }}
    >
      {delta > 0 ? "▲" : "▼"}{Math.abs(Math.round(delta * 100) / 100)}
    </span>
  );
}

export function TeamHistoryView({ entries, loading }: TeamHistoryViewProps) {
  if (loading) {
    return (
      <div
        data-testid="team-history-loading"
        role="status"
        aria-label="Loading team history"
        style={{ color: "var(--color-text-muted)", fontSize: "0.875rem" }}
      >
        Loading team history…
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div
        data-testid="team-history-empty"
        style={{
          padding: "1.5rem",
          textAlign: "center",
          color: "var(--color-text-muted)",
          fontSize: "0.9rem",
        }}
      >
        No team history available yet.
      </div>
    );
  }

  // Sort newest first
  const sorted = [...entries].sort(
    (a, b) =>
      new Date(b.weekStartDate).getTime() - new Date(a.weekStartDate).getTime(),
  );

  return (
    <section
      data-testid="team-history-view"
      aria-labelledby="team-history-heading"
    >
      <h3
        id="team-history-heading"
        style={{ margin: "0 0 0.75rem", fontSize: "0.95rem", fontWeight: 700 }}
      >
        Team History
      </h3>
      <div style={{ overflowX: "auto" }}>
        <table
          data-testid="team-history-table"
          style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.875rem" }}
        >
          <thead>
            <tr>
              <th style={thStyle}>Week</th>
              <th style={thStyle}>Members</th>
              <th style={thStyle}>Compliance %</th>
              <th style={thStyle}>Planned Pts</th>
              <th style={thStyle}>Achieved Pts</th>
              <th style={thStyle}>CF Rate</th>
              <th style={thStyle}>Exceptions</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((entry, idx) => {
              const prev = sorted[idx + 1]; // previous week (older)
              return (
                <tr
                  key={entry.weekStartDate}
                  data-testid={`team-history-row-${entry.weekStartDate}`}
                >
                  <td style={tdStyle}>
                    <span
                      data-testid={`team-history-week-${entry.weekStartDate}`}
                      style={{ fontWeight: 600 }}
                    >
                      {formatWeekLabel(entry.weekStartDate)}
                    </span>
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    {entry.memberCount}
                  </td>
                  <td style={tdStyle}>
                    <span
                      data-testid={`team-history-compliance-${entry.weekStartDate}`}
                      style={{
                        color:
                          entry.complianceRate >= 0.8
                            ? "var(--color-success)"
                            : entry.complianceRate >= 0.5
                              ? "var(--color-warning)"
                              : "var(--color-danger)",
                        fontWeight: 600,
                      }}
                    >
                      {formatPct(entry.complianceRate)}
                    </span>
                    <DeltaIndicator
                      current={entry.complianceRate}
                      previous={prev?.complianceRate}
                      higherIsBetter
                    />
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    <span data-testid={`team-history-planned-${entry.weekStartDate}`}>
                      {entry.plannedPoints}
                    </span>
                    <DeltaIndicator
                      current={entry.plannedPoints}
                      previous={prev?.plannedPoints}
                      higherIsBetter
                    />
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    <span
                      data-testid={`team-history-achieved-${entry.weekStartDate}`}
                      style={{
                        color:
                          entry.achievedPoints > 0
                            ? "var(--color-success)"
                            : "var(--color-text-muted)",
                      }}
                    >
                      {entry.achievedPoints}
                    </span>
                    <DeltaIndicator
                      current={entry.achievedPoints}
                      previous={prev?.achievedPoints}
                      higherIsBetter
                    />
                  </td>
                  <td style={tdStyle}>
                    <span
                      data-testid={`team-history-cf-rate-${entry.weekStartDate}`}
                      style={{
                        color:
                          entry.carryForwardRate > 0.3
                            ? "var(--color-warning)"
                            : "var(--color-text)",
                      }}
                    >
                      {formatPct(entry.carryForwardRate)}
                    </span>
                    <DeltaIndicator
                      current={entry.carryForwardRate}
                      previous={prev?.carryForwardRate}
                      higherIsBetter={false}
                    />
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    <span
                      data-testid={`team-history-exceptions-${entry.weekStartDate}`}
                      style={{
                        color:
                          entry.exceptionCount > 0
                            ? "var(--color-danger)"
                            : "var(--color-text-muted)",
                        fontWeight: entry.exceptionCount > 0 ? 600 : undefined,
                      }}
                    >
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
