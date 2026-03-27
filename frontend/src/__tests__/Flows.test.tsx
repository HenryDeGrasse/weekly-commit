/**
 * Flows.test.tsx — regression tests for the six bugs fixed in this session.
 *
 * 1. Navigation: clicking sidebar links navigates to the correct route
 * 2. Estimate points: selected button highlights correctly (string/number coercion)
 * 3. Team dashboard: renders Overview tab when memberViews is populated
 * 4. RCDO picker: CommitForm renders a populated RCDO tree
 * 5. Mock UUIDs: MockHostProvider uses valid UUIDs, not "user-dev-1" strings
 * 6. Plan state: DRAFT plan renders Add Commit button; LOCKED plan does not
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { MockHostProvider, mockHostContext } from "../host/MockHostProvider.js";
import { CommitForm } from "../components/myweek/CommitForm.js";
import type {
  CommitResponse,
  PlanResponse,
  CreateCommitPayload,
} from "../api/planTypes.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";
import type {
  MemberCommitView,
  MemberWeekView,
  TeamWeekViewResponse,
} from "../api/teamTypes.js";

// ── shared mocks ──────────────────────────────────────────────────────────────

vi.mock("../api/planHooks.js", () => ({
  usePlanApi: vi.fn(),
  useCurrentPlan: vi.fn(),
}));
vi.mock("../api/rcdoHooks.js", () => ({
  useRcdoTree: vi.fn(),
  useRcdoNode: vi.fn(),
  useRcdoApi: vi.fn(),
}));
vi.mock("../api/teamHooks.js", () => ({
  useTeamApi: vi.fn(),
  useTeamWeekView: vi.fn(),
  useExceptionQueue: vi.fn(),
  useTeamMembers: vi.fn().mockReturnValue({ data: [], loading: false, error: null, refetch: vi.fn() }),
}));
vi.mock("../api/ticketHooks.js", () => ({
  useTicketApi: vi.fn(),
  useTicketList: vi.fn(),
  useTicket: vi.fn(),
  useTeamHistory: vi.fn(),
  usePlanHistory: vi.fn(),
  useCarryForwardLineage: vi.fn(),
}));

import { useCurrentPlan, usePlanApi } from "../api/planHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import { useTeamWeekView, useExceptionQueue, useTeamApi } from "../api/teamHooks.js";
import {
  useTicketApi,
  useTicketList,
  useTicket,
  useTeamHistory,
  usePlanHistory,
  useCarryForwardLineage,
} from "../api/ticketHooks.js";

import MyWeek from "../routes/MyWeek.js";
import TeamWeek from "../routes/TeamWeek.js";
import WeeklyCommitRoutes from "../Routes.js";
import { mockHostBridge } from "../host/MockHostProvider.js";

// ── fixtures ──────────────────────────────────────────────────────────────────

const DRAFT_PLAN: PlanResponse = {
  id: "plan-1",
  ownerUserId: "00000000-0000-0000-0000-000000000001",
  teamId: "00000000-0000-0000-0000-000000000010",
  weekStartDate: "2026-03-24",
  state: "DRAFT",
  lockDeadline: "2026-04-01T12:00:00Z",
  reconcileDeadline: "2026-04-05T17:00:00Z",
  capacityBudgetPoints: 10,
  compliant: true,
  systemLockedWithErrors: false,
  createdAt: "2026-03-24T00:00:00Z",
  updatedAt: "2026-03-24T00:00:00Z",
};

const LOCKED_PLAN: PlanResponse = { ...DRAFT_PLAN, state: "LOCKED" };

function makeCommit(overrides: Partial<CommitResponse> = {}): CommitResponse {
  return {
    id: "c-1",
    planId: "plan-1",
    ownerUserId: "00000000-0000-0000-0000-000000000001",
    title: "Test commit",
    chessPiece: "PAWN",
    priorityOrder: 1,
    carryForwardStreak: 0,
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

const seededRcdoTree: RcdoTreeNode[] = [
  {
    id: "00000000-0000-0000-aaaa-000000000001",
    nodeType: "RALLY_CRY",
    status: "ACTIVE",
    title: "Grow the Business",
    children: [
      {
        id: "00000000-0000-0000-bbbb-000000000001",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Increase Revenue",
        children: [
          {
            id: "00000000-0000-0000-cccc-000000000001",
            nodeType: "OUTCOME",
            status: "ACTIVE",
            title: "Close 10 Enterprise Deals",
            children: [],
          },
          {
            id: "00000000-0000-0000-cccc-000000000002",
            nodeType: "OUTCOME",
            status: "ACTIVE",
            title: "Expand to EMEA Market",
            children: [],
          },
        ],
      },
    ],
  },
  {
    id: "00000000-0000-0000-aaaa-000000000002",
    nodeType: "RALLY_CRY",
    status: "ACTIVE",
    title: "Operational Excellence",
    children: [
      {
        id: "00000000-0000-0000-bbbb-000000000003",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Ship Faster",
        children: [
          {
            id: "00000000-0000-0000-cccc-000000000004",
            nodeType: "OUTCOME",
            status: "ACTIVE",
            title: "Deploy Daily by Q2",
            children: [],
          },
        ],
      },
    ],
  },
];

const memberView: MemberWeekView = {
  userId: "00000000-0000-0000-0000-000000000001",
  displayName: "Dev User",
  planId: "plan-1",
  planState: "DRAFT",
  capacityBudgetPoints: 10,
  totalCommittedPoints: 3,
  commits: [
    {
      id: "c-1",
      title: "Test commit",
      chessPiece: "PAWN",
      estimatePoints: 3,
      priorityOrder: 1,
      rcdoNodeId: null,
      carryForwardStreak: 0,
      outcome: null,
    } satisfies MemberCommitView,
  ],
};

const teamWeekData: TeamWeekViewResponse = {
  teamId: "00000000-0000-0000-0000-000000000010",
  teamName: "Engineering",
  weekStart: "2026-03-24",
  memberViews: [memberView],
  peerViews: [],
  uncommittedAssignedTickets: [],
  uncommittedUnassignedTickets: [],
  rcdoRollup: [],
  chessDistribution: [],
  complianceSummary: [],
};

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

const mockTeamApi = {
  getTeamMembers: vi.fn().mockResolvedValue([]),
  getTeamWeekView: vi.fn(),
  getExceptionQueue: vi.fn(),
  resolveException: vi.fn(),
  addComment: vi.fn(),
  quickAssignTicket: vi.fn(),
  getTeamHistory: vi.fn(),
};

function noop() {
  return { data: undefined, loading: false, error: null, refetch: vi.fn() };
}

beforeEach(() => {
  vi.mocked(useCurrentPlan).mockReturnValue({
    data: { plan: DRAFT_PLAN, commits: [makeCommit()], totalPoints: 0 },
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  vi.mocked(usePlanApi).mockReturnValue(
    mockPlanApi as ReturnType<typeof usePlanApi>,
  );
  vi.mocked(useRcdoTree).mockReturnValue({
    data: seededRcdoTree,
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  vi.mocked(useTeamApi).mockReturnValue(
    mockTeamApi as ReturnType<typeof useTeamApi>,
  );
  vi.mocked(useTeamWeekView).mockReturnValue({
    data: teamWeekData,
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  vi.mocked(useExceptionQueue).mockReturnValue({
    data: [],
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  vi.mocked(useTicketApi).mockReturnValue({} as ReturnType<typeof useTicketApi>);
  vi.mocked(useTicketList).mockReturnValue(noop() as never);
  vi.mocked(useTicket).mockReturnValue(noop() as never);
  vi.mocked(useTeamHistory).mockReturnValue(noop() as never);
  vi.mocked(usePlanHistory).mockReturnValue(noop() as never);
  vi.mocked(useCarryForwardLineage).mockReturnValue(noop() as never);
});

// ── Fix 1: Navigation ─────────────────────────────────────────────────────────

describe("Fix 1 — Navigation: sidebar links route to correct pages", () => {
  function renderApp(initialPath = "/weekly") {
    return render(
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/weekly/*" element={<WeeklyCommitRoutes bridge={mockHostBridge} />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("renders My Week at /weekly/my-week", async () => {
    renderApp("/weekly/my-week");
    expect(await screen.findByTestId("page-my-week")).toBeInTheDocument();
  });

  it("renders Team Week at /weekly/team", async () => {
    renderApp("/weekly/team");
    expect(await screen.findByTestId("page-team-week")).toBeInTheDocument();
  });

  it("renders Reconcile at /weekly/reconcile", async () => {
    renderApp("/weekly/reconcile");
    expect(await screen.findByTestId("page-reconcile")).toBeInTheDocument();
  });

  it("renders Tickets at /weekly/tickets", async () => {
    renderApp("/weekly/tickets");
    expect(await screen.findByTestId("page-tickets")).toBeInTheDocument();
  });

  it("renders RCDOs at /weekly/rcdos", async () => {
    renderApp("/weekly/rcdos");
    expect(await screen.findByTestId("page-rcdos")).toBeInTheDocument();
  });

  it("index /weekly redirects to /weekly/my-week", async () => {
    renderApp("/weekly");
    expect(await screen.findByTestId("page-my-week")).toBeInTheDocument();
  });

  it("sidebar nav links are present and have correct /weekly/* hrefs", async () => {
    renderApp("/weekly/my-week");
    await screen.findByTestId("page-my-week");
    const nav = screen.getByRole("navigation", { name: "Weekly Commit navigation" });
    const links = nav.querySelectorAll("a");
    const hrefs = Array.from(links).map((a) => a.getAttribute("href"));
    expect(hrefs).toContain("/weekly/my-week");
    expect(hrefs).toContain("/weekly/team");
    expect(hrefs).toContain("/weekly/reconcile");
    expect(hrefs).toContain("/weekly/tickets");
    expect(hrefs).toContain("/weekly/rcdos");
  });

  it("clicking Team Week nav link navigates to team page", async () => {
    renderApp("/weekly/my-week");
    await screen.findByTestId("page-my-week");
    fireEvent.click(screen.getByRole("link", { name: /Team Week/i }));
    expect(await screen.findByTestId("page-team-week")).toBeInTheDocument();
  });

  it("clicking My Week nav link from Team Week navigates back", async () => {
    renderApp("/weekly/team");
    await screen.findByTestId("page-team-week");
    fireEvent.click(screen.getByRole("link", { name: /My Week/i }));
    expect(await screen.findByTestId("page-my-week")).toBeInTheDocument();
  });
});

// ── Fix 2: Estimate points visual ────────────────────────────────────────────

describe("Fix 2 — Estimate points: selected button highlights correctly", () => {
  function renderForm(onSubmit = vi.fn().mockResolvedValue(undefined) as (p: CreateCommitPayload) => Promise<void>) {
    render(
      <CommitForm
        mode="create"
        planId="00000000-0000-0000-0000-000000000099"
        rcdoTree={seededRcdoTree}
        existingCommits={[]}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );
  }

  it("all estimate buttons start un-pressed", () => {
    renderForm();
    [1, 2, 3, 5, 8].forEach((pts) => {
      expect(screen.getByTestId(`estimate-btn-${pts}`)).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    });
  });

  it("clicking estimate 3 sets aria-pressed=true on that button", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("estimate-btn-3"));
    expect(screen.getByTestId("estimate-btn-3")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("clicking estimate 3 sets primary background color on that button", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("estimate-btn-3"));
    const btn = screen.getByTestId("estimate-btn-3");
    expect(btn.style.background).toBe("var(--color-primary)");
    expect(btn.style.color).toBe("rgb(255, 255, 255)");
  });

  it("other estimate buttons remain un-pressed after selecting one", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("estimate-btn-5"));
    [1, 2, 3, 8].forEach((pts) => {
      expect(screen.getByTestId(`estimate-btn-${pts}`)).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    });
    expect(screen.getByTestId("estimate-btn-5")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("clicking selected button again deselects it (toggles off)", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("estimate-btn-2"));
    fireEvent.click(screen.getByTestId("estimate-btn-2"));
    expect(screen.getByTestId("estimate-btn-2")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByTestId("estimate-btn-2").style.background).not.toBe(
      "var(--color-primary)",
    );
  });

  it("selected estimate is included in submit payload", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined) as (p: CreateCommitPayload) => Promise<void>;
    renderForm(onSubmit);
    fireEvent.change(screen.getByTestId("commit-form-title"), {
      target: { value: "My task" },
    });
    // Select PAWN piece
    const pawnLabel = screen.getByTestId("chess-piece-option-pawn");
    fireEvent.click(pawnLabel.querySelector("input[type=radio]")!);
    // Select 5 points
    fireEvent.click(screen.getByTestId("estimate-btn-5"));
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ estimatePoints: 5 }),
    );
  });
});

// ── Fix 3: Team dashboard memberViews ────────────────────────────────────────

describe("Fix 3 — Team dashboard: renders with populated memberViews", () => {
  function renderTeam() {
    return render(
      <MemoryRouter initialEntries={["/team/00000000-0000-0000-0000-000000000010"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
  }

  it("renders the team week page", async () => {
    renderTeam();
    expect(await screen.findByTestId("page-team-week")).toBeInTheDocument();
  });

  it("shows the team name", async () => {
    renderTeam();
    await screen.findByTestId("page-team-week");
    expect(screen.getByText("Engineering")).toBeInTheDocument();
  });

  it("renders all tab buttons", async () => {
    renderTeam();
    await screen.findByTestId("page-team-week");
    ["overview", "by-person", "by-rcdo", "chess", "uncommitted", "exceptions", "history"].forEach(
      (tab) => expect(screen.getByTestId(`tab-${tab}`)).toBeInTheDocument(),
    );
  });

  it("Overview tab panel renders with member data", async () => {
    renderTeam();
    await screen.findByTestId("page-team-week");
    expect(screen.getByTestId("panel-overview")).toBeInTheDocument();
  });

  it("switching to By Person tab renders that panel", async () => {
    renderTeam();
    await screen.findByTestId("page-team-week");
    fireEvent.click(screen.getByTestId("tab-by-person"));
    expect(screen.getByTestId("panel-by-person")).toBeInTheDocument();
  });

  it("switching to Chess tab renders that panel", async () => {
    renderTeam();
    await screen.findByTestId("page-team-week");
    fireEvent.click(screen.getByTestId("tab-chess"));
    expect(screen.getByTestId("panel-chess")).toBeInTheDocument();
  });

  it("switching to Exceptions tab renders that panel", async () => {
    renderTeam();
    await screen.findByTestId("page-team-week");
    fireEvent.click(screen.getByTestId("tab-exceptions"));
    expect(screen.getByTestId("panel-exceptions")).toBeInTheDocument();
  });
});

// ── Fix 4: RCDO picker has seeded data ───────────────────────────────────────

describe("Fix 4 — RCDO picker: CommitForm shows the seeded RCDO tree", () => {
  function renderForm() {
    render(
      <MemoryRouter>
        <MockHostProvider>
          <CommitForm
            mode="create"
            planId="00000000-0000-0000-0000-000000000099"
            rcdoTree={seededRcdoTree}
            existingCommits={[]}
            onSubmit={vi.fn().mockResolvedValue(undefined)}
            onCancel={vi.fn()}
          />
        </MockHostProvider>
      </MemoryRouter>,
    );
  }

  it("RCDO picker toggle is present", () => {
    renderForm();
    expect(screen.getByTestId("rcdo-picker-toggle")).toBeInTheDocument();
  });

  it("opening the picker shows the seeded tree", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    expect(screen.getByTestId("rcdo-picker-panel")).toBeInTheDocument();
    expect(screen.getByTestId("rcdo-tree-view")).toBeInTheDocument();
  });

  it("seeded Rally Cry nodes appear in the tree", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    expect(screen.getByText("Grow the Business")).toBeInTheDocument();
    expect(screen.getByText("Operational Excellence")).toBeInTheDocument();
  });

  it("can select an Outcome node from the seeded tree", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    // Expand all to show Outcome nodes
    fireEvent.click(screen.getByRole("button", { name: "Expand all nodes" }));
    // Select "Close 10 Enterprise Deals" (an Outcome — valid selection)
    fireEvent.click(
      screen.getByTestId(`tree-node-00000000-0000-0000-cccc-000000000001`),
    );
    expect(screen.queryByTestId("rcdo-picker-panel")).not.toBeInTheDocument();
    expect(screen.getByTestId("rcdo-selected-node")).toHaveTextContent(
      "Close 10 Enterprise Deals",
    );
  });

  it("blocks selecting a Rally Cry node", () => {
    renderForm();
    fireEvent.click(screen.getByTestId("rcdo-picker-toggle"));
    fireEvent.click(
      screen.getByTestId(`tree-node-00000000-0000-0000-aaaa-000000000001`),
    );
    // Picker stays open, no node selected
    expect(screen.getByTestId("rcdo-picker-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("rcdo-selected-node")).not.toBeInTheDocument();
  });
});

// ── Fix 5: MockHostProvider uses valid UUIDs ──────────────────────────────────

describe("Fix 5 — MockHostProvider: IDs are valid UUIDs", () => {
  const UUID_REGEX =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  it("authenticatedUser.id is a valid UUID", () => {
    expect(mockHostContext.authenticatedUser.id).toMatch(UUID_REGEX);
  });

  it("currentTeam.id is a valid UUID", () => {
    expect(mockHostContext.currentTeam?.id).toMatch(UUID_REGEX);
  });

  it("managerChain[0].id is a valid UUID", () => {
    expect(mockHostContext.managerChain[0]?.id).toMatch(UUID_REGEX);
  });

  it("authenticatedUser.id is not the old non-UUID placeholder", () => {
    expect(mockHostContext.authenticatedUser.id).not.toBe("user-dev-1");
  });

  it("currentTeam.id is not the old non-UUID placeholder", () => {
    expect(mockHostContext.currentTeam?.id).not.toBe("team-dev-1");
  });
});

// ── Fix 6: Plan state controls UI correctly ───────────────────────────────────

describe("Fix 6 — Plan state: UI adapts correctly to DRAFT vs LOCKED", () => {
  function renderMyWeek(plan: PlanResponse, commits: CommitResponse[] = []) {
    vi.mocked(useCurrentPlan).mockReturnValue({
      data: { plan, commits, totalPoints: commits.reduce((s, c) => s + (c.estimatePoints ?? 0), 0) },
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    render(
      <MemoryRouter>
        <MockHostProvider>
          <MyWeek />
        </MockHostProvider>
      </MemoryRouter>,
    );
  }

  it("DRAFT plan shows Add Commit button", () => {
    renderMyWeek(DRAFT_PLAN);
    expect(screen.getByTestId("add-commit-btn")).toBeInTheDocument();
  });

  it("DRAFT plan shows Lock Plan button", () => {
    renderMyWeek(DRAFT_PLAN);
    expect(screen.getByTestId("lock-plan-btn")).toBeInTheDocument();
  });

  it("LOCKED plan hides Add Commit header button", () => {
    renderMyWeek(LOCKED_PLAN);
    expect(screen.queryByTestId("add-commit-btn")).not.toBeInTheDocument();
  });

  it("LOCKED plan hides Lock Plan button", () => {
    renderMyWeek(LOCKED_PLAN);
    expect(screen.queryByTestId("lock-plan-btn")).not.toBeInTheDocument();
  });

  it("LOCKED plan shows reconcile hint", () => {
    renderMyWeek(LOCKED_PLAN);
    expect(screen.getByTestId("reconcile-hint")).toBeInTheDocument();
  });

  it("LOCKED plan shows post-lock Add Commit button", () => {
    renderMyWeek(LOCKED_PLAN);
    expect(screen.getByTestId("post-lock-add-commit-btn")).toBeInTheDocument();
  });

  it("DRAFT plan: clicking + Add Commit opens the CommitForm modal", () => {
    renderMyWeek(DRAFT_PLAN);
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    expect(screen.getByTestId("commit-form-modal")).toBeInTheDocument();
  });

  it("DRAFT plan: CommitForm cancel closes the modal", () => {
    renderMyWeek(DRAFT_PLAN);
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByTestId("commit-form-modal")).not.toBeInTheDocument();
  });

  it("successful createCommit call refetches the plan", async () => {
    const refetch = vi.fn();
    vi.mocked(useCurrentPlan).mockReturnValue({
      data: { plan: DRAFT_PLAN, commits: [], totalPoints: 0 },
      loading: false,
      error: null,
      refetch,
    });
    mockPlanApi.createCommit.mockResolvedValue(makeCommit({ title: "New one" }));
    render(
      <MemoryRouter>
        <MockHostProvider>
          <MyWeek />
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByTestId("add-commit-btn"));
    fireEvent.change(screen.getByTestId("commit-form-title"), {
      target: { value: "New one" },
    });
    const pawnLabel = screen.getByTestId("chess-piece-option-pawn");
    fireEvent.click(pawnLabel.querySelector("input[type=radio]")!);
    fireEvent.click(screen.getByTestId("commit-form-submit"));
    await waitFor(() => expect(mockPlanApi.createCommit).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(refetch).toHaveBeenCalled());
  });
});
