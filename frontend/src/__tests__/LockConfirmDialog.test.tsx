/**
 * Tests for LockConfirmDialog component.
 *
 * Covers:
 *   - Renders dialog with summary stats
 *   - Shows commit count, total points, RCDO coverage
 *   - Chess piece breakdown rendered
 *   - Confirm calls onConfirm
 *   - Cancel calls onCancel
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { LockConfirmDialog } from "../components/lock/LockConfirmDialog.js";
import type { CommitResponse } from "../api/planTypes.js";

function makeCommit(
  id: string,
  overrides: Partial<CommitResponse> = {},
): CommitResponse {
  return {
    id,
    planId: "plan-1",
    ownerUserId: "user-1",
    title: `Commit ${id}`,
    chessPiece: "PAWN",
    priorityOrder: 1,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

const threeCommits: CommitResponse[] = [
  makeCommit("c-1", {
    chessPiece: "KING",
    estimatePoints: 5,
    rcdoNodeId: "r1",
  }),
  makeCommit("c-2", {
    chessPiece: "QUEEN",
    estimatePoints: 3,
    rcdoNodeId: "r2",
  }),
  makeCommit("c-3", { chessPiece: "PAWN", estimatePoints: 2 }), // no RCDO
];

describe("LockConfirmDialog", () => {
  it("renders the dialog", () => {
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("lock-confirm-dialog")).toBeInTheDocument();
  });

  it("shows commit count", () => {
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("lock-confirm-commit-count")).toHaveTextContent(
      "3",
    );
  });

  it("shows total points vs budget", () => {
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    // 5 + 3 + 2 = 10 pts
    expect(screen.getByTestId("lock-confirm-total-points")).toHaveTextContent(
      "10/10",
    );
  });

  it("shows RCDO coverage percentage", () => {
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    // 2 out of 3 have RCDO → 67%
    expect(
      screen.getByTestId("lock-confirm-rcdo-coverage"),
    ).toHaveTextContent("67%");
  });

  it("shows chess piece breakdown", () => {
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("piece-count-king")).toBeInTheDocument();
    expect(screen.getByTestId("piece-count-queen")).toBeInTheDocument();
    expect(screen.getByTestId("piece-count-pawn")).toBeInTheDocument();
    // Bishop, rook, knight should not appear (count=0)
    expect(screen.queryByTestId("piece-count-bishop")).not.toBeInTheDocument();
  });

  it("calls onConfirm when confirm button clicked", () => {
    const onConfirm = vi.fn();
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByTestId("lock-confirm-btn"));
    expect(onConfirm).toHaveBeenCalled();
  });

  it("calls onCancel when cancel button clicked", () => {
    const onCancel = vi.fn();
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );
    fireEvent.click(screen.getByTestId("lock-confirm-cancel"));
    expect(onCancel).toHaveBeenCalled();
  });

  it("disables buttons while locking", () => {
    render(
      <LockConfirmDialog
        commits={threeCommits}
        capacityBudgetPoints={10}
        weekLabel="Week of Mar 24, 2026"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
        isLocking
      />,
    );
    expect(screen.getByTestId("lock-confirm-btn")).toBeDisabled();
    expect(screen.getByTestId("lock-confirm-cancel")).toBeDisabled();
  });
});
