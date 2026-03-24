/**
 * Tests for PreLockValidationPanel component.
 *
 * Covers:
 *   - Shows "all clear" state when no errors or warnings
 *   - Renders hard errors section when errors present
 *   - Lock is blocked when hard errors exist (no continue button)
 *   - Shows soft warnings (derived from commits)
 *   - AI lint placeholder always rendered
 *   - Loading state
 */
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PreLockValidationPanel } from "../components/lock/PreLockValidationPanel.js";
import type { LockValidationError, CommitResponse } from "../api/planTypes.js";

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

const twoCommits: CommitResponse[] = [
  makeCommit("c-1", {
    chessPiece: "KING",
    estimatePoints: 3,
    rcdoNodeId: "r1",
    successCriteria: "Ship the critical deliverable",
  }),
  makeCommit("c-2", { chessPiece: "PAWN", estimatePoints: 2, rcdoNodeId: "r2" }),
];

describe("PreLockValidationPanel", () => {
  it("shows all-clear state when no errors and no warnings", () => {
    render(
      <PreLockValidationPanel
        errors={[]}
        commits={twoCommits}
      />,
    );
    expect(screen.getByTestId("pre-lock-validation-panel")).toBeInTheDocument();
    expect(screen.getByTestId("pre-lock-validation-ok")).toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-hard-errors")).not.toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-soft-warnings")).not.toBeInTheDocument();
  });

  it("shows loading state when isLoading=true", () => {
    render(
      <PreLockValidationPanel
        errors={[]}
        commits={[]}
        isLoading
      />,
    );
    expect(screen.getByTestId("pre-lock-validation-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-validation-ok")).not.toBeInTheDocument();
  });

  it("renders hard errors section with error items", () => {
    const errors: LockValidationError[] = [
      { field: "commit[c-1].rcdoNodeId", message: "Primary RCDO link is required" },
      { field: "commit[c-2].estimatePoints", message: "Estimate points are required" },
    ];
    render(<PreLockValidationPanel errors={errors} commits={twoCommits} />);

    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    const errorItems = screen.getAllByTestId("hard-error-item");
    expect(errorItems).toHaveLength(2);
    expect(errorItems[0]).toHaveTextContent("Primary RCDO link is required");
    expect(errorItems[1]).toHaveTextContent("Estimate points are required");
  });

  it("does not show all-clear when hard errors present", () => {
    const errors: LockValidationError[] = [
      { field: "commits", message: "At least one commit is required" },
    ];
    render(<PreLockValidationPanel errors={errors} commits={[]} />);
    expect(screen.queryByTestId("pre-lock-validation-ok")).not.toBeInTheDocument();
  });

  it("shows soft warning for >8 commits", () => {
    const manyCommits = Array.from({ length: 9 }, (_, i) =>
      makeCommit(`c-${i}`, { rcdoNodeId: `r${i}`, estimatePoints: 1 }),
    );
    render(<PreLockValidationPanel errors={[]} commits={manyCommits} />);
    expect(screen.getByTestId("pre-lock-soft-warnings")).toBeInTheDocument();
    const items = screen.getAllByTestId("soft-warning-item");
    expect(items.some((el) => el.textContent?.includes("9 commits"))).toBe(true);
  });

  it("shows soft warning for pawn-heavy commits (>40%)", () => {
    const commits: CommitResponse[] = [
      makeCommit("c-1", {
        chessPiece: "KING",
        estimatePoints: 2,
        rcdoNodeId: "r1",
        successCriteria: "Complete the must-win goal",
      }),
      makeCommit("c-2", { chessPiece: "PAWN", estimatePoints: 8, rcdoNodeId: "r2" }),
    ];
    render(<PreLockValidationPanel errors={[]} commits={commits} />);
    expect(screen.getByTestId("pre-lock-soft-warnings")).toBeInTheDocument();
    const items = screen.getAllByTestId("soft-warning-item");
    expect(items.some((el) => el.textContent?.includes("80%"))).toBe(true);
  });

  it("shows hard error for commits missing RCDO", () => {
    const commits: CommitResponse[] = [
      makeCommit("c-1", { chessPiece: "KING", estimatePoints: 3 }), // no rcdoNodeId
    ];
    render(<PreLockValidationPanel errors={[]} commits={commits} />);
    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    expect(screen.getByText(/Primary RCDO link is required at lock time/)).toBeInTheDocument();
  });

  it("always renders the AI lint placeholder", () => {
    render(<PreLockValidationPanel errors={[]} commits={twoCommits} />);
    expect(screen.getByTestId("pre-lock-ai-lint-placeholder")).toBeInTheDocument();
  });

  it("renders both hard errors and soft warnings simultaneously", () => {
    const errors: LockValidationError[] = [
      { field: "commits.king", message: "Maximum 1 King commit per week" },
    ];
    const commits = Array.from({ length: 9 }, (_, i) =>
      makeCommit(`c-${i}`, { rcdoNodeId: `r${i}`, estimatePoints: 1 }),
    );
    render(<PreLockValidationPanel errors={errors} commits={commits} />);
    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    expect(screen.getByTestId("pre-lock-soft-warnings")).toBeInTheDocument();
  });
});
