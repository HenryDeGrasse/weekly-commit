/**
 * Tests for CommitForm component.
 *
 * Covers:
 *   - Title required validation
 *   - Chess piece required validation
 *   - Chess piece limit enforcement (max 1 King, max 2 Queens)
 *   - Success criteria required for KING / QUEEN
 *   - Estimate points segmented control selection
 *   - RCDO picker toggle and selection
 *   - Carry-forward provenance banner for commits with streak > 0
 *   - Form submission with correct payload
 *   - Cancel button
 */
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { CommitForm } from "../components/myweek/CommitForm.js";
import type { CommitResponse, CreateCommitPayload } from "../api/planTypes.js";
import type { RcdoTreeNode as RcdoTreeNodeType } from "../api/rcdoTypes.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

const mockRcdoTree: RcdoTreeNodeType[] = [
  {
    id: "rcdo-1",
    nodeType: "RALLY_CRY",
    status: "ACTIVE",
    title: "Grow Revenue",
    children: [
      {
        id: "rcdo-2",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Enterprise Sales",
        children: [
          {
            id: "rcdo-3",
            nodeType: "OUTCOME",
            status: "ACTIVE",
            title: "Close 10 Deals",
            children: [],
          },
        ],
      },
    ],
  },
];

function makeCommit(overrides: Partial<CommitResponse> = {}): CommitResponse {
  return {
    id: "c-1",
    planId: "plan-1",
    ownerUserId: "user-1",
    title: "Existing commit",
    chessPiece: "PAWN",
    priorityOrder: 1,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

interface CreateFormOverrides {
  rcdoTree?: RcdoTreeNodeType[];
  existingCommits?: CommitResponse[];
  onSubmit?: (payload: CreateCommitPayload) => Promise<void>;
  onCancel?: () => void;
}

function renderCreateForm(overrides: CreateFormOverrides = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined) as (
    payload: CreateCommitPayload,
  ) => Promise<void>;
  const onCancel = vi.fn();
  render(
    <CommitForm
      mode="create"
      rcdoTree={overrides.rcdoTree ?? mockRcdoTree}
      existingCommits={overrides.existingCommits ?? []}
      onSubmit={overrides.onSubmit ?? onSubmit}
      onCancel={overrides.onCancel ?? onCancel}
    />,
  );
  return { onSubmit, onCancel };
}

/** Select a chess piece by clicking its radio option. */
function selectChessPiece(piece: string) {
  const option = screen.getByTestId(`chess-piece-option-${piece.toLowerCase()}`);
  const radio = option.querySelector("input[type=radio]");
  if (radio) fireEvent.click(radio);
}

/** Fill in the title field. */
function fillTitle(title: string) {
  fireEvent.change(screen.getByTestId("commit-form-title"), {
    target: { value: title },
  });
}

// ── Tests: required field validation ─────────────────────────────────────────

describe("CommitForm — validation", () => {
  it("renders the form dialog", () => {
    renderCreateForm();
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
  });

  it("renders the form with aria-label 'New commit'", () => {
    renderCreateForm();
    expect(screen.getByRole("form", { name: "New commit" })).toBeInTheDocument();
  });

  it("shows title required error when submitted empty", async () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => {
      expect(screen.getAllByRole("alert")[0]).toHaveTextContent(
        "Title is required",
      );
    });
  });

  it("shows chess piece required error when submitted without selection", async () => {
    renderCreateForm();
    fillTitle("My commit");
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => {
      expect(
        screen.getByText(/Chess piece is required/),
      ).toBeInTheDocument();
    });
  });

  it("does not call onSubmit when title is empty", async () => {
    const { onSubmit } = renderCreateForm();
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => {
      expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("shows success criteria error for KING when criteria is empty", async () => {
    renderCreateForm();
    fillTitle("King commit");
    selectChessPiece("KING");
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => {
      expect(
        screen.getByText(/Success criteria is required for King \/ Queen/),
      ).toBeInTheDocument();
    });
  });

  it("shows success criteria error for QUEEN when criteria is empty", async () => {
    renderCreateForm();
    fillTitle("Queen commit");
    selectChessPiece("QUEEN");
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => {
      expect(
        screen.getByText(/Success criteria is required for King \/ Queen/),
      ).toBeInTheDocument();
    });
  });

  it("does NOT require success criteria for PAWN", async () => {
    const { onSubmit } = renderCreateForm();
    fillTitle("Pawn commit");
    selectChessPiece("PAWN");
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    // Should NOT have success criteria error
    expect(
      screen.queryByText(/Success criteria is required/),
    ).not.toBeInTheDocument();
  });

  it("allows KING when success criteria is filled", async () => {
    const { onSubmit } = renderCreateForm();
    fillTitle("King commit");
    selectChessPiece("KING");
    fireEvent.change(screen.getByTestId("commit-form-success-criteria"), {
      target: { value: "Revenue doubles" },
    });
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  });
});

// ── Tests: chess piece limit enforcement ─────────────────────────────────────

describe("CommitForm — chess piece limit enforcement", () => {
  it("shows King limit message when plan already has a King", () => {
    renderCreateForm({
      existingCommits: [
        makeCommit({ id: "existing-king", chessPiece: "KING" }),
      ],
    });
    expect(
      screen.getByTestId("chess-piece-limit-king"),
    ).toHaveTextContent("Max 1 King already used");
  });

  it("King radio is disabled when plan already has a King", () => {
    renderCreateForm({
      existingCommits: [
        makeCommit({ id: "existing-king", chessPiece: "KING" }),
      ],
    });
    const kingOption = screen.getByTestId("chess-piece-option-king");
    const radio = kingOption.querySelector("input[type=radio]") as HTMLInputElement;
    expect(radio).toBeDisabled();
  });

  it("shows Queen limit message when plan already has 2 Queens", () => {
    renderCreateForm({
      existingCommits: [
        makeCommit({ id: "queen-1", chessPiece: "QUEEN" }),
        makeCommit({ id: "queen-2", chessPiece: "QUEEN" }),
      ],
    });
    expect(
      screen.getByTestId("chess-piece-limit-queen"),
    ).toHaveTextContent("Max 2 Queens already used");
  });

  it("Queen radio is disabled when plan already has 2 Queens", () => {
    renderCreateForm({
      existingCommits: [
        makeCommit({ id: "queen-1", chessPiece: "QUEEN" }),
        makeCommit({ id: "queen-2", chessPiece: "QUEEN" }),
      ],
    });
    const queenOption = screen.getByTestId("chess-piece-option-queen");
    const radio = queenOption.querySelector("input[type=radio]") as HTMLInputElement;
    expect(radio).toBeDisabled();
  });

  it("does NOT show King limit when plan has no Kings", () => {
    renderCreateForm();
    expect(
      screen.queryByTestId("chess-piece-limit-king"),
    ).not.toBeInTheDocument();
  });

  it("does NOT disable King when the commit being edited is the existing King", () => {
    // Edit mode: the existing commit IS the king — it shouldn't count against limit
    render(
      <CommitForm
        mode="edit"
        commit={makeCommit({ id: "my-king", chessPiece: "KING", title: "My King" })}
        rcdoTree={mockRcdoTree}
        existingCommits={[
          makeCommit({ id: "my-king", chessPiece: "KING", title: "My King" }),
        ]}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    const kingOption = screen.getByTestId("chess-piece-option-king");
    const radio = kingOption.querySelector("input[type=radio]") as HTMLInputElement;
    // Should NOT be disabled — this is the same commit
    expect(radio).not.toBeDisabled();
  });
});

// ── Tests: estimate points ────────────────────────────────────────────────────

describe("CommitForm — estimate points", () => {
  it("renders all valid estimate point buttons (1, 2, 3, 5, 8)", () => {
    renderCreateForm();
    [1, 2, 3, 5, 8].forEach((pts) => {
      expect(screen.getByTestId(`estimate-btn-${pts}`)).toBeInTheDocument();
    });
  });

  it("clicking an estimate button marks it as pressed", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("estimate-btn-3"));
    expect(screen.getByTestId("estimate-btn-3")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("clicking the same estimate button twice deselects it", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("estimate-btn-3"));
    fireEvent.click(screen.getByTestId("estimate-btn-3"));
    expect(screen.getByTestId("estimate-btn-3")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });
});

// ── Tests: RCDO picker ────────────────────────────────────────────────────────

describe("CommitForm — RCDO picker", () => {
  it("RCDO picker is collapsed by default", () => {
    renderCreateForm();
    expect(screen.queryByTestId("rcdo-picker-panel")).not.toBeInTheDocument();
  });

  it("clicking Browse opens the RCDO picker panel", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    expect(screen.getByTestId("rcdo-picker-panel")).toBeInTheDocument();
  });

  it("RCDO tree is shown in the picker panel", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    expect(screen.getByTestId("rcdo-tree-view")).toBeInTheDocument();
  });

  it("selecting a valid Outcome node closes the picker and shows selected node", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    fireEvent.click(screen.getByTestId("tree-node-rcdo-3"));
    // Picker should close
    expect(screen.queryByTestId("rcdo-picker-panel")).not.toBeInTheDocument();
    // Selected node badge should appear
    expect(screen.getByTestId("rcdo-selected-node")).toHaveTextContent(
      "Close 10 Deals",
    );
  });

  it("blocks selecting a Rally Cry in the RCDO picker", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    fireEvent.click(screen.getByTestId("tree-node-rcdo-1"));

    expect(screen.getByTestId("rcdo-picker-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("rcdo-selected-node")).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "Select an Outcome, or a Defining Objective with no active Outcomes",
      ),
    ).toBeInTheDocument();
  });

  it("blocks selecting a Defining Objective that has active outcomes", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    fireEvent.click(screen.getByTestId("tree-node-rcdo-2"));

    expect(screen.getByTestId("rcdo-picker-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("rcdo-selected-node")).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "Select an Outcome, or a Defining Objective with no active Outcomes",
      ),
    ).toBeInTheDocument();
  });

  it("clearing the RCDO selection removes the selected node badge", () => {
    renderCreateForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    fireEvent.click(screen.getByTestId("tree-node-rcdo-3"));
    expect(screen.getByTestId("rcdo-selected-node")).toBeInTheDocument();
    // Click the clear button
    fireEvent.click(screen.getByLabelText("Clear RCDO link"));
    expect(screen.queryByTestId("rcdo-selected-node")).not.toBeInTheDocument();
  });
});

// ── Tests: carry-forward banner ───────────────────────────────────────────────

describe("CommitForm — carry-forward banner", () => {
  it("does NOT show carry-forward banner for new commits (create mode)", () => {
    renderCreateForm();
    expect(
      screen.queryByTestId("carry-forward-banner"),
    ).not.toBeInTheDocument();
  });

  it("does NOT show carry-forward banner when streak is 0", () => {
    render(
      <CommitForm
        mode="edit"
        commit={makeCommit({ carryForwardStreak: 0 })}
        rcdoTree={mockRcdoTree}
        existingCommits={[]}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(
      screen.queryByTestId("carry-forward-banner"),
    ).not.toBeInTheDocument();
  });

  it("shows carry-forward banner when streak is > 0", () => {
    render(
      <CommitForm
        mode="edit"
        commit={makeCommit({ carryForwardStreak: 2 })}
        rcdoTree={mockRcdoTree}
        existingCommits={[]}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("carry-forward-banner")).toBeInTheDocument();
    expect(screen.getByTestId("carry-forward-banner")).toHaveTextContent(
      "2 times",
    );
  });

  it("shows singular 'time' when streak is 1", () => {
    render(
      <CommitForm
        mode="edit"
        commit={makeCommit({ carryForwardStreak: 1 })}
        rcdoTree={mockRcdoTree}
        existingCommits={[]}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId("carry-forward-banner")).toHaveTextContent(
      "1 time",
    );
  });
});

// ── Tests: submission payload ─────────────────────────────────────────────────

describe("CommitForm — submission", () => {
  it("calls onSubmit with correct payload for PAWN", async () => {
    const { onSubmit } = renderCreateForm();
    fillTitle("My pawn commit");
    selectChessPiece("PAWN");
    fireEvent.click(screen.getByTestId("estimate-btn-2"));
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "My pawn commit",
        chessPiece: "PAWN",
        estimatePoints: 2,
      }),
    );
  });

  it("calls onCancel when Cancel button clicked", () => {
    const { onCancel } = renderCreateForm();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("calls onCancel when close (✕) button clicked", () => {
    const { onCancel } = renderCreateForm();
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("shows API error when onSubmit rejects", async () => {
    render(
      <CommitForm
        mode="create"
        rcdoTree={mockRcdoTree}
        existingCommits={[]}
        onSubmit={vi.fn().mockRejectedValue(new Error("Server error")) as unknown as (p: CreateCommitPayload) => Promise<void>}
        onCancel={vi.fn()}
      />,
    );
    fillTitle("Failing commit");
    selectChessPiece("PAWN");
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Server error");
    });
  });
});
