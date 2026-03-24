/**
 * React hooks for Plan and Commit data fetching.
 * Builds an ApiClient from the host bridge and delegates to planApi.
 */

import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createPlanApi, type PlanApi } from "./planApi.js";
import type { PlanWithCommitsResponse } from "./planTypes.js";

/**
 * Returns a stable PlanApi instance bound to the current user's auth token.
 * Re-creates the API object only when the authToken or userId changes.
 */
export function usePlanApi(): PlanApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(
    () => {
      const client = createApiClient({
        baseUrl: "/api",
        getAuthToken: () => bridge.context.authToken,
      });
      return createPlanApi(client, authenticatedUser.id);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [authToken, authenticatedUser.id],
  );
}

/**
 * Fetches (or creates) the weekly plan for the authenticated user.
 * Pass a weekStartDate (yyyy-MM-dd) to view a specific week; omit for current week.
 */
export function useCurrentPlan(
  weekStartDate?: string,
): QueryState<PlanWithCommitsResponse> {
  const api = usePlanApi();
  const bridge = useHostBridge();
  const userId = bridge.context.authenticatedUser.id;
  const key = `plan-${userId}-${weekStartDate ?? "current"}`;
  return useQuery<PlanWithCommitsResponse>(key, () =>
    api.getOrCreatePlan(userId, weekStartDate),
  );
}
