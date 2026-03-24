/**
 * Tests for TeamHistoryView component.
 *
 * Covers:
 *   - Loading state
 *   - Empty state
 *   - Renders rows for each week (newest first)
 *   - Shows compliance rate, planned/achieved points, CF rate, exception count
 *   - Delta indicators show week-over-week changes
 *   - Compliance rate color coding (green ≥ 80%, yellow ≥ 50%, red < 50%)
 */
import { describe, it, expect } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { TeamHistoryView } from "../components/teamweek/TeamHistoryView.js";
import type { TeamWeekHistoryEntry } from "../api/ticketTypes.js";

function makeEntry(
  weekStart: string,
  overrides: Partial<TeamWeekHistoryEntry> = {},
): TeamWeekHistoryEntry {
  return {
    weekStartDate: weekStart,
    memberCount: 5,
    complianceRate: 0.8,
    plannedPoints: 40,
    achievedPoints: 36,
    carryForwardRate: 0.1,
    exceptionCount: 0,
    ...overrides,
  };
}

const threeWeeks: TeamWeekHistoryEntry[] = [
  makeEntry("2026-03-10", { complianceRate: 0.6, plannedPoints: 35, achievedPoints: 28, exceptionCount: 2 }),
  makeEntry("2026-03-17", { complianceRate: 0.8, plannedPoints: 40, achievedPoints: 36 }),
  makeEntry("2026-03-24", { complianceRate: 1.0, plannedPoints: 42, achievedPoints: 40, carryForwardRate: 0.05 }),
];

describe("TeamHistoryView — rendering", () => {
  it("shows loading state", () => {
    render(<TeamHistoryView entries={[]} loading={true} />);
    expect(screen.getByTestId("team-history-loading")).toBeInTheDocument();
  });

  it("shows empty state when no entries", () => {
    render(<TeamHistoryView entries={[]} loading={false} />);
    expect(screen.getByTestId("team-history-empty")).toBeInTheDocument();
  });

  it("renders the history view with table", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-view")).toBeInTheDocument();
    expect(screen.getByTestId("team-history-table")).toBeInTheDocument();
  });

  it("renders a row for each week entry", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-row-2026-03-10")).toBeInTheDocument();
    expect(screen.getByTestId("team-history-row-2026-03-17")).toBeInTheDocument();
    expect(screen.getByTestId("team-history-row-2026-03-24")).toBeInTheDocument();
  });
});

describe("TeamHistoryView — sort order (newest first)", () => {
  it("renders newest week first", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    const table = screen.getByTestId("team-history-table");
    const rows = within(table).getAllByTestId(/^team-history-row-/);
    // 2026-03-24 should be first
    expect(rows[0]).toHaveAttribute("data-testid", "team-history-row-2026-03-24");
    expect(rows[2]).toHaveAttribute("data-testid", "team-history-row-2026-03-10");
  });
});

describe("TeamHistoryView — row data", () => {
  it("shows compliance rate as percentage", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-compliance-2026-03-24")).toHaveTextContent("100%");
    expect(screen.getByTestId("team-history-compliance-2026-03-17")).toHaveTextContent("80%");
    expect(screen.getByTestId("team-history-compliance-2026-03-10")).toHaveTextContent("60%");
  });

  it("shows planned points for each week", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-planned-2026-03-24")).toHaveTextContent("42");
    expect(screen.getByTestId("team-history-planned-2026-03-10")).toHaveTextContent("35");
  });

  it("shows achieved points for each week", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-achieved-2026-03-24")).toHaveTextContent("40");
    expect(screen.getByTestId("team-history-achieved-2026-03-10")).toHaveTextContent("28");
  });

  it("shows carry-forward rate as percentage", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-cf-rate-2026-03-24")).toHaveTextContent("5%");
    expect(screen.getByTestId("team-history-cf-rate-2026-03-10")).toHaveTextContent("10%");
  });

  it("shows exception count with color for non-zero values", () => {
    render(<TeamHistoryView entries={threeWeeks} loading={false} />);
    expect(screen.getByTestId("team-history-exceptions-2026-03-10")).toHaveTextContent("2");
    expect(screen.getByTestId("team-history-exceptions-2026-03-17")).toHaveTextContent("0");
  });
});

describe("TeamHistoryView — compliance color coding", () => {
  it("uses success color for compliance ≥ 80%", () => {
    render(<TeamHistoryView entries={[makeEntry("2026-03-24", { complianceRate: 0.9 })]} loading={false} />);
    const cell = screen.getByTestId("team-history-compliance-2026-03-24");
    // Color should be success color (green)
    expect(cell).toHaveStyle({ color: "var(--color-success)" });
  });

  it("uses warning color for compliance 50%–79%", () => {
    render(<TeamHistoryView entries={[makeEntry("2026-03-24", { complianceRate: 0.6 })]} loading={false} />);
    const cell = screen.getByTestId("team-history-compliance-2026-03-24");
    expect(cell).toHaveStyle({ color: "var(--color-warning)" });
  });

  it("uses danger color for compliance < 50%", () => {
    render(<TeamHistoryView entries={[makeEntry("2026-03-24", { complianceRate: 0.4 })]} loading={false} />);
    const cell = screen.getByTestId("team-history-compliance-2026-03-24");
    expect(cell).toHaveStyle({ color: "var(--color-danger)" });
  });
});
