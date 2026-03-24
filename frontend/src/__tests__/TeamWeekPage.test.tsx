/**
 * Integration tests for the Team Week dashboard (route-level + section components).
 *
 * Covers:
 *   - Page renders with testid
 *   - No team selected message
 *   - Loading and error states
 *   - Tab navigation
 *   - OverviewSection: compliance cards compute correct aggregates
 *   - ByPersonSection: table renders direct reports, expandable rows
 *   - ByRcdoSection: RCDO rollup groups correctly, coverage gap highlighting
 *   - ChessDistributionSection: renders accurately, highlights King/Queen
 *   - UncommittedWorkSection: shows correct tickets without double-counting
 *   - ExceptionQueueSection: sorts by severity, resolve and comment actions work
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
  within,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { MockHostProvider } from "../host/MockHostProvider.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/teamHooks.js", () => ({
  useTeamApi: vi.fn(),
  useTeamWeekView: vi.fn(),
  useExceptionQueue: vi.fn(),
}));

vi.mock("../api/rcdoHooks.js", () => ({
  useRcdoTree: vi.fn(),
  useRcdoNode: vi.fn(),
  useRcdoApi: vi.fn(),
}));

import {
  useTeamApi,
  useTeamWeekView,
  useExceptionQueue,
} from "../api/teamHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import TeamWeek from "../routes/TeamWeek.js";
import { mockHostBridge, mockHostContext } from "../host/MockHostProvider.js";
import type {
  TeamWeekViewResponse,
  MemberWeekView,
  MemberComplianceSummary,
  ExceptionResponse,
  RcdoRollupEntry,
  ChessDistributionEntry,
  UncommittedTicketSummary,
} from "../api/teamTypes.js";
import type { RcdoTreeNode } from "../api/rcdoTypes.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeMemberView(
  userId: string,
  displayName: string,
  overrides: Partial<MemberWeekView> = {},
): MemberWeekView {
  return {
    userId,
    displayName,
    planId: `plan-${userId}`,
    planState: "LOCKED",
    capacityBudgetPoints: 10,
    totalCommittedPoints: 6,
    commits: [
      {
        id: `commit-${userId}-1`,
        title: `Commit A (${displayName})`,
        chessPiece: "KING",
        estimatePoints: 3,
        priorityOrder: 1,
        rcdoNodeId: "rcdo-3",
        carryForwardStreak: 0,
        outcome: null,
      },
      {
        id: `commit-${userId}-2`,
        title: `Commit B (${displayName})`,
        chessPiece: "PAWN",
        estimatePoints: 3,
        priorityOrder: 2,
        rcdoNodeId: "rcdo-3",
        carryForwardStreak: 0,
        outcome: null,
      },
    ],
    ...overrides,
  };
}

function makeCompliance(
  userId: string,
  displayName: string,
  overrides: Partial<MemberComplianceSummary> = {},
): MemberComplianceSummary {
  return {
    userId,
    displayName,
    lockCompliant: true,
    reconcileCompliant: true,
    autoLocked: false,
    planState: "LOCKED",
    hasPlan: true,
    ...overrides,
  };
}

function makeException(
  id: string,
  overrides: Partial<ExceptionResponse> = {},
): ExceptionResponse {
  return {
    id,
    teamId: "team-1",
    planId: `plan-user-1`,
    userId: "user-1",
    exceptionType: "MISSED_LOCK",
    severity: "HIGH",
    description: "Plan was not locked on time",
    weekStartDate: "2026-03-24",
    resolved: false,
    resolution: null,
    resolvedAt: null,
    resolvedById: null,
    createdAt: "2026-03-25T09:00:00Z",
    ...overrides,
  };
}

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
      {
        id: "rcdo-4",
        nodeType: "DEFINING_OBJECTIVE",
        status: "ACTIVE",
        title: "Product Growth",
        children: [],
      },
    ],
  },
];

const twoMembers = [
  makeMemberView("user-1", "Alice"),
  makeMemberView("user-2", "Bob"),
];

const twoCompliance = [
  makeCompliance("user-1", "Alice"),
  makeCompliance("user-2", "Bob"),
];

const chessDistribution: ChessDistributionEntry[] = [
  { chessPiece: "KING", commitCount: 2, totalPoints: 6 },
  { chessPiece: "PAWN", commitCount: 2, totalPoints: 6 },
];

const rcdoRollup: RcdoRollupEntry[] = [
  { rcdoNodeId: "rcdo-3", commitCount: 4, totalPoints: 12 },
];

const assignedTickets: UncommittedTicketSummary[] = [
  {
    id: "ticket-1",
    key: "WC-101",
    title: "Fix login bug",
    status: "IN_PROGRESS",
    assigneeUserId: "user-1",
    teamId: "team-1",
    rcdoNodeId: null,
    estimatePoints: 3,
    targetWeekStartDate: "2026-03-24",
  },
];

const unassignedTickets: UncommittedTicketSummary[] = [
  {
    id: "ticket-2",
    key: "WC-102",
    title: "Write docs",
    status: "TODO",
    assigneeUserId: null,
    teamId: "team-1",
    rcdoNodeId: null,
    estimatePoints: 2,
    targetWeekStartDate: "2026-03-24",
  },
];

function makeTeamWeekData(
  overrides: Partial<TeamWeekViewResponse> = {},
): TeamWeekViewResponse {
  return {
    teamId: "team-1",
    teamName: "Engineering",
    weekStart: "2026-03-24",
    memberViews: twoMembers,
    peerViews: [],
    uncommittedAssignedTickets: assignedTickets,
    uncommittedUnassignedTickets: unassignedTickets,
    rcdoRollup,
    chessDistribution,
    complianceSummary: twoCompliance,
    ...overrides,
  };
}

const exceptions: ExceptionResponse[] = [
  makeException("exc-1", { severity: "HIGH", exceptionType: "MISSED_LOCK" }),
  makeException("exc-2", { severity: "LOW", exceptionType: "OVER_BUDGET", planId: "plan-user-2" }),
  makeException("exc-3", { severity: "MEDIUM", exceptionType: "REPEATED_CARRY_FORWARD" }),
];

const mockRefetch = vi.fn();
const mockTeamApi = {
  getTeamWeekView: vi.fn(),
  getExceptionQueue: vi.fn(),
  resolveException: vi.fn(),
  addComment: vi.fn(),
  quickAssignTicket: vi.fn(),
};

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(useTeamApi).mockReturnValue(
    mockTeamApi as ReturnType<typeof useTeamApi>,
  );
  vi.mocked(useTeamWeekView).mockReturnValue({
    data: makeTeamWeekData(),
    loading: false,
    error: null,
    refetch: mockRefetch,
  });
  vi.mocked(useExceptionQueue).mockReturnValue({
    data: exceptions,
    loading: false,
    error: null,
    refetch: mockRefetch,
  });
  vi.mocked(useRcdoTree).mockReturnValue({
    data: mockRcdoTree,
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  mockTeamApi.resolveException.mockReset();
  mockTeamApi.addComment.mockReset();
  mockTeamApi.quickAssignTicket.mockReset();
});

function renderPage(teamId?: string) {
  const path = teamId ? `/team/${teamId}` : "/team";
  const routePath = teamId ? "/team/:teamId" : "/team";
  return render(
    <MemoryRouter initialEntries={[path]}>
      <MockHostProvider>
        <Routes>
          <Route path={routePath} element={<TeamWeek />} />
          {teamId && <Route path="/team" element={<TeamWeek />} />}
        </Routes>
      </MockHostProvider>
    </MemoryRouter>,
  );
}

// ── Tests: basic rendering ────────────────────────────────────────────────────

describe("TeamWeekPage — rendering", () => {
  it("renders the page with data-testid=page-team-week", () => {
    renderPage("team-1");
    expect(screen.getByTestId("page-team-week")).toBeInTheDocument();
  });

  it("renders the team name from the response", () => {
    renderPage("team-1");
    expect(screen.getByText("Engineering")).toBeInTheDocument();
  });

  it("renders the week selector", () => {
    renderPage("team-1");
    expect(screen.getByTestId("team-week-selector")).toBeInTheDocument();
  });

  it("shows loading state while data is fetching", () => {
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    renderPage("team-1");
    expect(screen.getByTestId("team-week-loading")).toBeInTheDocument();
  });

  it("shows error message when team data fails to load", () => {
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: undefined,
      loading: false,
      error: { name: "ApiRequestError", message: "Network error", status: 0 } as never,
      refetch: vi.fn(),
    });
    renderPage("team-1");
    expect(screen.getByTestId("team-week-error")).toHaveTextContent("Network error");
  });

  it("shows 'no team selected' message when teamId is absent and host has no team", () => {
    // When the host has no currentTeam and no route teamId, show no-team message
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: undefined,
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    vi.mocked(useExceptionQueue).mockReturnValue({
      data: undefined,
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    render(
      <MemoryRouter initialEntries={["/team"]}>
        <MockHostProvider
          bridge={{
            ...mockHostBridge,
            context: {
              ...mockHostContext,
              currentTeam: null,
            },
          }}
        >
          <Routes>
            <Route path="/team" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    expect(screen.getByTestId("no-team-selected")).toBeInTheDocument();
  });
});

// ── Tests: tab navigation ─────────────────────────────────────────────────────

describe("TeamWeekPage — tab navigation", () => {
  it("renders all tab buttons", () => {
    renderPage("team-1");
    expect(screen.getByTestId("tab-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-by-person")).toBeInTheDocument();
    expect(screen.getByTestId("tab-by-rcdo")).toBeInTheDocument();
    expect(screen.getByTestId("tab-chess")).toBeInTheDocument();
    expect(screen.getByTestId("tab-uncommitted")).toBeInTheDocument();
    expect(screen.getByTestId("tab-exceptions")).toBeInTheDocument();
  });

  it("shows overview panel by default", () => {
    renderPage("team-1");
    expect(screen.getByTestId("overview-section")).toBeInTheDocument();
  });

  it("switches to By Person panel on tab click", () => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-by-person"));
    expect(screen.getByTestId("by-person-section")).toBeInTheDocument();
  });

  it("switches to RCDO panel on tab click", () => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-by-rcdo"));
    expect(screen.getByTestId("by-rcdo-section")).toBeInTheDocument();
  });

  it("switches to Chess Distribution panel on tab click", () => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-chess"));
    expect(screen.getByTestId("chess-distribution-section")).toBeInTheDocument();
  });

  it("switches to Uncommitted Work panel on tab click", () => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-uncommitted"));
    expect(screen.getByTestId("uncommitted-work-section")).toBeInTheDocument();
  });

  it("switches to Exceptions panel on tab click", () => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-exceptions"));
    expect(screen.getByTestId("exception-queue-section")).toBeInTheDocument();
  });

  it("shows unresolved exception count badge on exceptions tab", () => {
    renderPage("team-1");
    // 3 exceptions, none resolved → badge shows 3
    const tabBtn = screen.getByTestId("tab-exceptions");
    expect(tabBtn).toHaveTextContent("3");
  });
});

// ── Tests: OverviewSection ────────────────────────────────────────────────────

describe("OverviewSection — compliance cards", () => {
  it("renders lock compliance card", () => {
    renderPage("team-1");
    expect(screen.getByTestId("lock-compliance-card")).toBeInTheDocument();
  });

  it("computes correct lock compliance (2/2 members locked)", () => {
    renderPage("team-1");
    expect(screen.getByTestId("lock-compliance-card-count")).toHaveTextContent("2/2");
    expect(screen.getByTestId("lock-compliance-card-pct")).toHaveTextContent("100%");
  });

  it("computes partial lock compliance correctly", () => {
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: makeTeamWeekData({
        complianceSummary: [
          makeCompliance("user-1", "Alice", { lockCompliant: true }),
          makeCompliance("user-2", "Bob", { lockCompliant: false }),
        ],
      }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage("team-1");
    expect(screen.getByTestId("lock-compliance-card-count")).toHaveTextContent("1/2");
    expect(screen.getByTestId("lock-compliance-card-pct")).toHaveTextContent("50%");
  });

  it("renders reconcile compliance card", () => {
    renderPage("team-1");
    expect(screen.getByTestId("reconcile-compliance-card")).toBeInTheDocument();
  });

  it("computes correct planned points total", () => {
    // 2 members × 6 points each = 12
    renderPage("team-1");
    expect(screen.getByTestId("planned-points")).toHaveTextContent("12");
  });

  it("computes achieved points from commits with ACHIEVED outcome", () => {
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: makeTeamWeekData({
        memberViews: [
          makeMemberView("user-1", "Alice", {
            commits: [
              {
                id: "c-1",
                title: "Done thing",
                chessPiece: "KING",
                estimatePoints: 5,
                priorityOrder: 1,
                rcdoNodeId: null,
                carryForwardStreak: 0,
                outcome: "ACHIEVED",
              },
              {
                id: "c-2",
                title: "Not done",
                chessPiece: "PAWN",
                estimatePoints: 3,
                priorityOrder: 2,
                rcdoNodeId: null,
                carryForwardStreak: 0,
                outcome: "NOT_ACHIEVED",
              },
            ],
          }),
        ],
      }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage("team-1");
    expect(screen.getByTestId("achieved-points")).toHaveTextContent("5");
  });

  it("renders exceptions overview card with severity breakdown", () => {
    renderPage("team-1");
    expect(screen.getByTestId("exceptions-overview-card")).toBeInTheDocument();
    expect(screen.getByTestId("open-exceptions-count")).toHaveTextContent("3");
    // HIGH, MEDIUM, LOW breakdown
    expect(screen.getByTestId("exceptions-high-count")).toHaveTextContent("1");
    expect(screen.getByTestId("exceptions-medium-count")).toHaveTextContent("1");
    expect(screen.getByTestId("exceptions-low-count")).toHaveTextContent("1");
  });
});

// ── Tests: ByPersonSection ────────────────────────────────────────────────────

describe("ByPersonSection — direct reports table", () => {
  beforeEach(() => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-by-person"));
  });

  it("renders by-person section with table", () => {
    expect(screen.getByTestId("by-person-table")).toBeInTheDocument();
  });

  it("renders a row for each direct report", () => {
    expect(screen.getByTestId("member-row-user-1")).toBeInTheDocument();
    expect(screen.getByTestId("member-row-user-2")).toBeInTheDocument();
  });

  it("shows member display names", () => {
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
  });

  it("shows plan state for each member", () => {
    // Both members have LOCKED plan state
    const aliceRow = screen.getByTestId("member-row-user-1");
    expect(within(aliceRow).getByText("LOCKED")).toBeInTheDocument();
  });

  it("shows points committed/budget for each member", () => {
    expect(screen.getByTestId("member-points-user-1")).toHaveTextContent("6/10");
    expect(screen.getByTestId("member-points-user-2")).toHaveTextContent("6/10");
  });

  it("expands a member row on click to show commit list", () => {
    const aliceRow = screen.getByTestId("member-row-user-1");
    fireEvent.click(aliceRow);
    expect(screen.getByTestId("member-row-expanded-user-1")).toBeInTheDocument();
  });

  it("shows commits in the expanded row", () => {
    const aliceRow = screen.getByTestId("member-row-user-1");
    fireEvent.click(aliceRow);
    const expandedRow = screen.getByTestId("member-row-expanded-user-1");
    expect(within(expandedRow).getByText("Commit A (Alice)")).toBeInTheDocument();
    expect(within(expandedRow).getByText("Commit B (Alice)")).toBeInTheDocument();
  });

  it("collapses the expanded row on second click", () => {
    const aliceRow = screen.getByTestId("member-row-user-1");
    fireEvent.click(aliceRow);
    expect(screen.getByTestId("member-row-expanded-user-1")).toBeInTheDocument();
    fireEvent.click(aliceRow);
    expect(screen.queryByTestId("member-row-expanded-user-1")).not.toBeInTheDocument();
  });

  it("shows carry-forward streak badge for members with streak >= 1", () => {
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: makeTeamWeekData({
        memberViews: [
          makeMemberView("user-1", "Alice", {
            commits: [
              {
                id: "c-1",
                title: "Old commit",
                chessPiece: "PAWN",
                estimatePoints: 3,
                priorityOrder: 1,
                rcdoNodeId: null,
                carryForwardStreak: 3,
                outcome: null,
              },
            ],
          }),
        ],
      }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    // Re-render after mock update (use within(container) to scope to only this render)
    const { container } = render(
      <MemoryRouter initialEntries={["/team/team-1"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(within(container).getByTestId("tab-by-person"));
    expect(within(container).getByTestId("cf-streak-user-1")).toBeInTheDocument();
  });
});

// ── Tests: ByRcdoSection ──────────────────────────────────────────────────────

describe("ByRcdoSection — RCDO rollup", () => {
  beforeEach(() => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-by-rcdo"));
  });

  it("renders the by-rcdo section with table", () => {
    expect(screen.getByTestId("by-rcdo-table")).toBeInTheDocument();
  });

  it("renders a row for each RCDO node in the tree", () => {
    expect(screen.getByTestId("rcdo-row-rcdo-1")).toBeInTheDocument();
    expect(screen.getByTestId("rcdo-row-rcdo-2")).toBeInTheDocument();
    expect(screen.getByTestId("rcdo-row-rcdo-3")).toBeInTheDocument();
  });

  it("shows aggregated points for RCDO node with commits (rcdo-3: 12 pts)", () => {
    expect(screen.getByTestId("rcdo-points-rcdo-3")).toHaveTextContent("12");
  });

  it("shows aggregated commit count for RCDO node (rcdo-3: 4)", () => {
    expect(screen.getByTestId("rcdo-commits-rcdo-3")).toHaveTextContent("4");
  });

  it("bubbles up points through the hierarchy (rcdo-2 inherits from rcdo-3: 12 pts)", () => {
    // rcdo-2 has no direct commits but rcdo-3 (child) has 12 pts
    expect(screen.getByTestId("rcdo-points-rcdo-2")).toHaveTextContent("12");
  });

  it("shows coverage gap for RCDO node with no commits (rcdo-4)", () => {
    // rcdo-4 has no commits → coverage-gap badge
    expect(screen.getByTestId("coverage-gap-rcdo-4")).toBeInTheDocument();
  });

  it("does NOT show coverage gap for RCDO with commits", () => {
    expect(screen.queryByTestId("coverage-gap-rcdo-3")).not.toBeInTheDocument();
  });

  it("shows achieved points for RCDO branches based on achieved commit outcomes", () => {
    cleanup();
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: makeTeamWeekData({
        memberViews: [
          makeMemberView("user-1", "Alice", {
            commits: [
              {
                id: "c-1",
                title: "Won outcome",
                chessPiece: "KING",
                estimatePoints: 5,
                priorityOrder: 1,
                rcdoNodeId: "rcdo-3",
                carryForwardStreak: 0,
                outcome: "ACHIEVED",
              },
              {
                id: "c-2",
                title: "Missed outcome",
                chessPiece: "PAWN",
                estimatePoints: 3,
                priorityOrder: 2,
                rcdoNodeId: "rcdo-3",
                carryForwardStreak: 0,
                outcome: "NOT_ACHIEVED",
              },
            ],
            totalCommittedPoints: 8,
          }),
        ],
        rcdoRollup: [{ rcdoNodeId: "rcdo-3", commitCount: 2, totalPoints: 8 }],
      }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });

    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-by-rcdo"));

    expect(screen.getByTestId("rcdo-achieved-rcdo-3")).toHaveTextContent("5");
    expect(screen.getByTestId("rcdo-achieved-rcdo-2")).toHaveTextContent("5");
  });

  it("renders planned-versus-achieved progress bars for RCDO branches", () => {
    expect(screen.getByTestId("rcdo-progress-rcdo-3")).toBeInTheDocument();
    expect(screen.getByTestId("rcdo-progress-rcdo-2")).toBeInTheDocument();
  });
});

// ── Tests: ChessDistributionSection ──────────────────────────────────────────

describe("ChessDistributionSection — chess distribution", () => {
  beforeEach(() => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-chess"));
  });

  it("renders chess distribution section", () => {
    expect(screen.getByTestId("chess-distribution-section")).toBeInTheDocument();
  });

  it("renders the distribution table with all chess pieces", () => {
    expect(screen.getByTestId("chess-distribution-table")).toBeInTheDocument();
    expect(screen.getByTestId("chess-row-king")).toBeInTheDocument();
    expect(screen.getByTestId("chess-row-queen")).toBeInTheDocument();
    expect(screen.getByTestId("chess-row-pawn")).toBeInTheDocument();
  });

  it("shows correct commit count for King piece", () => {
    expect(screen.getByTestId("chess-commits-king")).toHaveTextContent("2");
  });

  it("shows correct points for King piece", () => {
    expect(screen.getByTestId("chess-points-king")).toHaveTextContent("6");
  });

  it("shows correct totals", () => {
    expect(screen.getByTestId("chess-total-commits")).toHaveTextContent("4");
    expect(screen.getByTestId("chess-total-points")).toHaveTextContent("12");
  });

  it("shows the stacked bar when there are points", () => {
    expect(screen.getByTestId("chess-stacked-bar")).toBeInTheDocument();
  });

  it("shows critical work summary for King+Queen allocation", () => {
    // 6 King pts / 12 total = 50% critical
    expect(screen.getByTestId("critical-work-summary")).toBeInTheDocument();
    expect(screen.getByTestId("critical-work-pct")).toHaveTextContent("50%");
  });

  it("shows bar segments for pieces with points", () => {
    expect(screen.getByTestId("bar-segment-king")).toBeInTheDocument();
    expect(screen.getByTestId("bar-segment-pawn")).toBeInTheDocument();
    // Pieces with 0 points should not appear in the bar
    expect(screen.queryByTestId("bar-segment-queen")).not.toBeInTheDocument();
  });
});

// ── Tests: UncommittedWorkSection ─────────────────────────────────────────────

describe("UncommittedWorkSection — uncommitted work", () => {
  beforeEach(() => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-uncommitted"));
  });

  it("renders uncommitted work section", () => {
    expect(screen.getByTestId("uncommitted-work-section")).toBeInTheDocument();
  });

  it("shows assigned-but-uncommitted ticket list", () => {
    expect(screen.getByTestId("assigned-uncommitted-section")).toBeInTheDocument();
    expect(screen.getByTestId("assigned-uncommitted-count")).toHaveTextContent("1");
  });

  it("shows unassigned ticket list", () => {
    expect(screen.getByTestId("unassigned-section")).toBeInTheDocument();
    expect(screen.getByTestId("unassigned-count")).toHaveTextContent("1");
  });

  it("renders assigned ticket row", () => {
    expect(screen.getByTestId("assigned-ticket-ticket-1")).toBeInTheDocument();
  });

  it("renders unassigned ticket row", () => {
    expect(screen.getByTestId("unassigned-ticket-ticket-2")).toBeInTheDocument();
  });

  it("does not double-count tickets across the two lists", () => {
    // ticket-1 (assigned) should only appear in assigned list
    const assignedTable = screen.getByTestId("assigned-uncommitted-table");
    const unassignedTable = screen.getByTestId("unassigned-tickets-table");
    expect(within(assignedTable).queryByText("WC-101")).toBeInTheDocument();
    expect(within(unassignedTable).queryByText("WC-101")).not.toBeInTheDocument();
    // ticket-2 (unassigned) should only appear in unassigned list
    expect(within(unassignedTable).queryByText("WC-102")).toBeInTheDocument();
    expect(within(assignedTable).queryByText("WC-102")).not.toBeInTheDocument();
  });

  it("shows quick-assign input for unassigned tickets", () => {
    expect(screen.getByTestId("quick-assign-input-ticket-2")).toBeInTheDocument();
  });

  it("calls quickAssignTicket API when assign button is clicked with a userId", async () => {
    mockTeamApi.quickAssignTicket.mockResolvedValue({});
    const input = screen.getByTestId("quick-assign-input-ticket-2");
    fireEvent.change(input, { target: { value: "user-5" } });
    fireEvent.click(screen.getByTestId("quick-assign-btn-ticket-2"));
    await waitFor(() =>
      expect(mockTeamApi.quickAssignTicket).toHaveBeenCalledWith(
        "ticket-2",
        { assigneeUserId: "user-5" },
      ),
    );
  });

  it("shows no uncommitted work message when lists are empty", () => {
    vi.mocked(useTeamWeekView).mockReturnValue({
      data: makeTeamWeekData({
        uncommittedAssignedTickets: [],
        uncommittedUnassignedTickets: [],
      }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    const { container } = render(
      <MemoryRouter initialEntries={["/team/team-1"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(within(container).getByTestId("tab-uncommitted"));
    expect(within(container).getByTestId("no-uncommitted-work")).toBeInTheDocument();
  });
});

// ── Tests: ExceptionQueueSection ─────────────────────────────────────────────

describe("ExceptionQueueSection — exception queue", () => {
  beforeEach(() => {
    renderPage("team-1");
    fireEvent.click(screen.getByTestId("tab-exceptions"));
  });

  it("renders exception queue section", () => {
    expect(screen.getByTestId("exception-queue-section")).toBeInTheDocument();
  });

  it("renders exception list with items", () => {
    expect(screen.getByTestId("exception-list")).toBeInTheDocument();
  });

  it("defaults to unresolved filter showing all 3 unresolved exceptions", () => {
    expect(screen.getByTestId("exception-item-exc-1")).toBeInTheDocument();
    expect(screen.getByTestId("exception-item-exc-2")).toBeInTheDocument();
    expect(screen.getByTestId("exception-item-exc-3")).toBeInTheDocument();
  });

  it("sorts exceptions by severity (HIGH first, then MEDIUM, then LOW)", () => {
    const list = screen.getByTestId("exception-list");
    const items = within(list).getAllByTestId(/^exception-item-/);
    // First item should be HIGH (exc-1), second MEDIUM (exc-3), third LOW (exc-2)
    expect(items[0]).toHaveAttribute("data-testid", "exception-item-exc-1");
    expect(items[1]).toHaveAttribute("data-testid", "exception-item-exc-3");
    expect(items[2]).toHaveAttribute("data-testid", "exception-item-exc-2");
  });

  it("shows severity badge for each exception", () => {
    expect(screen.getByTestId("exception-severity-exc-1")).toHaveTextContent("HIGH");
  });

  it("shows exception type label", () => {
    expect(screen.getByTestId("exception-type-exc-1")).toHaveTextContent("Missed Lock");
  });

  it("shows resolve button for unresolved exceptions", () => {
    expect(screen.getByTestId("resolve-btn-exc-1")).toBeInTheDocument();
  });

  it("opens resolve dialog on resolve button click", () => {
    fireEvent.click(screen.getByTestId("resolve-btn-exc-1"));
    expect(screen.getByTestId("resolve-exception-dialog")).toBeInTheDocument();
  });

  it("resolve confirm button disabled when resolution note is empty", () => {
    fireEvent.click(screen.getByTestId("resolve-btn-exc-1"));
    expect(screen.getByTestId("resolve-exception-confirm")).toBeDisabled();
  });

  it("calls resolveException API with note and actorUserId on confirm", async () => {
    mockTeamApi.resolveException.mockResolvedValue({
      ...makeException("exc-1"),
      resolved: true,
      resolution: "Addressed the issue",
    });
    fireEvent.click(screen.getByTestId("resolve-btn-exc-1"));
    fireEvent.change(screen.getByTestId("resolution-note-input"), {
      target: { value: "Addressed the issue" },
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId("resolve-exception-confirm"));
    });
    await waitFor(() =>
      expect(mockTeamApi.resolveException).toHaveBeenCalledWith("exc-1", {
        resolverId: "user-dev-1",
        resolution: "Addressed the issue",
      }),
    );
  });

  it("closes resolve dialog on cancel", () => {
    fireEvent.click(screen.getByTestId("resolve-btn-exc-1"));
    expect(screen.getByTestId("resolve-exception-dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByTestId("resolve-exception-dialog")).not.toBeInTheDocument();
  });

  it("shows comment button for unresolved exceptions with a planId", () => {
    // exc-2 has planId = "plan-user-2"
    expect(screen.getByTestId("comment-btn-exc-2")).toBeInTheDocument();
  });

  it("does NOT show comment button for exceptions without a planId", () => {
    // exc-3 has no planId in this fixture — but wait, let me check...
    // makeException creates exc-3 with planId = "plan-user-1" by default
    // Let's override: re-check exc-3 has a planId by default in the fixture
    // All exceptions have planId="plan-user-1" by default → all should have comment btn
    // Let's verify the "no comment when no planId" behavior with a specific exception
    // that has planId: null
    const exceptionsWithoutPlan = [
      makeException("exc-no-plan", { planId: null, severity: "LOW", exceptionType: "OVER_BUDGET" }),
    ];
    vi.mocked(useExceptionQueue).mockReturnValue({
      data: exceptionsWithoutPlan,
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    const { container } = render(
      <MemoryRouter initialEntries={["/team/team-1"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(within(container).getByTestId("tab-exceptions"));
    expect(within(container).queryByTestId("comment-btn-exc-no-plan")).not.toBeInTheDocument();
  });

  it("opens comment dialog on comment button click", () => {
    fireEvent.click(screen.getByTestId("comment-btn-exc-2"));
    expect(screen.getByTestId("add-comment-dialog")).toBeInTheDocument();
  });

  it("calls addComment API on comment confirm", async () => {
    mockTeamApi.addComment.mockResolvedValue({});
    fireEvent.click(screen.getByTestId("comment-btn-exc-2"));
    fireEvent.change(screen.getByTestId("comment-text-input"), {
      target: { value: "Manager note here" },
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId("add-comment-confirm"));
    });
    await waitFor(() =>
      expect(mockTeamApi.addComment).toHaveBeenCalledWith({
        managerId: "user-dev-1",
        planId: "plan-user-2",
        text: "Manager note here",
      }),
    );
  });

  it("filter tabs: 'All' shows resolved and unresolved", () => {
    // Add a resolved exception to the list
    vi.mocked(useExceptionQueue).mockReturnValue({
      data: [
        ...exceptions,
        makeException("exc-resolved", { resolved: true, resolution: "Fixed" }),
      ],
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    const { container } = render(
      <MemoryRouter initialEntries={["/team/team-1"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(within(container).getByTestId("tab-exceptions"));
    fireEvent.click(within(container).getByTestId("exception-filter-all"));
    expect(within(container).getByTestId("exception-item-exc-resolved")).toBeInTheDocument();
    expect(within(container).getByTestId("exception-item-exc-1")).toBeInTheDocument();
  });

  it("filter tabs: 'Resolved' shows only resolved exceptions", () => {
    vi.mocked(useExceptionQueue).mockReturnValue({
      data: [
        ...exceptions,
        makeException("exc-resolved", { resolved: true, resolution: "Fixed" }),
      ],
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    const { container } = render(
      <MemoryRouter initialEntries={["/team/team-1"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(within(container).getByTestId("tab-exceptions"));
    fireEvent.click(within(container).getByTestId("exception-filter-resolved"));
    expect(within(container).getByTestId("exception-item-exc-resolved")).toBeInTheDocument();
    expect(within(container).queryByTestId("exception-item-exc-1")).not.toBeInTheDocument();
  });

  it("shows no-exceptions message when all exceptions are resolved (unresolved filter)", () => {
    vi.mocked(useExceptionQueue).mockReturnValue({
      data: [makeException("exc-1", { resolved: true, resolution: "Done" })],
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    const { container } = render(
      <MemoryRouter initialEntries={["/team/team-1"]}>
        <MockHostProvider>
          <Routes>
            <Route path="/team/:teamId" element={<TeamWeek />} />
          </Routes>
        </MockHostProvider>
      </MemoryRouter>,
    );
    fireEvent.click(within(container).getByTestId("tab-exceptions"));
    expect(within(container).getByTestId("no-exceptions-message")).toBeInTheDocument();
  });
});
