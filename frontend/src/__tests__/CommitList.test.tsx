/**
 * Tests for CommitList component.
 *
 * Covers:
 *   - Renders commits in priority order (by priorityOrder field)
 *   - Keyboard reorder: move-up / move-down buttons update order
 *   - First commit move-up disabled; last commit move-down disabled
 *   - Inline expand shows description, success criteria, carry-forward streak
 *   - Edit / Delete buttons visible in DRAFT state, hidden in LOCKED state
 *   - Empty state message
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CommitList } from "../components/myweek/CommitList.js";
import type { CommitResponse } from "../api/planTypes.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeCommit(
  id: string,
  priorityOrder: number,
  overrides: Partial<CommitResponse> = {},
): CommitResponse {
  return {
    id,
    planId: "plan-1",
    ownerUserId: "user-1",
    title: `Commit ${id}`,
    chessPiece: "PAWN",
    priorityOrder,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

const mockCommits: CommitResponse[] = [
  makeCommit("c-1", 1, { title: "Alpha", chessPiece: "KING", estimatePoints: 3 }),
  makeCommit("c-2", 2, { title: "Bravo", chessPiece: "QUEEN", estimatePoints: 2 }),
  makeCommit("c-3", 3, { title: "Charlie", chessPiece: "PAWN", estimatePoints: 1 }),
];

function renderList(
  commits: CommitResponse[] = mockCommits,
  planState: CommitList["planState"] = "DRAFT",
  overrides: Partial<React.ComponentProps<typeof CommitList>> = {},
) {
  const onReorder = vi.fn();
  const onEdit = vi.fn();
  const onDelete = vi.fn();
  render(
    <CommitList
      commits={commits}
      planState={planState}
      onReorder={onReorder}
      onEdit={onEdit}
      onDelete={onDelete}
      {...overrides}
    />,
  );
  return { onReorder, onEdit, onDelete };
}

// TypeScript helper — CommitList component props type
type CommitList = React.ComponentProps<typeof CommitList>;

// ── Tests: rendering and priority order ──────────────────────────────────────

describe("CommitList — rendering", () => {
  it("renders the commit list container", () => {
    renderList();
    expect(screen.getByTestId("commit-list")).toBeInTheDocument();
  });

  it("renders commits in priority order (rank 1, 2, 3)", () => {
    renderList();
    // Check that rank badges appear in order
    const ranks = screen
      .getAllByTestId(/^priority-rank-/)
      .map((el) => el.textContent);
    expect(ranks).toEqual(["1", "2", "3"]);
  });

  it("sorts commits by priorityOrder even if prop is unsorted", () => {
    const unsorted = [
      makeCommit("c-3", 3, { title: "Third" }),
      makeCommit("c-1", 1, { title: "First" }),
      makeCommit("c-2", 2, { title: "Second" }),
    ];
    renderList(unsorted);
    const titles = screen
      .getAllByTestId(/^commit-title-/)
      .map((el) => el.textContent);
    expect(titles).toEqual(["First", "Second", "Third"]);
  });

  it("shows chess piece icons", () => {
    renderList();
    expect(screen.getByTestId("chess-piece-icon-c-1")).toHaveTextContent("♔");
    expect(screen.getByTestId("chess-piece-icon-c-2")).toHaveTextContent("♕");
    expect(screen.getByTestId("chess-piece-icon-c-3")).toHaveTextContent("♙");
  });

  it("shows estimate badge when commit has estimatePoints", () => {
    renderList();
    expect(screen.getByTestId("estimate-badge-c-1")).toHaveTextContent("3 pt");
  });

  it("shows ticket badge when commit has workItemId", () => {
    const commits = [
      makeCommit("c-1", 1, { title: "With Ticket", workItemId: "WC-42" }),
    ];
    renderList(commits);
    expect(screen.getByTestId("ticket-badge-c-1")).toHaveTextContent("WC-42");
  });

  it("shows RCDO path when rcdoLabels map is provided", () => {
    const commits = [
      makeCommit("c-1", 1, { title: "With RCDO", rcdoNodeId: "rcdo-1" }),
    ];
    renderList(commits, "DRAFT", { rcdoLabels: { "rcdo-1": "Close 10 Deals" } });
    expect(screen.getByTestId("rcdo-path-c-1")).toHaveTextContent(
      "Close 10 Deals",
    );
  });

  it("shows empty state when commits array is empty", () => {
    renderList([]);
    expect(screen.getByTestId("commit-list-empty")).toBeInTheDocument();
    expect(screen.getByTestId("commit-list-empty")).toHaveTextContent(
      "No commits yet",
    );
  });

  it("shows add button in draft empty state when onAddCommit is provided", () => {
    const onAddCommit = vi.fn();
    renderList([], "DRAFT", { onAddCommit });
    fireEvent.click(screen.getByTestId("commit-list-add-btn"));
    expect(onAddCommit).toHaveBeenCalledTimes(1);
  });

  it("shows different empty message in non-DRAFT state", () => {
    renderList([], "LOCKED");
    expect(screen.getByTestId("commit-list-empty")).toHaveTextContent(
      "No commits in this plan",
    );
    expect(screen.queryByTestId("commit-list-add-btn")).not.toBeInTheDocument();
  });
});

// ── Tests: drag-and-drop keyboard alternative (move-up/down) ─────────────────

describe("CommitList — keyboard reorder (move-up/move-down)", () => {
  it("move-up button is disabled for the first commit", () => {
    renderList();
    expect(screen.getByTestId("move-up-c-1")).toBeDisabled();
  });

  it("move-down button is disabled for the last commit", () => {
    renderList();
    expect(screen.getByTestId("move-down-c-3")).toBeDisabled();
  });

  it("move-up button is enabled for commits that are not first", () => {
    renderList();
    expect(screen.getByTestId("move-up-c-2")).not.toBeDisabled();
    expect(screen.getByTestId("move-up-c-3")).not.toBeDisabled();
  });

  it("move-down button is enabled for commits that are not last", () => {
    renderList();
    expect(screen.getByTestId("move-down-c-1")).not.toBeDisabled();
    expect(screen.getByTestId("move-down-c-2")).not.toBeDisabled();
  });

  it("clicking move-up calls onReorder with updated order", () => {
    const { onReorder } = renderList();
    // Move c-2 up (currently rank 2)
    fireEvent.click(screen.getByTestId("move-up-c-2"));
    // c-2 should now be first, c-1 second, c-3 third
    expect(onReorder).toHaveBeenCalledWith(["c-2", "c-1", "c-3"]);
  });

  it("clicking move-down calls onReorder with updated order", () => {
    const { onReorder } = renderList();
    // Move c-2 down (currently rank 2)
    fireEvent.click(screen.getByTestId("move-down-c-2"));
    // c-1 should be first, c-3 second, c-2 third
    expect(onReorder).toHaveBeenCalledWith(["c-1", "c-3", "c-2"]);
  });

  it("after move-up, rank badges update to reflect new order", () => {
    renderList();
    fireEvent.click(screen.getByTestId("move-up-c-2"));
    // Now c-2 is rank 1, c-1 is rank 2
    expect(screen.getByTestId("priority-rank-c-2")).toHaveTextContent("1");
    expect(screen.getByTestId("priority-rank-c-1")).toHaveTextContent("2");
  });

  it("keyboard reorder buttons are accessible with aria-label", () => {
    renderList();
    expect(screen.getByLabelText("Move up: Alpha")).toBeInTheDocument();
    expect(screen.getByLabelText("Move down: Bravo")).toBeInTheDocument();
  });
});

// ── Tests: inline expand/collapse ────────────────────────────────────────────

describe("CommitList — inline expand", () => {
  it("detail panel is hidden by default", () => {
    renderList();
    expect(screen.queryByTestId("commit-detail-c-1")).not.toBeInTheDocument();
  });

  it("clicking the commit title expands the detail panel", () => {
    renderList();
    fireEvent.click(screen.getByTestId("commit-title-c-1"));
    expect(screen.getByTestId("commit-detail-c-1")).toBeInTheDocument();
  });

  it("expanded detail shows description when present", () => {
    const commits = [
      makeCommit("c-1", 1, { title: "With Desc", description: "A detailed description" }),
    ];
    renderList(commits);
    fireEvent.click(screen.getByTestId("commit-title-c-1"));
    expect(screen.getByTestId("commit-description-c-1")).toHaveTextContent(
      "A detailed description",
    );
  });

  it("expanded detail shows success criteria when present", () => {
    const commits = [
      makeCommit("c-1", 1, {
        chessPiece: "KING",
        successCriteria: "Revenue > $1M",
      }),
    ];
    renderList(commits);
    fireEvent.click(screen.getByTestId("commit-title-c-1"));
    expect(screen.getByTestId("commit-sc-c-1")).toHaveTextContent(
      "Revenue > $1M",
    );
  });

  it("expanded detail shows carry-forward streak when > 0", () => {
    const commits = [
      makeCommit("c-1", 1, { carryForwardStreak: 3 }),
    ];
    renderList(commits);
    fireEvent.click(screen.getByTestId("commit-title-c-1"));
    expect(screen.getByTestId("carry-forward-streak-c-1")).toHaveTextContent(
      "3 times",
    );
  });

  it("clicking title again collapses the detail panel", () => {
    renderList();
    fireEvent.click(screen.getByTestId("commit-title-c-1"));
    expect(screen.getByTestId("commit-detail-c-1")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("commit-title-c-1"));
    expect(screen.queryByTestId("commit-detail-c-1")).not.toBeInTheDocument();
  });
});

// ── Tests: DRAFT vs. non-DRAFT state ─────────────────────────────────────────

describe("CommitList — state-based button visibility", () => {
  it("edit and delete buttons are visible in DRAFT state", () => {
    renderList(mockCommits, "DRAFT");
    expect(screen.getByTestId("edit-commit-c-1")).toBeInTheDocument();
    expect(screen.getByTestId("delete-commit-c-1")).toBeInTheDocument();
  });

  it("edit and delete buttons are hidden in LOCKED state", () => {
    renderList(mockCommits, "LOCKED");
    expect(screen.queryByTestId("edit-commit-c-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("delete-commit-c-1")).not.toBeInTheDocument();
  });

  it("move-up/down buttons are hidden in LOCKED state", () => {
    renderList(mockCommits, "LOCKED");
    expect(screen.queryByTestId("move-up-c-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("move-down-c-1")).not.toBeInTheDocument();
  });

  it("drag handle is hidden in LOCKED state", () => {
    renderList(mockCommits, "LOCKED");
    expect(screen.queryByTestId("drag-handle-c-1")).not.toBeInTheDocument();
  });

  it("clicking edit button calls onEdit with the commit", () => {
    const { onEdit } = renderList(mockCommits, "DRAFT");
    fireEvent.click(screen.getByTestId("edit-commit-c-1"));
    expect(onEdit).toHaveBeenCalledWith(
      expect.objectContaining({ id: "c-1" }),
    );
  });

  it("clicking delete button calls onDelete with the commit ID", () => {
    const { onDelete } = renderList(mockCommits, "DRAFT");
    fireEvent.click(screen.getByTestId("delete-commit-c-1"));
    expect(onDelete).toHaveBeenCalledWith("c-1");
  });
});
