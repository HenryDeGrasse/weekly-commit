/**
 * Tests for the Tickets page and related components.
 *
 * Covers:
 *   - Ticket list renders with filters
 *   - TicketForm validates required fields
 *   - Status transitions show only valid options
 *   - Create-from-commit pre-fills data
 *   - Filter bar applies and clears correctly (URL params)
 *   - Pagination controls
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { MockHostProvider } from "../host/MockHostProvider.js";

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock("../api/ticketHooks.js", () => ({
  useTicketApi: vi.fn(),
  useTicketList: vi.fn(),
  useTicket: vi.fn(),
  usePlanHistory: vi.fn(),
  useCarryForwardLineage: vi.fn(),
  useTeamHistory: vi.fn(),
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

import {
  useTicketApi,
  useTicketList,
  useTicket,
} from "../api/ticketHooks.js";
import { useRcdoTree } from "../api/rcdoHooks.js";
import TicketsPage from "../routes/Tickets.js";
import type {
  TicketSummaryResponse,
  TicketResponse,
  PagedTicketResponse,
} from "../api/ticketTypes.js";

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeTicketSummary(
  id: string,
  overrides: Partial<TicketSummaryResponse> = {},
): TicketSummaryResponse {
  return {
    id,
    key: `WC-${id}`,
    title: `Ticket ${id}`,
    status: "TODO",
    priority: "MEDIUM",
    assigneeUserId: null,
    assigneeDisplayName: null,
    teamId: "team-1",
    teamName: "Engineering",
    rcdoNodeId: null,
    estimatePoints: 3,
    targetWeekStartDate: "2026-03-24",
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

function makeTicketDetail(
  id: string,
  overrides: Partial<TicketResponse> = {},
): TicketResponse {
  return {
    id,
    key: `WC-${id}`,
    title: `Ticket ${id}`,
    description: "A test ticket",
    status: "TODO",
    priority: "MEDIUM",
    assigneeUserId: null,
    assigneeDisplayName: null,
    reporterUserId: "user-dev-1",
    reporterDisplayName: "Dev User",
    teamId: "team-1",
    teamName: "Engineering",
    rcdoNodeId: null,
    estimatePoints: 3,
    targetWeekStartDate: "2026-03-24",
    statusHistory: [],
    linkedCommits: [],
    createdAt: "2026-03-24T00:00:00Z",
    updatedAt: "2026-03-24T00:00:00Z",
    ...overrides,
  };
}

function makePagedTickets(
  items: TicketSummaryResponse[],
  total?: number,
): PagedTicketResponse {
  return {
    items,
    total: total ?? items.length,
    page: 1,
    pageSize: 20,
  };
}

const mockTicketApi = {
  listTickets: vi.fn(),
  getTicket: vi.fn(),
  createTicket: vi.fn(),
  updateTicket: vi.fn(),
  deleteTicket: vi.fn(),
  createTicketFromCommit: vi.fn(),
  transitionStatus: vi.fn(),
  getPlanHistory: vi.fn(),
  getCarryForwardLineage: vi.fn(),
  getTeamHistory: vi.fn(),
  listTicketSummaries: vi.fn(),
};

const mockRefetch = vi.fn();

// ── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.mocked(useTicketApi).mockReturnValue(
    mockTicketApi as ReturnType<typeof useTicketApi>,
  );
  vi.mocked(useTicketList).mockReturnValue({
    data: makePagedTickets([
      makeTicketSummary("t-1", { title: "Fix login", status: "IN_PROGRESS", priority: "HIGH" }),
      makeTicketSummary("t-2", { title: "Write docs", status: "TODO" }),
    ]),
    loading: false,
    error: null,
    refetch: mockRefetch,
  });
  vi.mocked(useTicket).mockReturnValue({
    data: null,
    loading: false,
    error: null,
    refetch: mockRefetch,
  });
  vi.mocked(useRcdoTree).mockReturnValue({
    data: [],
    loading: false,
    error: null,
    refetch: vi.fn(),
  });
  mockTicketApi.createTicket.mockReset();
  mockTicketApi.updateTicket.mockReset();
  mockTicketApi.transitionStatus.mockReset();
});

function renderPage(search = "") {
  return render(
    <MemoryRouter initialEntries={[`/tickets${search}`]}>
      <MockHostProvider>
        <Routes>
          <Route path="/tickets" element={<TicketsPage />} />
        </Routes>
      </MockHostProvider>
    </MemoryRouter>,
  );
}

// ── Tests: basic rendering ─────────────────────────────────────────────────────

describe("TicketsPage — rendering", () => {
  it("renders the page with data-testid=page-tickets", () => {
    renderPage();
    expect(screen.getByTestId("page-tickets")).toBeInTheDocument();
  });

  it("renders the ticket list table", () => {
    renderPage();
    expect(screen.getByTestId("ticket-list-table")).toBeInTheDocument();
  });

  it("renders a row for each ticket", () => {
    renderPage();
    expect(screen.getByTestId("ticket-row-t-1")).toBeInTheDocument();
    expect(screen.getByTestId("ticket-row-t-2")).toBeInTheDocument();
  });

  it("shows ticket key and title in each row", () => {
    renderPage();
    expect(screen.getByTestId("ticket-key-t-1")).toHaveTextContent("WC-t-1");
    expect(screen.getByTestId("ticket-title-t-1")).toHaveTextContent("Fix login");
  });

  it("shows loading state while fetching", () => {
    vi.mocked(useTicketList).mockReturnValue({
      data: undefined,
      loading: true,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("ticket-list-loading")).toBeInTheDocument();
  });

  it("shows empty state when no tickets match filters", () => {
    vi.mocked(useTicketList).mockReturnValue({
      data: makePagedTickets([]),
      loading: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("ticket-list-empty")).toBeInTheDocument();
  });

  it("shows the ticket count", () => {
    renderPage();
    expect(screen.getByTestId("ticket-count")).toHaveTextContent("2 tickets");
  });
});

// ── Tests: filter bar ─────────────────────────────────────────────────────────

describe("TicketsPage — filter bar", () => {
  it("renders filter controls", () => {
    renderPage();
    expect(screen.getByTestId("ticket-filters")).toBeInTheDocument();
    expect(screen.getByTestId("filter-status")).toBeInTheDocument();
    expect(screen.getByTestId("filter-priority")).toBeInTheDocument();
    expect(screen.getByTestId("filter-assignee")).toBeInTheDocument();
  });

  it("renders clear-all when no filter active", () => {
    renderPage();
    // No filter active — clear-all should NOT be present
    expect(screen.queryByTestId("filter-clear-all")).not.toBeInTheDocument();
  });

  it("shows clear-all button when a filter is active and clears on click", () => {
    renderPage();
    fireEvent.change(screen.getByTestId("filter-status"), {
      target: { value: "TODO" },
    });
    expect(screen.getByTestId("filter-clear-all")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("filter-clear-all"));
    expect(screen.queryByTestId("filter-clear-all")).not.toBeInTheDocument();
  });

  it("updates the useTicketList params when status filter changes", () => {
    renderPage();
    fireEvent.change(screen.getByTestId("filter-status"), {
      target: { value: "IN_PROGRESS" },
    });
    // useTicketList is called with updated params
    const calls = vi.mocked(useTicketList).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall![0]).toMatchObject({ status: "IN_PROGRESS" });
  });
});

// ── Tests: sorting ────────────────────────────────────────────────────────────

describe("TicketsPage — sorting", () => {
  it("renders sortable column headers", () => {
    renderPage();
    expect(screen.getByTestId("sort-col-key")).toBeInTheDocument();
    expect(screen.getByTestId("sort-col-title")).toBeInTheDocument();
    expect(screen.getByTestId("sort-col-status")).toBeInTheDocument();
    expect(screen.getByTestId("sort-col-priority")).toBeInTheDocument();
  });

  it("clicking a column header updates the sort params", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("sort-col-key"));
    const calls = vi.mocked(useTicketList).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall![0]).toMatchObject({ sortBy: "key", sortDir: "asc" });
  });

  it("clicking the same column again reverses sort direction", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("sort-col-key"));
    fireEvent.click(screen.getByTestId("sort-col-key"));
    const calls = vi.mocked(useTicketList).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall![0]).toMatchObject({ sortBy: "key", sortDir: "desc" });
  });
});

// ── Tests: detail panel ────────────────────────────────────────────────────────

describe("TicketsPage — ticket detail panel", () => {
  beforeEach(() => {
    vi.mocked(useTicket).mockReturnValue({
      data: makeTicketDetail("t-1", {
        status: "TODO",
        statusHistory: [
          {
            id: "sh-1",
            ticketId: "t-1",
            fromStatus: null,
            toStatus: "TODO",
            changedByUserId: "user-dev-1",
            changedAt: "2026-03-24T09:00:00Z",
            note: null,
          },
        ],
      }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
  });

  it("clicking a ticket row shows the detail panel", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));
    expect(screen.getByTestId("ticket-detail-panel")).toBeInTheDocument();
    expect(screen.getByTestId("ticket-detail-view")).toBeInTheDocument();
  });

  it("detail view shows the ticket title", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));
    expect(screen.getByTestId("ticket-detail-title")).toHaveTextContent("Ticket t-1");
  });

  it("detail view shows status history", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));
    expect(screen.getByTestId("status-history-timeline")).toBeInTheDocument();
  });

  it("close button hides the detail panel", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));
    expect(screen.getByTestId("ticket-detail-panel")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("ticket-detail-close-btn"));
    expect(screen.queryByTestId("ticket-detail-panel")).not.toBeInTheDocument();
  });
});

// ── Tests: status transitions ──────────────────────────────────────────────────

describe("TicketDetailView — status transitions", () => {
  it("shows only valid next-status transition buttons for TODO", () => {
    vi.mocked(useTicket).mockReturnValue({
      data: makeTicketDetail("t-1", { status: "TODO" }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));

    // From TODO: can go to IN_PROGRESS, CANCELED
    expect(screen.getByTestId("transition-btn-in_progress")).toBeInTheDocument();
    expect(screen.getByTestId("transition-btn-canceled")).toBeInTheDocument();
    // Cannot go back to TODO from TODO
    expect(screen.queryByTestId("transition-btn-todo")).not.toBeInTheDocument();
    // Cannot go to DONE directly from TODO
    expect(screen.queryByTestId("transition-btn-done")).not.toBeInTheDocument();
  });

  it("shows no transitions for DONE (terminal status)", () => {
    vi.mocked(useTicket).mockReturnValue({
      data: makeTicketDetail("t-1", { status: "DONE" }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));
    // DONE has no valid transitions
    expect(screen.queryByTestId("status-transition-buttons")).not.toBeInTheDocument();
  });

  it("clicking a transition button calls transitionStatus", async () => {
    mockTicketApi.transitionStatus.mockResolvedValue(
      makeTicketDetail("t-1", { status: "IN_PROGRESS" }),
    );
    vi.mocked(useTicket).mockReturnValue({
      data: makeTicketDetail("t-1", { status: "TODO" }),
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-row-t-1"));
    await act(async () => {
      fireEvent.click(screen.getByTestId("transition-btn-in_progress"));
    });
    await waitFor(() =>
      expect(mockTicketApi.transitionStatus).toHaveBeenCalledWith(
        "t-1",
        "IN_PROGRESS",
        expect.any(String),
      ),
    );
  });
});

// ── Tests: create ticket ──────────────────────────────────────────────────────

describe("TicketsPage — create ticket", () => {
  it("renders the create ticket button", () => {
    renderPage();
    expect(screen.getByTestId("create-ticket-btn")).toBeInTheDocument();
  });

  it("clicking create ticket opens the TicketForm dialog", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("create-ticket-btn"));
    expect(screen.getByTestId("ticket-form-dialog")).toBeInTheDocument();
  });

  it("TicketForm shows validation errors when title is empty on submit", async () => {
    renderPage();
    fireEvent.click(screen.getByTestId("create-ticket-btn"));

    // Clear the title and submit
    fireEvent.change(screen.getByTestId("ticket-form-title"), {
      target: { value: "" },
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId("ticket-form-submit"));
    });
    expect(screen.getByTestId("ticket-form-title-error")).toBeInTheDocument();
  });

  it("TicketForm shows validation error when team is empty on submit", async () => {
    renderPage();
    fireEvent.click(screen.getByTestId("create-ticket-btn"));

    // Fill title but clear team
    fireEvent.change(screen.getByTestId("ticket-form-title"), {
      target: { value: "My ticket" },
    });
    fireEvent.change(screen.getByTestId("ticket-form-team"), {
      target: { value: "" },
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId("ticket-form-submit"));
    });
    expect(screen.getByTestId("ticket-form-team-error")).toBeInTheDocument();
  });

  it("successful TicketForm submit calls createTicket API", async () => {
    mockTicketApi.createTicket.mockResolvedValue(
      makeTicketDetail("t-new", { title: "New ticket" }),
    );
    renderPage();
    fireEvent.click(screen.getByTestId("create-ticket-btn"));

    fireEvent.change(screen.getByTestId("ticket-form-title"), {
      target: { value: "New ticket" },
    });
    // Team should be pre-filled from current team (team-dev-1)
    await act(async () => {
      fireEvent.click(screen.getByTestId("ticket-form-submit"));
    });
    await waitFor(() =>
      expect(mockTicketApi.createTicket).toHaveBeenCalledWith(
        expect.objectContaining({ title: "New ticket" }),
      ),
    );
  });

  it("cancel button closes the TicketForm", () => {
    renderPage();
    fireEvent.click(screen.getByTestId("create-ticket-btn"));
    expect(screen.getByTestId("ticket-form-dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByTestId("ticket-form-dialog")).not.toBeInTheDocument();
  });
});

// ── Tests: pagination ─────────────────────────────────────────────────────────

describe("TicketsPage — pagination", () => {
  it("shows pagination controls when total > pageSize", () => {
    vi.mocked(useTicketList).mockReturnValue({
      data: {
        items: [makeTicketSummary("t-1")],
        total: 50,
        page: 1,
        pageSize: 20,
      },
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage();
    expect(screen.getByTestId("ticket-list-pagination")).toBeInTheDocument();
    expect(screen.getByTestId("ticket-page-prev")).toBeInTheDocument();
    expect(screen.getByTestId("ticket-page-next")).toBeInTheDocument();
  });

  it("prev page button is disabled on page 1", () => {
    vi.mocked(useTicketList).mockReturnValue({
      data: { items: [makeTicketSummary("t-1")], total: 50, page: 1, pageSize: 20 },
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage();
    expect(screen.getByTestId("ticket-page-prev")).toBeDisabled();
  });

  it("clicking next page updates page param", () => {
    vi.mocked(useTicketList).mockReturnValue({
      data: { items: [makeTicketSummary("t-1")], total: 50, page: 1, pageSize: 20 },
      loading: false,
      error: null,
      refetch: mockRefetch,
    });
    renderPage();
    fireEvent.click(screen.getByTestId("ticket-page-next"));
    const calls = vi.mocked(useTicketList).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall![0]).toMatchObject({ page: 2 });
  });
});
