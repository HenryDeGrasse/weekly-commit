/**
 * React hooks for AI API endpoints.
 */
import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createAiApi, type AiApi, type AiStatusResponse, type PlanRiskSignalsResponse } from "./aiApi.js";

/** Returns a stable AiApi instance bound to the current user's auth token. */
export function useAiApi(): AiApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(() => {
    const client = createApiClient({
      baseUrl: "/api",
      getAuthToken: () => bridge.context.authToken,
    });
    return createAiApi(client, authenticatedUser.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken, authenticatedUser.id]);
}

/** Fetches the AI provider status. */
export function useAiStatus(): QueryState<AiStatusResponse> {
  const api = useAiApi();
  return useQuery<AiStatusResponse>("ai-status", () => api.getStatus());
}

/** Fetches risk signals for a specific plan. */
export function useRiskSignals(
  planId: string | null,
): QueryState<PlanRiskSignalsResponse> {
  const api = useAiApi();
  return useQuery<PlanRiskSignalsResponse>(
    `risk-signals-${planId ?? "none"}`,
    () => {
      if (!planId) return Promise.reject(new Error("No plan ID"));
      return api.getRiskSignals(planId);
    },
    { enabled: planId != null },
  );
}
