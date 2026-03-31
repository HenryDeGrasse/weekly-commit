/**
 * React hooks for Team data fetching.
 * Builds an ApiClient from the host bridge and delegates to teamApi.
 */

import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createTeamApi, type TeamApi } from "./teamApi.js";
import type { TeamWeekViewResponse, ExceptionResponse, TeamMember } from "./teamTypes.js";

/**
 * Returns a stable TeamApi instance bound to the current user's auth token.
 * Re-creates the API object only when the authToken or userId changes.
 */
export function useTeamApi(): TeamApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(
    () => {
      const client = createApiClient({
        baseUrl: API_BASE_URL,
        getAuthToken: () => bridge.context.authToken,
      });
      return createTeamApi(client, authenticatedUser.id);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [authToken, authenticatedUser.id],
  );
}

/**
 * Fetches the aggregated team weekly view.
 * Returns loading state while teamId is null.
 */
export function useTeamWeekView(
  teamId: string | null,
  weekStart: string,
): QueryState<TeamWeekViewResponse> {
  const api = useTeamApi();
  const key = `team-week-${teamId ?? "none"}-${weekStart}`;
  return useQuery<TeamWeekViewResponse>(
    key,
    () => api.getTeamWeekView(teamId ?? "", weekStart),
    { enabled: teamId !== null },
  );
}

/**
 * Fetches the lightweight member list for a team.
 * Used for assignee dropdowns and similar selectors.
 */
export function useTeamMembers(
  teamId: string | null,
): QueryState<TeamMember[]> {
  const api = useTeamApi();
  const key = `team-members-${teamId ?? "none"}`;
  return useQuery<TeamMember[]>(
    key,
    () => api.getTeamMembers(teamId ?? ""),
    { enabled: teamId !== null },
  );
}

/**
 * Fetches the manager-review exception queue for a team week.
 * Sorted by severity (HIGH first) by the backend.
 */
export function useExceptionQueue(
  teamId: string | null,
  weekStart: string,
): QueryState<ExceptionResponse[]> {
  const api = useTeamApi();
  const key = `exceptions-${teamId ?? "none"}-${weekStart}`;
  return useQuery<ExceptionResponse[]>(
    key,
    () => api.getExceptionQueue(teamId ?? "", weekStart),
    { enabled: teamId !== null },
  );
}
