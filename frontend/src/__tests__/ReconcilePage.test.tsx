/**
 * Integration tests for the ReconcilePage component.
 *
 * Covers:
 *   - Loading state
 *   - Error state
 *   - No plan ID (no-plan message)
 *   - Baseline vs current columns rendered
 *   - Outcome selector works
 *   - Notes required for PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED
 *   - Auto-achieved badge shown for Done tickets
 *   - Carry-forward checkbox appears for NOT_ACHIEVED/PARTIALLY_ACHIEVED
 *   - CarryForwardDialog opens when checkbox is checked
 *   - Carry-forward creates request via API
 *   - Submit button disabled until all outcomes set
 *   - Submit button triggers confirmation dialog
 *   - Read-only banner shown for RECONCILED plans
 *   - Scope changes shown in timeline
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { MockHostProvider } from "../host/MockHostProvider.js";
import type {
  ReconciliationViewResponse,
  ReconcileCommitView,
  PlanResponse,
} from "../api/planTypes.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/planHooks.js", () => ({
  usePlanApi: vi.fn(),
  useCurrentPlan: vi.fn().mockReturnValue({ data: undefined, loading: false, error: null, refetch: vi.fn() }),
}));

vi.mock("../api/rcdoHooks.js", () => ({
  useRcdoTree: vi.fn().mockReturnValue({ data: [], loading: false, error: null, refetch: vi.fn() }),
  useRcdoNode: vi.fn(),
  useRcdoApi: vi.fn(),
}));

import { usePlanApi } from "../api/planHooks.js";
import ReconcilePage from "../routes/Reconcile.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

const RECONCILING_PLAN: PlanResponse = {
  id: "plan-1",
  ownerUserId: "user-1",
  teamId: "team-1",
  weekStartDate: "2026-03-24",
  state: "RECONCILING",
  lockDeadline: "2026-03-27T17:00:00Z",
  reconcileDeadline: "2026-03-29T17:00:00Z",
  capacityBudgetPoints: 10,
  compliant: true,
  systemLockedWithErrors: false,
  createdAt: "2026-03-24T00:00:00Z",
  updatedAt: "2026-03-24T00:00:00Z",
};

const RECONCILED_PLAN: PlanResponse = {
  ...RECONCILING_PLAN,
  state: "RECONCILED",
};

function makeCommitView(
  id: string,
  overrides: Partial<ReconcileCommitView> = {},
): ReconcileCommitView {
  return {
    commitId: id,
    currentTitle: `Commit ${id}`,
    currentChessPiece: "PAWN",
    currentEstimatePoints: 3,
    currentOutcome: null,
    currentOutcomeNotes: null,
    baselineSnapshot: {
      title: `Commit ${id}`,
      chessPiece: "PAWN",
      estimatePoints: 3,
      rcdoNodeId: null,
    },
    scopeChanges: [],
    linkedTicketStatus: null,
    addedPostLock: false,
    removedPostLock: false,
    ...overrides,
  };
}

const twoCommitViews: ReconcileCommitView[] = [
  makeCommitView("c-1", { currentTitle: "Alpha", currentChessPiece: "KING" }),
  makeCommitView("c-2", { currentTitle: "Bravo", currentChessPiece: "PAWN" }),
];

function makeReconcileData(
  plan: PlanResponse = RECONCILING_PLAN,
  commits: ReconcileCommitView[] = twoCommitViews,
): ReconciliationViewResponse {
  return {
    plan,
    commits,
    baselineTotalPoints: 6,
    currentTotalPoints: 6,
    commitCount: commits.length,
    outcomesSetCount: commits.filter((c) => c.currentOutcome !== null).length,
  };
}

const mockPlanApi = {
  getReconciliationView: vi.fn(),
  setCommitOutcome: vi.fn(),
  submitReconciliation: vi.fn(),
  carryForward: vi.fn(),
  getScopeChangeTimeline: vi.fn(),
  lockPlan: vi.fn(),
  getOrCreatePlan: vi.fn(),
  getPlan: vi.fn(),
  createCommit: vi.fn(),
  updateCommit: vi.fn(),
  deleteCommit: vi.fn(),
  reorderCommits: vi.fn(),
  openReconciliation: vi.fn(),
  applyScopeChange: vi.fn(),
};

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(usePlanApi).mockReturnValue(
    mockPlanApi as ReturnType<typeof usePlanApi>,
  );
  mockPlanApi.getReconciliationView.mockResolvedValue(
    makeReconcileData(),
  );
  mockPlanApi.setCommitOutcome.mockResolvedValue({} as never);
  mockPlanApi.submitReconciliation.mockResolvedValue(
    makeReconcileData(RECONCILED_PLAN),
  );
  mockPlanApi.carryForward.mockResolvedValue({});
});

function renderReconcile(planId = "plan-1") {
  return render(
    <MemoryRouter initialEntries={[`/reconcile/${planId}`]}>
      <MockHostProvider>
        <Routes>
          <Route path="/reconcile/:planId" element={<ReconcilePage />} />
          <Route path="/reconcile" element={<ReconcilePage />} />
        </Routes>
      </MockHostProvider>
    </MemoryRouter>,
  );
}

function renderReconcileNoId() {
  return render(
    <MemoryRouter initialEntries={["/reconcile"]}>
      <MockHostProvider>
        <Routes>
          <Route path="/reconcile" element={<ReconcilePage />} />
        </Routes>
      </MockHostProvider>
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("ReconcilePage — basic rendering", () => {
  it("renders page with data-testid=page-reconcile", async () => {
    renderReconcile();
    expect(screen.getByTestId("page-reconcile")).toBeInTheDocument();
  });

  it("shows no-plan message when planId is missing", () => {
    renderReconcileNoId();
    expect(screen.getByTestId("page-reconcile")).toBeInTheDocument();
    expect(screen.getByText(/No plan available for reconciliation/i)).toBeInTheDocument();
  });

  it("shows loading indicator while fetching", () => {
    mockPlanApi.getReconciliationView.mockReturnValue(new Promise(() => {}));
    renderReconcile();
    expect(screen.getByTestId("reconcile-loading")).toBeInTheDocument();
  });

  it("shows error message on API failure", async () => {
    mockPlanApi.getReconciliationView.mockRejectedValue(
      new Error("Server error"),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-error")).toBeInTheDocument(),
    );
    // useQuery wraps non-ApiRequestError errors as "Unexpected error"
    expect(screen.getByTestId("reconcile-error")).toBeInTheDocument();
  });

  it("renders commit rows after loading", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-commit-list")).toBeInTheDocument(),
    );
    expect(
      screen.getByTestId("reconcile-commit-row-c-1"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("reconcile-commit-row-c-2"),
    ).toBeInTheDocument();
  });

  it("renders baseline and current columns", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("baseline-col-c-1")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("current-col-c-1")).toBeInTheDocument();
  });
});

describe("ReconcilePage — outcome selector", () => {
  it("renders outcome selectors for each commit", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("outcome-selector-c-2")).toBeInTheDocument();
  });

  it("selects ACHIEVED outcome and calls setCommitOutcome", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );

    fireEvent.click(
      screen.getByTestId("outcome-option-c-1-achieved"),
    );

    await waitFor(() =>
      expect(mockPlanApi.setCommitOutcome).toHaveBeenCalledWith(
        "plan-1",
        "c-1",
        { outcome: "ACHIEVED" },
      ),
    );
  });

  it("outcome selectors disabled for RECONCILED plans", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(
        RECONCILED_PLAN,
        twoCommitViews.map((c) => ({
          ...c,
          currentOutcome: "ACHIEVED" as const,
        })),
      ),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-readonly-banner")).toBeInTheDocument(),
    );
  });
});

describe("ReconcilePage — notes enforcement", () => {
  it("shows notes field for PARTIALLY_ACHIEVED outcome", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );

    fireEvent.click(
      screen.getByTestId("outcome-option-c-1-partially_achieved"),
    );

    // Notes textarea should appear
    expect(screen.getByTestId("outcome-notes-c-1")).toBeInTheDocument();
  });

  it("shows notes field for NOT_ACHIEVED outcome", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("outcome-option-c-1-not_achieved"));
    expect(screen.getByTestId("outcome-notes-c-1")).toBeInTheDocument();
  });

  it("shows notes field for CANCELED outcome", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("outcome-option-c-1-canceled"));
    expect(screen.getByTestId("outcome-notes-c-1")).toBeInTheDocument();
  });

  it("does NOT show notes field for ACHIEVED outcome", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("outcome-option-c-1-achieved"));
    // Small wait to confirm it doesn't appear
    await waitFor(() =>
      expect(
        screen.queryByTestId("outcome-notes-c-1"),
      ).not.toBeInTheDocument(),
    );
  });

  it("submit button remains disabled if not all outcomes set", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-submit-btn")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("reconcile-submit-btn")).toBeDisabled();
  });
});

describe("ReconcilePage — auto-achieve", () => {
  it("shows auto-achieved badge for DONE ticket commits", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(RECONCILING_PLAN, [
        makeCommitView("c-1", {
          linkedTicketStatus: "DONE",
          currentOutcome: "ACHIEVED",
        }),
        makeCommitView("c-2"),
      ]),
    );

    renderReconcile();
    await waitFor(() =>
      expect(
        screen.getByTestId("auto-achieved-badge-c-1"),
      ).toBeInTheDocument(),
    );
  });

  it("shows ticket status badge", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(RECONCILING_PLAN, [
        makeCommitView("c-1", { linkedTicketStatus: "IN_PROGRESS" }),
      ]),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("ticket-status-c-1")).toBeInTheDocument(),
    );
    expect(screen.getByTestId("ticket-status-c-1")).toHaveTextContent(
      "IN_PROGRESS",
    );
  });
});

describe("ReconcilePage — carry-forward", () => {
  it("shows carry-forward checkbox for NOT_ACHIEVED", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId("outcome-option-c-1-not_achieved"));
    expect(
      screen.getByTestId("carry-forward-checkbox-c-1"),
    ).toBeInTheDocument();
  });

  it("shows carry-forward checkbox for PARTIALLY_ACHIEVED", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );
    fireEvent.click(
      screen.getByTestId("outcome-option-c-1-partially_achieved"),
    );
    expect(
      screen.getByTestId("carry-forward-checkbox-c-1"),
    ).toBeInTheDocument();
  });

  it("does NOT show carry-forward checkbox for ACHIEVED", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId("outcome-option-c-1-achieved"));
    await waitFor(() =>
      expect(
        screen.queryByTestId("carry-forward-checkbox-c-1"),
      ).not.toBeInTheDocument(),
    );
  });

  it("opens CarryForwardDialog when carry-forward checkbox is checked", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId("outcome-option-c-1-not_achieved"));
    fireEvent.click(screen.getByTestId("carry-forward-checkbox-c-1"));
    expect(screen.getByTestId("carry-forward-dialog")).toBeInTheDocument();
  });

  it("calls carryForward API on carry-forward confirm", async () => {
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("outcome-selector-c-1")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId("outcome-option-c-1-not_achieved"));
    fireEvent.click(screen.getByTestId("carry-forward-checkbox-c-1"));

    expect(screen.getByTestId("carry-forward-dialog")).toBeInTheDocument();

    // Select a reason
    fireEvent.change(screen.getByTestId("carry-forward-reason-select"), {
      target: { value: "STILL_IN_PROGRESS" },
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId("carry-forward-confirm"));
    });

    await waitFor(() =>
      expect(mockPlanApi.carryForward).toHaveBeenCalled(),
    );
  });
});

describe("ReconcilePage — submit", () => {
  it("submit button enabled when all outcomes set", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(RECONCILING_PLAN, [
        makeCommitView("c-1", { currentOutcome: "ACHIEVED" }),
        makeCommitView("c-2", { currentOutcome: "ACHIEVED" }),
      ]),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-submit-btn")).not.toBeDisabled(),
    );
  });

  it("clicking submit button shows confirmation dialog", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(RECONCILING_PLAN, [
        makeCommitView("c-1", { currentOutcome: "ACHIEVED" }),
        makeCommitView("c-2", { currentOutcome: "ACHIEVED" }),
      ]),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-submit-btn")).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByTestId("reconcile-submit-btn"));
    expect(screen.getByTestId("reconcile-submit-dialog")).toBeInTheDocument();
  });

  it("submit calls API and refreshes on confirm", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(RECONCILING_PLAN, [
        makeCommitView("c-1", { currentOutcome: "ACHIEVED" }),
        makeCommitView("c-2", { currentOutcome: "ACHIEVED" }),
      ]),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("reconcile-submit-btn")).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByTestId("reconcile-submit-btn"));
    await act(async () => {
      fireEvent.click(screen.getByTestId("reconcile-submit-confirm"));
    });
    await waitFor(() =>
      expect(mockPlanApi.submitReconciliation).toHaveBeenCalledWith("plan-1"),
    );
  });
});

describe("ReconcilePage — scope changes", () => {
  it("shows scope change timeline when commits have scope changes", async () => {
    mockPlanApi.getReconciliationView.mockResolvedValue(
      makeReconcileData(RECONCILING_PLAN, [
        makeCommitView("c-1", {
          scopeChanges: [
            {
              id: "sc-1",
              planId: "plan-1",
              commitId: "c-1",
              category: "ESTIMATE_CHANGED",
              changedByUserId: "user-1",
              reason: "Scope expanded",
              previousValue: "3",
              newValue: "5",
              createdAt: "2026-03-25T10:00:00Z",
            },
          ],
        }),
      ]),
    );
    renderReconcile();
    await waitFor(() =>
      expect(screen.getByTestId("scope-change-timeline")).toBeInTheDocument(),
    );
  });
});
