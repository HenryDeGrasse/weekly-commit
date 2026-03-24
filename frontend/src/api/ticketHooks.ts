/**
 * React hooks for Ticket, Plan-history, and Team-history data fetching.
 */

import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createTicketApi, type TicketApi } from "./ticketApi.js";
import type {
  PagedTicketResponse,
  TicketResponse,
  TicketListParams,
  WeeklyPlanHistoryEntry,
  CarryForwardLineageResponse,
  TeamHistoryResponse,
} from "./ticketTypes.js";

/**
 * Returns a stable TicketApi instance bound to the current user's auth token.
 */
export function useTicketApi(): TicketApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(
    () => {
      const client = createApiClient({
        baseUrl: "/api",
        getAuthToken: () => bridge.context.authToken,
      });
      return createTicketApi(client, authenticatedUser.id);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [authToken, authenticatedUser.id],
  );
}

/**
 * Fetches a paged ticket list with optional filters.
 * The params object is serialized as the cache key.
 */
export function useTicketList(
  params: TicketListParams = {},
): QueryState<PagedTicketResponse> {
  const api = useTicketApi();
  const key = `tickets-${JSON.stringify(params)}`;
  return useQuery<PagedTicketResponse>(key, () => api.listTickets(params));
}

/**
 * Fetches full ticket detail for a single ticket.
 * Returns null data when ticketId is null.
 */
export function useTicket(
  ticketId: string | null,
): QueryState<TicketResponse | null> {
  const api = useTicketApi();
  const key = `ticket-${ticketId ?? "none"}`;
  return useQuery<TicketResponse | null>(
    key,
    () => (ticketId ? api.getTicket(ticketId) : Promise.resolve(null)),
    { enabled: ticketId !== null },
  );
}

/**
 * Fetches the week-by-week plan history for a user.
 */
export function usePlanHistory(
  userId: string | null,
): QueryState<WeeklyPlanHistoryEntry[]> {
  const api = useTicketApi();
  const key = `plan-history-${userId ?? "none"}`;
  return useQuery<WeeklyPlanHistoryEntry[]>(
    key,
    () => (userId ? api.getPlanHistory(userId) : Promise.resolve([])),
    { enabled: userId !== null },
  );
}

/**
 * Fetches the carry-forward lineage for a commit.
 */
export function useCarryForwardLineage(
  commitId: string | null,
): QueryState<CarryForwardLineageResponse | null> {
  const api = useTicketApi();
  const key = `cf-lineage-${commitId ?? "none"}`;
  return useQuery<CarryForwardLineageResponse | null>(
    key,
    () =>
      commitId
        ? api.getCarryForwardLineage(commitId)
        : Promise.resolve(null),
    { enabled: commitId !== null },
  );
}

/**
 * Fetches the weekly trend history for a team.
 */
export function useTeamHistory(
  teamId: string | null,
): QueryState<TeamHistoryResponse | null> {
  const api = useTicketApi();
  const key = `team-history-${teamId ?? "none"}`;
  return useQuery<TeamHistoryResponse | null>(
    key,
    () => (teamId ? api.getTeamHistory(teamId) : Promise.resolve(null)),
    { enabled: teamId !== null },
  );
}
