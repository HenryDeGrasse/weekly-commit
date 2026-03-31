/**
 * React hooks for plan recommendation API endpoints.
 */
import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import {
  createRecommendationApi,
  type PlanRecommendation,
  type RecommendationApi,
} from "./recommendationApi.js";

// ── Base hook ─────────────────────────────────────────────────────────────────

/** Returns a stable RecommendationApi instance bound to the current auth token. */
export function useRecommendationApi(): RecommendationApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(() => {
    const client = createApiClient({
      baseUrl: API_BASE_URL,
      getAuthToken: () => bridge.context.authToken,
    });
    return createRecommendationApi(client, authenticatedUser.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken, authenticatedUser.id]);
}

// ── Query hook ────────────────────────────────────────────────────────────────

/**
 * Fetches persisted plan recommendations for the given plan.
 * Uses the read-only GET endpoint — does not trigger re-generation.
 *
 * @param planId plan UUID string, or {@code null} to skip fetching
 */
export function usePlanRecommendations(
  planId: string | null,
): QueryState<PlanRecommendation[]> {
  const api = useRecommendationApi();
  return useQuery<PlanRecommendation[]>(
    `plan-recommendations-${planId ?? "none"}`,
    () => {
      if (!planId) return Promise.reject(new Error("No plan ID"));
      return api.getRecommendations(planId);
    },
    { enabled: planId != null },
  );
}
