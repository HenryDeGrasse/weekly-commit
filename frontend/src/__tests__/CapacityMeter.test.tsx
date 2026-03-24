/**
 * Tests for CapacityMeter component.
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CapacityMeter } from "../components/myweek/CapacityMeter.js";
import type { CommitResponse } from "../api/planTypes.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeCommit(
  overrides: Partial<CommitResponse> & Pick<CommitResponse, "id" | "chessPiece">,
): CommitResponse {
  return {
    planId: "plan-1",
    ownerUserId: "user-1",
    title: "Test commit",
    priorityOrder: 1,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("CapacityMeter — totals display", () => {
  it("renders the capacity meter container", () => {
    render(<CapacityMeter commits={[]} budgetPoints={10} />);
    expect(screen.getByTestId("capacity-meter")).toBeInTheDocument();
  });

  it("shows 0 / 10 when there are no commits", () => {
    render(<CapacityMeter commits={[]} budgetPoints={10} />);
    expect(screen.getByTestId("capacity-tally")).toHaveTextContent("0 / 10 pts");
  });

  it("shows correct total when commits have estimate points", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "KING", estimatePoints: 3 }),
      makeCommit({ id: "c-2", chessPiece: "PAWN", estimatePoints: 2 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.getByTestId("capacity-tally")).toHaveTextContent("5 / 10 pts");
  });

  it("treats missing estimatePoints as 0", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "ROOK" }), // no estimatePoints
      makeCommit({ id: "c-2", chessPiece: "PAWN", estimatePoints: 3 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.getByTestId("capacity-tally")).toHaveTextContent("3 / 10 pts");
  });

  it("shows the progress bar element", () => {
    render(<CapacityMeter commits={[]} budgetPoints={10} />);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("progressbar has correct aria attributes", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "KING", estimatePoints: 5 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "5");
    expect(bar).toHaveAttribute("aria-valuemax", "10");
  });
});

describe("CapacityMeter — color coding", () => {
  it("does NOT show over-budget alert when under budget", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "PAWN", estimatePoints: 5 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.queryByTestId("over-budget-alert")).not.toBeInTheDocument();
  });

  it("shows over-budget alert when total > budget", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "KING", estimatePoints: 8 }),
      makeCommit({ id: "c-2", chessPiece: "QUEEN", estimatePoints: 5 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.getByTestId("over-budget-alert")).toBeInTheDocument();
    expect(screen.getByTestId("over-budget-alert")).toHaveTextContent("3 pts over budget");
  });

  it("shows singular 'pt over budget' when exactly 1 point over", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "KING", estimatePoints: 8 }),
      makeCommit({ id: "c-2", chessPiece: "PAWN", estimatePoints: 3 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.getByTestId("over-budget-alert")).toHaveTextContent("1 pt over budget");
  });
});

describe("CapacityMeter — breakdown by chess piece", () => {
  it("shows no breakdown when all commits have 0 points", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "PAWN" }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.queryByTestId("capacity-breakdown")).not.toBeInTheDocument();
  });

  it("shows breakdown for pieces with points", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "KING", estimatePoints: 3 }),
      makeCommit({ id: "c-2", chessPiece: "PAWN", estimatePoints: 2 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.getByTestId("capacity-breakdown")).toBeInTheDocument();
    expect(screen.getByTestId("capacity-piece-king")).toBeInTheDocument();
    expect(screen.getByTestId("capacity-piece-pawn")).toBeInTheDocument();
  });

  it("aggregates multiple commits of the same piece type", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "PAWN", estimatePoints: 2 }),
      makeCommit({ id: "c-2", chessPiece: "PAWN", estimatePoints: 3 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    // PAWN piece should show 5 total
    expect(screen.getByTestId("capacity-piece-pawn")).toHaveTextContent("5");
  });

  it("does not show a row for a piece with 0 points", () => {
    const commits = [
      makeCommit({ id: "c-1", chessPiece: "KING", estimatePoints: 3 }),
    ];
    render(<CapacityMeter commits={commits} budgetPoints={10} />);
    expect(screen.queryByTestId("capacity-piece-pawn")).not.toBeInTheDocument();
  });
});

describe("CapacityMeter — override badge", () => {
  it("does NOT show override badge for default budget (10 pts)", () => {
    render(<CapacityMeter commits={[]} budgetPoints={10} />);
    expect(screen.queryByTestId("override-badge")).not.toBeInTheDocument();
  });

  it("shows override badge when budget differs from default", () => {
    render(<CapacityMeter commits={[]} budgetPoints={15} />);
    expect(screen.getByTestId("override-badge")).toBeInTheDocument();
    expect(screen.getByTestId("override-badge")).toHaveTextContent(
      "Manager override",
    );
  });

  it("shows override badge when isManagerOverride flag is set", () => {
    render(<CapacityMeter commits={[]} budgetPoints={10} isManagerOverride />);
    expect(screen.getByTestId("override-badge")).toBeInTheDocument();
  });
});
