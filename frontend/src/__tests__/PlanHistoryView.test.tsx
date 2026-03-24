/**
 * Tests for PlanHistoryView component.
 *
 * Covers:
 *   - Loading state
 *   - Empty state
 *   - Renders weeks in newest-first order
 *   - Shows plan state, compliance, commit count, points, carry-forward count
 *   - Click to expand full plan detail
 *   - Collapse on second click
 *   - "View Full Plan" button calls onViewPlan
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import { PlanHistoryView } from "../components/myweek/PlanHistoryView.js";
import type { WeeklyPlanHistoryEntry } from "../api/ticketTypes.js";

function makeEntry(
  planId: string,
  weekStart: string,
  overrides: Partial<WeeklyPlanHistoryEntry> = {},
): WeeklyPlanHistoryEntry {
  return {
    planId,
    weekStartDate: weekStart,
    planState: "RECONCILED",
    compliant: true,
    commitCount: 4,
    plannedPoints: 10,
    achievedPoints: 9,
    carryForwardCount: 1,
    ...overrides,
  };
}

const entries: WeeklyPlanHistoryEntry[] = [
  makeEntry("plan-1", "2026-03-10"),
  makeEntry("plan-2", "2026-03-17", { compliant: false, carryForwardCount: 0 }),
  makeEntry("plan-3", "2026-03-24", { planState: "LOCKED", achievedPoints: 0 }),
];

describe("PlanHistoryView — rendering", () => {
  it("renders loading state", () => {
    render(<PlanHistoryView entries={[]} loading={true} />);
    expect(screen.getByTestId("plan-history-loading")).toBeInTheDocument();
  });

  it("renders empty state when no entries", () => {
    render(<PlanHistoryView entries={[]} loading={false} />);
    expect(screen.getByTestId("plan-history-empty")).toBeInTheDocument();
  });

  it("renders the history view with table", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-view")).toBeInTheDocument();
    expect(screen.getByTestId("plan-history-table")).toBeInTheDocument();
  });

  it("renders a row for each entry", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-row-plan-1")).toBeInTheDocument();
    expect(screen.getByTestId("plan-history-row-plan-2")).toBeInTheDocument();
    expect(screen.getByTestId("plan-history-row-plan-3")).toBeInTheDocument();
  });
});

describe("PlanHistoryView — sort order (newest first)", () => {
  it("renders weeks in newest-first order", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    const table = screen.getByTestId("plan-history-table");
    const rows = within(table).getAllByTestId(/^plan-history-row-/);
    // plan-3 (2026-03-24) should be first, plan-1 (2026-03-10) last
    expect(rows[0]).toHaveAttribute("data-testid", "plan-history-row-plan-3");
    expect(rows[2]).toHaveAttribute("data-testid", "plan-history-row-plan-1");
  });
});

describe("PlanHistoryView — row data", () => {
  it("shows planned points for each entry", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-planned-plan-1")).toHaveTextContent("10");
  });

  it("shows achieved points for each entry", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-achieved-plan-1")).toHaveTextContent("9");
  });

  it("shows carry-forward count badge when > 0", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-cf-plan-1")).toBeInTheDocument();
    expect(screen.getByTestId("plan-history-cf-plan-1")).toHaveTextContent("1 CF");
  });

  it("does NOT show carry-forward badge when count is 0", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.queryByTestId("plan-history-cf-plan-2")).not.toBeInTheDocument();
  });

  it("shows compliance check mark for compliant plan", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-compliant-plan-1")).toBeInTheDocument();
  });

  it("shows non-compliance mark for non-compliant plan", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-noncompliant-plan-2")).toBeInTheDocument();
  });

  it("shows commit count for each entry", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    expect(screen.getByTestId("plan-history-commits-plan-1")).toHaveTextContent("4");
  });
});

describe("PlanHistoryView — expand/collapse", () => {
  it("clicking a row expands the detail row", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    const row = screen.getByTestId("plan-history-row-plan-1");
    fireEvent.click(row);
    expect(screen.getByTestId("plan-history-expanded-plan-1")).toBeInTheDocument();
  });

  it("clicking the same row again collapses the detail", () => {
    render(<PlanHistoryView entries={entries} loading={false} />);
    const row = screen.getByTestId("plan-history-row-plan-1");
    fireEvent.click(row);
    expect(screen.getByTestId("plan-history-expanded-plan-1")).toBeInTheDocument();
    fireEvent.click(row);
    expect(screen.queryByTestId("plan-history-expanded-plan-1")).not.toBeInTheDocument();
  });

  it("shows 'View Full Plan' button when onViewPlan is provided", () => {
    const onViewPlan = vi.fn();
    render(<PlanHistoryView entries={entries} loading={false} onViewPlan={onViewPlan} />);
    const row = screen.getByTestId("plan-history-row-plan-1");
    fireEvent.click(row);
    expect(screen.getByTestId("plan-history-view-btn-plan-1")).toBeInTheDocument();
  });

  it("'View Full Plan' button calls onViewPlan with planId and weekStart", () => {
    const onViewPlan = vi.fn();
    render(<PlanHistoryView entries={entries} loading={false} onViewPlan={onViewPlan} />);
    const row = screen.getByTestId("plan-history-row-plan-1");
    fireEvent.click(row);
    fireEvent.click(screen.getByTestId("plan-history-view-btn-plan-1"));
    expect(onViewPlan).toHaveBeenCalledWith("plan-1", "2026-03-10");
  });
});
