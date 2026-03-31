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
 *   - Inline AI lint panel shown in DRAFT state, hidden in LOCKED
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

vi.mock("../api/ragHooks.js", () => ({
  useTeamInsights: vi.fn(() => ({ data: undefined, loading: false, error: null, refetch: vi.fn() })),
  usePlanInsights: vi.fn(() => ({ data: undefined, loading: false, error: null, refetch: vi.fn() })),
  useSemanticQuery: vi.fn(() => ({ mutate: vi.fn(), data: undefined, loading: false, error: null })),
  useRagApi: vi.fn(),
}));

vi.mock("../api/aiHooks.js", () => ({
  useAiApi: vi.fn(() => ({
    recordFeedback: vi.fn(),
    commitDraftAssist: vi.fn(),
    commitFromFreeform: vi.fn(),
    rcdoSuggest: vi.fn(),
  })),
  // Default: AI not available → "Add Commit" falls back to direct CommitForm
  useAiStatus: vi.fn(() => ({ data: { available: false }, loading: false, error: null })),
  useAutoReconcileAssist: vi.fn(() => ({ data: undefined, loading: false, error: null })),
  useManagerAiSummary: vi.fn(() => ({ data: undefined, loading: false, error: null })),
  // Default: no risk signals (banner renders nothing)
  useRiskSignals: vi.fn(() => ({ data: undefined, loading: false, error: null, refetch: vi.fn() })),
  // WhatIfPanel calls this; return a stable mock so no TypeError inside AiErrorBoundary
  useWhatIfApi: vi.fn(() => ({ simulate: vi.fn() })),
  usePlanEvidence: vi.fn(() => ({ data: undefined, loading: false, error: null })),
}));

vi.mock("../api/calibrationHooks.js", () => ({
  useCalibration: vi.fn(() => ({ data: undefined, loading: false, error: null, refetch: vi.fn() })),
}));

vi.mock("../api/recommendationHooks.js", () => ({
  usePlanRecommendations: vi.fn(() => ({ data: [], loading: false, error: null, refetch: vi.fn() })),
  useRecommendationApi: vi.fn(() => ({ getRecommendations: vi.fn(), refreshRecommendations: vi.fn() })),
}));

vi.mock("../api/ticketHooks.js", () => ({
  usePlanHistory: vi.fn(() => ({ data: null, loading: false })),
  useCarryForwardLineage: vi.fn(() => ({ data: null, loading: false })),
  useTicketApi: vi.fn(() => ({ createTicket: vi.fn() })),
}));

import { useCurrentPlan, usePlanApi } from "../api/planHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { usePlanHistory } from "../api/ticketHooks.js";
import * as aiHooks from "../api/aiHooks.js";
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
  makeCommit("c-1", 1, {
    title: "Alpha",
    chessPiece: "KING",
    estimatePoints: 3,
    rcdoNodeId: "rcdo-3",
    successCriteria: "Launch the critical initiative",
  }),
  makeCommit("c-2", 2, {
    title: "Bravo",
    chessPiece: "PAWN",
    estimatePoints: 2,
    rcdoNodeId: "rcdo-3",
  }),
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
  applyScopeChange: vi.fn(),
  getScopeChangeTimeline: vi.fn(),
  getReconciliationView: vi.fn(),
  setCommitOutcome: vi.fn(),
  submitReconciliation: vi.fn(),
  openReconciliation: vi.fn(),
  carryForward: vi.fn(),
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
    // Use commits that actually fail pre-lock validation (KING missing successCriteria)
    const nonCompliantCommits = [
      makeCommit("c-1", 1, { title: "Alpha", chessPiece: "KING", estimatePoints: 3, rcdoNodeId: "rcdo-3" }),
      makeCommit("c-2", 2, { title: "Bravo", chessPiece: "PAWN", estimatePoints: 2, rcdoNodeId: "rcdo-3" }),
    ];
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState({ ...DRAFT_PLAN, compliant: false }, nonCompliantCommits),
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

  it("opens pre-lock validation panel when lock button is clicked", async () => {
    renderPage();
    fireEvent.click(screen.getByTestId("lock-plan-btn"));
    expect(screen.getByTestId("pre-lock-validation-section")).toBeInTheDocument();
  });

  it("blocks continue when pre-lock hard errors are present", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState(DRAFT_PLAN, [
        makeCommit("c-1", 1, { title: "Alpha", chessPiece: "KING" }),
      ]),
    );

    renderPage();
    fireEvent.click(screen.getByTestId("lock-plan-btn"));

    expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument();
    expect(screen.queryByTestId("pre-lock-continue-btn")).not.toBeInTheDocument();
  });

  it("calls lockPlan after confirming through lock flow", async () => {
    mockPlanApi.lockPlan.mockResolvedValue({
      success: true,
      plan: { plan: DRAFT_PLAN, commits: [], totalPoints: 0 },
      errors: [],
    });
    renderPage();
    // Step 1: click lock button → validation panel opens
    fireEvent.click(screen.getByTestId("lock-plan-btn"));
    expect(screen.getByTestId("pre-lock-validation-section")).toBeInTheDocument();
    // Step 2: continue is available because the fixture already satisfies lock requirements
    await waitFor(() =>
      expect(screen.getByTestId("pre-lock-continue-btn")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId("pre-lock-continue-btn"));
    // Step 3: lock confirm dialog
    expect(screen.getByTestId("lock-confirm-dialog")).toBeInTheDocument();
    // Step 4: confirm lock
    fireEvent.click(screen.getByTestId("lock-confirm-btn"));
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

  it("opens CommitForm modal when + Add Commit is clicked (AI unavailable — direct form)", () => {
    // Default mock: useAiStatus returns { available: false }
    // aiComposerEnabled = false → Add Commit opens CommitForm directly
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

  it("does NOT show 'Add manually' link when AI is unavailable", () => {
    // With available: false, composer is disabled → no secondary link needed
    renderPage();
    expect(screen.queryByTestId("add-commit-manually-btn")).not.toBeInTheDocument();
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

// ── Tests: system-locked banner ───────────────────────────────────────────────

describe("MyWeekPage — system-locked banner", () => {
  it("shows auto-lock banner when systemLockedWithErrors is true", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(
      makePlanState({ ...LOCKED_PLAN, systemLockedWithErrors: true }, twoCommits),
    );
    renderPage();
    expect(screen.getByTestId("auto-lock-banner")).toBeInTheDocument();
    expect(screen.getByTestId("auto-lock-banner")).toHaveTextContent("System-locked with errors");
  });

  it("does NOT show auto-lock banner when systemLockedWithErrors is false", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    expect(screen.queryByTestId("auto-lock-banner")).not.toBeInTheDocument();
  });
});

// ── Tests: post-lock add commit ───────────────────────────────────────────────

describe("MyWeekPage — post-lock add commit", () => {
  it("shows post-lock Add Commit button in LOCKED state", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    expect(screen.getByTestId("post-lock-add-commit-btn")).toBeInTheDocument();
  });

  it("opens CommitForm when post-lock Add Commit is clicked", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    fireEvent.click(screen.getByTestId("post-lock-add-commit-btn"));
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
  });
});

// ── Tests: delete confirm dialog ──────────────────────────────────────────────

describe("MyWeekPage — delete flow", () => {
  it("opens delete confirm dialog when delete is clicked on a commit", () => {
    renderPage();
    // Expand commit detail to access delete button
    fireEvent.click(screen.getByTestId("delete-commit-c-1"));
    expect(screen.getByTestId("delete-confirm-dialog")).toBeInTheDocument();
  });

  it("calls deleteCommit when delete is confirmed", async () => {
    mockPlanApi.deleteCommit.mockResolvedValue(undefined);
    renderPage();
    fireEvent.click(screen.getByTestId("delete-commit-c-1"));
    fireEvent.click(screen.getByTestId("delete-confirm-btn"));
    await waitFor(() =>
      expect(mockPlanApi.deleteCommit).toHaveBeenCalledWith("plan-1", "c-1"),
    );
  });

  it("closes delete dialog when cancel is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("delete-commit-c-1"));
    expect(screen.getByTestId("delete-confirm-dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByTestId("delete-confirm-dialog")).not.toBeInTheDocument();
  });
});

// ── Tests: plan history toggle ────────────────────────────────────────────────

describe("MyWeekPage — plan history", () => {
  it("shows plan history section with toggle button (collapsed by default)", () => {
    renderPage();
    const btn = screen.getByTestId("toggle-plan-history-btn");
    expect(btn).toBeInTheDocument();
    // CollapsibleSection starts collapsed (defaultExpanded=false)
    expect(btn).toHaveAttribute("aria-expanded", "false");
    // Title text is "Plan History"
    expect(btn).toHaveTextContent("Plan History");
  });

  it("toggles history visibility on button click (aria-expanded changes)", () => {
    vi.mocked(usePlanHistory).mockReturnValue({
      data: [],
      loading: false,
      error: null,
      refetch: vi.fn(),
    } as ReturnType<typeof usePlanHistory>);
    renderPage();
    const btn = screen.getByTestId("toggle-plan-history-btn");
    expect(btn).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(btn);
    expect(btn).toHaveAttribute("aria-expanded", "true");
  });
});

// ── Tests: lock validation errors from API ────────────────────────────────────

describe("MyWeekPage — lock validation failure", () => {
  it("shows validation errors when lock API returns success=false", async () => {
    mockPlanApi.lockPlan.mockResolvedValue({
      success: false,
      errors: [{ field: "commits", message: "At least one commit is required" }],
    });
    renderPage();
    // Open lock flow
    fireEvent.click(screen.getByTestId("lock-plan-btn"));
    await waitFor(() =>
      expect(screen.getByTestId("pre-lock-continue-btn")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByTestId("pre-lock-continue-btn"));
    // Confirm in dialog
    fireEvent.click(screen.getByTestId("lock-confirm-btn"));
    // The error should be shown back in the validation panel
    await waitFor(() =>
      expect(screen.getByTestId("pre-lock-hard-errors")).toBeInTheDocument(),
    );
  });
});

// ── Tests: scope change timeline ──────────────────────────────────────────────

describe("MyWeekPage — scope change timeline", () => {
  it("shows load-timeline button in LOCKED state", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    expect(screen.getByTestId("load-scope-timeline-btn")).toBeInTheDocument();
  });
});

// ── Tests: inline AI lint panel (step 1 — autoRun) ───────────────────────────

describe("MyWeekPage — inline AI lint panel", () => {
  it("renders the inline AI lint panel wrapper in DRAFT state when commits exist", () => {
    // Default fixture: DRAFT plan with twoCommits, aiAssistanceEnabled=true (MockHostProvider default)
    renderPage();
    expect(screen.getByTestId("inline-ai-lint-panel")).toBeInTheDocument();
  });

  it("shows lint feedback (unavailable banner) without any button click in DRAFT state", () => {
    // AI is mocked as unavailable. With autoRun=true, the unavailable state
    // shows immediately — no "Run AI Quality Check" button required.
    renderPage();
    const lintWrapper = screen.getByTestId("inline-ai-lint-panel");
    // ai-lint-unavailable is inside the wrapper
    expect(lintWrapper.querySelector("[data-testid='ai-lint-unavailable']")).toBeInTheDocument();
    // The manual run button must NOT be present
    expect(screen.queryByTestId("ai-lint-run-btn")).not.toBeInTheDocument();
  });

  it("does NOT render the inline AI lint panel in LOCKED state", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    expect(screen.queryByTestId("inline-ai-lint-panel")).not.toBeInTheDocument();
  });

  it("does NOT render the inline AI lint panel when there are no commits", () => {
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(DRAFT_PLAN, []));
    renderPage();
    expect(screen.queryByTestId("inline-ai-lint-panel")).not.toBeInTheDocument();
  });

  it("shows a collapsed lint summary badge when AI returns suggestions", async () => {
    vi.mocked(aiHooks.useAiStatus).mockReturnValue({
      data: {
        available: true,
        aiEnabled: true,
        providerName: "openrouter",
        providerVersion: "1",
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.useAiApi).mockReturnValue({
      recordFeedback: vi.fn(),
      commitDraftAssist: vi.fn(),
      commitFromFreeform: vi.fn(),
      rcdoSuggest: vi.fn(),
      getStatus: vi.fn(),
      commitLint: vi.fn().mockResolvedValue({
        aiAvailable: true,
        hardValidation: [{ code: "H1", message: "Missing success criteria" }],
        softGuidance: [
          { code: "S1", message: "Consider tightening scope" },
          { code: "S2", message: "Clarify estimate" },
        ],
      }),
      getRiskSignals: vi.fn(),
      reconcileAssist: vi.fn(),
      getTeamAiSummary: vi.fn(), getPlanEvidence: vi.fn(), getCommitEvidence: vi.fn(),
    } as ReturnType<typeof aiHooks.useAiApi>);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("3 issues")).toBeInTheDocument();
    });
  });
});

// ── Tests: AI Commit Composer integration (step 5) ────────────────────────────

describe("MyWeekPage — AI commit composer", () => {
  beforeEach(() => {
    // Override AI status to be available for this describe block.
    vi.mocked(aiHooks.useAiStatus).mockReturnValue({
      data: {
        available: true,
        aiEnabled: true,
        providerName: "openrouter",
        providerVersion: "1",
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(aiHooks.useAiApi).mockReturnValue({
      recordFeedback: vi.fn(),
      commitDraftAssist: vi.fn(),
      commitFromFreeform: vi.fn(),
      rcdoSuggest: vi.fn(),
      getStatus: vi.fn(),
      commitLint: vi.fn(),
      getRiskSignals: vi.fn(),
      reconcileAssist: vi.fn(),
      getTeamAiSummary: vi.fn(), getPlanEvidence: vi.fn(), getCommitEvidence: vi.fn(),
    } as ReturnType<typeof aiHooks.useAiApi>);
  });

  it("opens AI commit composer when AI is available and + Add Commit is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    expect(screen.getByTestId("ai-commit-composer")).toBeInTheDocument();
  });

  it("shows 'Add manually' link when AI composer is enabled", () => {
    renderPage();
    expect(screen.getByTestId("add-commit-manually-btn")).toBeInTheDocument();
  });

  it("opens CommitForm directly when 'Add manually' is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("add-commit-manually-btn"));
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
    expect(screen.queryByTestId("ai-commit-composer")).not.toBeInTheDocument();
  });

  it("closes the AI commit composer when Close button is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    expect(screen.getByTestId("ai-commit-composer")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("ai-composer-close-btn"));
    expect(screen.queryByTestId("ai-commit-composer")).not.toBeInTheDocument();
  });

  it("switching to manual form from AI composer opens CommitForm", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    expect(screen.getByTestId("ai-commit-composer")).toBeInTheDocument();
    // Click the 'Switch to manual form' link in Phase 1
    fireEvent.click(screen.getByTestId("ai-composer-switch-manual-link"));
    expect(screen.queryByTestId("ai-commit-composer")).not.toBeInTheDocument();
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
  });
});

// ── Tests: ProactiveRiskBanner integration ────────────────────────────────────

describe("MyWeekPage — ProactiveRiskBanner", () => {
  it("renders risk banners for a LOCKED plan when critical signals exist", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: {
        aiAvailable: true,
        planId: "plan-1",
        signals: [
          {
            id: "sig-1",
            signalType: "OVERCOMMIT",
            rationale: "You are over budget.",
            planId: "plan-1",
            createdAt: "2026-03-27T10:00:00Z",
          },
        ],
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    expect(screen.getByTestId("proactive-risk-banners")).toBeInTheDocument();
    expect(screen.getByTestId("risk-banner-sig-1")).toBeInTheDocument();
  });

  it("does NOT render risk banners for a DRAFT plan", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: {
        aiAvailable: true,
        planId: "plan-1",
        signals: [
          {
            id: "sig-1",
            signalType: "OVERCOMMIT",
            rationale: "You are over budget.",
            planId: "plan-1",
            createdAt: "2026-03-27T10:00:00Z",
          },
        ],
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    // DRAFT plan — ProactiveRiskBanner must not render
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(DRAFT_PLAN, twoCommits));
    renderPage();
    expect(screen.queryByTestId("proactive-risk-banners")).not.toBeInTheDocument();
  });

  it("does NOT render risk banners when signals are only non-critical types", () => {
    vi.mocked(aiHooks.useRiskSignals).mockReturnValue({
      data: {
        aiAvailable: true,
        planId: "plan-1",
        signals: [
          {
            id: "sig-4",
            signalType: "UNDERCOMMIT",
            rationale: "Under budget.",
            planId: "plan-1",
            createdAt: "2026-03-27T10:00:00Z",
          },
        ],
      },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(useCurrentPlan).mockReturnValue(makePlanState(LOCKED_PLAN, twoCommits));
    renderPage();
    expect(screen.queryByTestId("proactive-risk-banners")).not.toBeInTheDocument();
  });
});
