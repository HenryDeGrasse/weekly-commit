/**
 * Ticket API call functions built on top of the generic ApiClient.
 * Maps to TicketController backend endpoints.
 */

import type { ApiClient } from "./client.js";
import type {
  TicketResponse,
  TicketSummaryResponse,
  PagedTicketResponse,
  CreateTicketPayload,
  UpdateTicketPayload,
  TicketListParams,
  TicketStatus,
  WeeklyPlanHistoryEntry,
  CarryForwardLineageResponse,
  TeamHistoryResponse,
} from "./ticketTypes.js";

export function createTicketApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /**
     * GET /api/tickets — paged list with optional filters.
     */
    listTickets: (params: TicketListParams = {}): Promise<PagedTicketResponse> => {
      const qs = new URLSearchParams();
      if (params.status) qs.set("status", params.status);
      if (params.assigneeUserId) qs.set("assigneeUserId", params.assigneeUserId);
      if (params.teamId) qs.set("teamId", params.teamId);
      if (params.rcdoNodeId) qs.set("rcdoNodeId", params.rcdoNodeId);
      if (params.targetWeek) qs.set("targetWeek", params.targetWeek);
      if (params.priority) qs.set("priority", params.priority);
      if (params.page != null) qs.set("page", String(params.page));
      if (params.pageSize != null) qs.set("pageSize", String(params.pageSize));
      if (params.sortBy) qs.set("sortBy", params.sortBy);
      if (params.sortDir) qs.set("sortDir", params.sortDir);
      const suffix = qs.toString() ? `?${qs.toString()}` : "";
      return client.get(`/tickets${suffix}`, { headers: actorHeader });
    },

    /** GET /api/tickets/{id} — full ticket detail. */
    getTicket: (ticketId: string): Promise<TicketResponse> =>
      client.get(`/tickets/${encodeURIComponent(ticketId)}`, {
        headers: actorHeader,
      }),

    /** POST /api/tickets — create a new ticket. */
    createTicket: (payload: CreateTicketPayload): Promise<TicketResponse> =>
      client.post("/tickets", payload, { headers: actorHeader }),

    /** PUT /api/tickets/{id} — partial update. */
    updateTicket: (
      ticketId: string,
      payload: UpdateTicketPayload,
    ): Promise<TicketResponse> =>
      client.put(`/tickets/${encodeURIComponent(ticketId)}`, payload, {
        headers: actorHeader,
      }),

    /** DELETE /api/tickets/{id} — soft-delete a ticket. */
    deleteTicket: (ticketId: string): Promise<void> =>
      client.delete(`/tickets/${encodeURIComponent(ticketId)}`, {
        headers: actorHeader,
      }),

    /**
     * PUT /api/tickets/{id}/status — dedicated status transition endpoint.
     * Records who made the change for the audit history.
     */
    transitionStatus: (
      ticketId: string,
      newStatus: TicketStatus,
      changedByUserId: string,
    ): Promise<TicketResponse> =>
      client.put(
        `/tickets/${encodeURIComponent(ticketId)}/status`,
        { status: newStatus, changedByUserId },
        { headers: actorHeader },
      ),

    /**
     * POST /api/tickets/from-commit — create a ticket pre-populated from a commit.
     * The backend copies title, description, RCDO link, estimate, and reporterUserId.
     */
    createTicketFromCommit: (
      commitId: string,
      planId: string,
      overrides?: Partial<CreateTicketPayload>,
    ): Promise<TicketResponse> =>
      client.post(
        "/tickets/from-commit",
        { commitId, planId, ...overrides },
        { headers: actorHeader },
      ),

    // ── Plan history ──────────────────────────────────────────────────────────

    /**
     * GET /api/users/{userId}/plan-history
     * Returns week-by-week plan history for a user.
     */
    getPlanHistory: (userId: string): Promise<WeeklyPlanHistoryEntry[]> =>
      client.get(`/users/${encodeURIComponent(userId)}/plan-history`, {
        headers: actorHeader,
      }),

    // ── Carry-forward lineage ─────────────────────────────────────────────────

    /**
     * GET /api/commits/{commitId}/carry-forward-lineage
     * Returns the full carry-forward provenance chain for a commit.
     */
    getCarryForwardLineage: (
      commitId: string,
    ): Promise<CarryForwardLineageResponse> =>
      client.get(
        `/commits/${encodeURIComponent(commitId)}/carry-forward-lineage`,
        { headers: actorHeader },
      ),

    // ── Team history ──────────────────────────────────────────────────────────

    /**
     * GET /api/teams/{teamId}/history
     * Returns weekly trend data for the team.
     */
    getTeamHistory: (teamId: string): Promise<TeamHistoryResponse> =>
      client.get(`/teams/${encodeURIComponent(teamId)}/history`, {
        headers: actorHeader,
      }),

    /**
     * GET /api/tickets/summary — minimal list (no pagination) for quick selects.
     * Used for commit→ticket linking in forms.
     */
    listTicketSummaries: (
      teamId?: string,
    ): Promise<TicketSummaryResponse[]> => {
      const qs = teamId ? `?teamId=${encodeURIComponent(teamId)}` : "";
      return client.get(`/tickets/summaries${qs}`, { headers: actorHeader });
    },
  };
}

export type TicketApi = ReturnType<typeof createTicketApi>;
