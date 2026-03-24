/**
 * Integration tests for the My Week page (route-level component).
 *
 * Covers:
 *   - Page renders with testid
 *   - Week selector navigates weeks
 *   - Loading and error states
 *   - Plan state badge rendered
 *   - Lock button shown in DRAFT, hidden in LOCKED
 *   - Reconcile hint shown in LOCKED state
 *   - Add commit button visible in DRAFT
 *   - CommitForm opens on + Add Commit
 *   - Soft warning: >8 commits
 *   - Soft warning: >40% pawn points
 *   - Capacity meter rendered
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { MockHostProvider } from "../host/MockHostProvider.js";
import type { CommitResponse, PlanResponse } from "../api/planTypes.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/planHooks.js", () => ({
  usePlanApi: vi.fn(),
  useCurrentPlan: vi.fn(),
}));

vi.mock("../api/rcdoHooks.js", () => ({
  useRcdoTree: vi.fn(),
  useRcdoNode: vi.fn(),
  useRcdoApi: vi.fn(),
}));

import { useCurrentPlan, usePlanApi } from "../api/planHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import MyWeek from "../routes/MyWeek.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

const DRAFT_PLAN: PlanResponse = {
  id: "plan-1",
  ownerUserId: "user-dev-1",
  teamId: "team-1",
  weekStartDate: "2026-03-24",
  state: "DRAFT",
  lockDeadline: "2026-03-27T17:00:00Z",
  reconcileDeadline: "2026-03-29T17:00:00Z",
  capacityBudgetPoints: 10,
  compliant: true,
  systemLockedWithErrors: false,
  createdAt: "2026-03-24T00:00:00Z",
  updatedAt: "2026-03-24T00:00:00Z",
};

const LOCKED_PLAN: PlanResponse = {
  ...DRAFT_PLAN,
  state: "LOCKED",
};

function makeCommit(
  id: string,
  priorityOrder: number,
  overrides: Partial<CommitResponse> = {},
): CommitResponse {
  return {
    id,
    planId: "plan-1",
    ownerUserId: "user-dev-1",
    title: `Commit ${id}`,
    chessPiece: "PAWN",
    priorityOrder,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

const twoCommits: CommitResponse[] = [
  makeCommit("c-1", 1, { title: "Alpha", chessPiece: "KING", estimatePoints: 3 }),
  makeCommit("c-2", 2, { title: "Bravo", chessPiece: "PAWN", estimatePoints: 2 }),
];

/** Nine commits — triggers >8 soft warning */
const nineCommits: CommitResponse[] = Array.from({ length: 9 }, (_, i) =>
  makeCommit(`c-${i + 1}`, i + 1, { title: `Commit ${i + 1}` }),
);

/** 2 commits — PAWN 8 pts out of total 10 pts → 80% pawn → triggers warning */
const pawnHeavyCommits: CommitResponse[] = [
  makeCommit("c-1", 1, { chessPiece: "KING", estimatePoints: 2 }),
  makeCommit("c-2", 2, { chessPiece: "PAWN", estimatePoints: 8 }),
];

const mockRcdoTree: RcdoTreeNode[] = [
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

const mockRefetch = vi.fn();
const mockPlanApi = {
  getOrCreatePlan: vi.fn(),
  getPlan: vi.fn(),
  createCommit: vi.fn(),
  updateCommit: vi.fn(),
  deleteCommit: vi.fn(),
  reorderCommits: vi.fn(),
  lockPlan: vi.fn(),
};

function makePlanState(
  plan: PlanResponse,
  commits: CommitResponse[] = [],
): ReturnType<typeof useCurrentPlan> {
  return {
    data: { plan, commits, totalPoints: commits.reduce((s, c) => s + (c.estimatePoints ?? 0), 0) },
    loading: false,
    error: null,
    refetch: mockRefetch,
  };
}

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(DRAFT_PLAN, twoCommits));
  vi.mocked(usePlanApi).mockReturnValue(mockPlanApi as ReturnType<typeof usePlanApi>);
  vi.mocked(useRcdoTree).mockReturnValue({
    data: mockRcdoTree,
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  mockRefetch.mockReset();
  mockPlanApi.lockPlan.mockReset();
  mockPlanApi.createCommit.mockReset();
  mockPlanApi.deleteCommit.mockReset();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <MockHostProvider>
        <MyWeek />
      </MockHostProvider>
    </MemoryRouter>,
  );
}

// ── Tests: basic rendering ────────────────────────────────────────────────────

describe("MyWeekPage — rendering", () => {
  it("renders the page with data-testid=page-my-week", () => {
    renderPage();
    expect(screen.getByTestId("page-my-week")).toBeInTheDocument();
  });

  it("renders the week selector", () => {
    renderPage();
    expect(screen.getByTestId("week-selector")).toBeInTheDocument();
  });

  it("renders the week label", () => {
    renderPage();
    expect(screen.getByTestId("week-label")).toBeInTheDocument();
  });

  it("shows loading state while plan is loading", () => {
    vi.mocked(useCurrentPlan).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("plan-loading")).toBeInTheDocument();
  });

  it("shows error message when plan fails to load", () => {
    vi.mocked(useCurrentPlan).mockReturnValue({
      data: undefined,
      loading: false,
      error: { name: "ApiRequestError", message: "Network error", status: 0 } as never,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("plan-error")).toHaveTextContent("Network error");
  });
});

// ── Tests: week navigation ────────────────────────────────────────────────────

describe("MyWeekPage — week navigation", () => {
  it("shows previous-week and next-week navigation buttons", () => {
    renderPage();
    expect(screen.getByTestId("prev-week-btn")).toBeInTheDocument();
    expect(screen.getByTestId("next-week-btn")).toBeInTheDocument();
  });

  it("shows 'Today' button when on a non-current week", () => {
    renderPage();
    // Go to previous week
    fireEvent.click(screen.getByTestId("prev-week-btn"));
    expect(screen.getByTestId("current-week-btn")).toBeInTheDocument();
  });

  it("hides 'Today' button when on the current week", () => {
    renderPage();
    expect(screen.queryByTestId("current-week-btn")).not.toBeInTheDocument();
  });

  it("'Today' button returns to current week", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("prev-week-btn"));
    fireEvent.click(screen.getByTestId("current-week-btn"));
    expect(screen.queryByTestId("current-week-btn")).not.toBeInTheDocument();
  });
});

// ── Tests: plan state badge ───────────────────────────────────────────────────

describe("MyWeekPage — plan state badge", () => {
  it("renders DRAFT badge for a DRAFT plan", () => {
    renderPage();
    expect(screen.getByTestId("plan-state-badge")).toHaveTextContent("DRAFT");
  });

  it("renders LOCKED badge for a LOCKED plan", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(LOCKED_PLAN, twoCommits),
    );
    renderPage();
    expect(screen.getByTestId("plan-state-badge")).toHaveTextContent("LOCKED");
  });

  it("renders compliant badge for a compliant plan", () => {
    renderPage();
    expect(screen.getByTestId("compliance-badge-ok")).toBeInTheDocument();
  });

  it("renders non-compliant badge for a non-compliant plan", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState({ ...DRAFT_PLAN, compliant: false }, twoCommits),
    );
    renderPage();
    expect(screen.getByTestId("compliance-badge-warn")).toBeInTheDocument();
  });
});

// ── Tests: lock button ────────────────────────────────────────────────────────

describe("MyWeekPage — lock button", () => {
  it("shows lock button in DRAFT state", () => {
    renderPage();
    expect(screen.getByTestId("lock-plan-btn")).toBeInTheDocument();
  });

  it("hides lock button in LOCKED state", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(LOCKED_PLAN, twoCommits),
    );
    renderPage();
    expect(screen.queryByTestId("lock-plan-btn")).not.toBeInTheDocument();
  });

  it("shows reconcile hint in LOCKED state", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(LOCKED_PLAN, twoCommits),
    );
    renderPage();
    expect(screen.getByTestId("reconcile-hint")).toBeInTheDocument();
  });

  it("calls lockPlan when lock button is clicked", async () => {
    mockPlanApi.lockPlan.mockResolvedValue({ success: true });
    renderPage();
    fireEvent.click(screen.getByTestId("lock-plan-btn"));
    await waitFor(() => expect(mockPlanApi.lockPlan).toHaveBeenCalledWith("plan-1"));
  });
});

// ── Tests: add commit button ──────────────────────────────────────────────────

describe("MyWeekPage — add commit button", () => {
  it("shows + Add Commit button in DRAFT state", () => {
    renderPage();
    expect(screen.getByTestId("add-commit-btn")).toBeInTheDocument();
  });

  it("hides + Add Commit button in LOCKED state", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(LOCKED_PLAN, twoCommits),
    );
    renderPage();
    expect(screen.queryByTestId("add-commit-btn")).not.toBeInTheDocument();
  });

  it("opens CommitForm modal when + Add Commit is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
  });

  it("closes CommitForm when Cancel is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByTestId("commit-form-modal")).not.toBeInTheDocument();
  });
});

// ── Tests: capacity meter ─────────────────────────────────────────────────────

describe("MyWeekPage — capacity meter", () => {
  it("renders the capacity meter", () => {
    renderPage();
    expect(screen.getByTestId("capacity-meter")).toBeInTheDocument();
  });

  it("shows correct capacity tally (5 pts out of 10)", () => {
    // twoCommits has 3 + 2 = 5 pts
    renderPage();
    expect(screen.getByTestId("capacity-tally")).toHaveTextContent("5 / 10 pts");
  });
});

describe("MyWeekPage — RCDO path labels", () => {
  it("renders the full RCDO path for linked commits", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(DRAFT_PLAN, [
        makeCommit("c-1", 1, {
          title: "Alpha",
          chessPiece: "KING",
          estimatePoints: 3,
          rcdoNodeId: "rcdo-3",
        }),
      ]),
    );

    renderPage();
    expect(screen.getByTestId("rcdo-path-c-1")).toHaveTextContent(
      "Grow Revenue > Enterprise Sales > Close 10 Deals",
    );
  });
});

// ── Tests: soft warnings ──────────────────────────────────────────────────────

describe("MyWeekPage — soft warnings", () => {
  it("does NOT show soft warnings panel when commit count ≤ 8", () => {
    renderPage(); // twoCommits
    expect(
      screen.queryByTestId("warning-too-many-commits"),
    ).not.toBeInTheDocument();
  });

  it("shows 'too many commits' warning when > 8 active commits", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(DRAFT_PLAN, nineCommits),
    );
    renderPage();
    expect(
      screen.getByTestId("warning-too-many-commits"),
    ).toBeInTheDocument();
  });

  it("does NOT show pawn-heavy warning when pawns are under 40%", () => {
    renderPage(); // twoCommits: King(3) + Pawn(2) → 2/5 = 40% ≤ threshold
    // 40% is exactly the threshold; should not trigger (> 40% triggers)
    expect(
      screen.queryByTestId("warning-pawn-heavy"),
    ).not.toBeInTheDocument();
  });

  it("shows pawn-heavy warning when pawn points > 40% of total", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(DRAFT_PLAN, pawnHeavyCommits),
    );
    renderPage();
    expect(screen.getByTestId("warning-pawn-heavy")).toBeInTheDocument();
    expect(screen.getByTestId("warning-pawn-heavy")).toHaveTextContent("80%");
  });
});
