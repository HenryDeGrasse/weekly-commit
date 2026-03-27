/**
 * React hooks for AI API endpoints.
 */
import { useMemo, useState, useEffect, useRef } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createAiApi, type AiApi, type AiStatusResponse, type PlanRiskSignalsResponse, type ReconcileAssistResponse } from "./aiApi.js";

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

/**
 * Auto-fetches AI reconciliation suggestions when a plan enters RECONCILING
 * state. Runs once per planId (tracked by ref). Falls back silently if AI
 * is unavailable — callers must handle `data === undefined`.
 *
 * @param planId       Plan UUID string, or null/undefined when not yet known.
 * @param userId       Authenticated user ID for the reconcile-assist request.
 * @param enabled      Set to `true` only when the plan is in RECONCILING state.
 */
export function useAutoReconcileAssist(
  planId: string | null | undefined,
  userId: string,
  enabled: boolean,
): { data: ReconcileAssistResponse | undefined; loading: boolean; error: string | null } {
  const aiApi = useAiApi();
  const [data, setData] = useState<ReconcileAssistResponse | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Track which planId we've already fetched for so we don't re-fetch on re-renders.
  const fetchedForRef = useRef<string | null>(null);

  useEffect(() => {
    if (!enabled || !planId) return;
    if (fetchedForRef.current === planId) return;
    fetchedForRef.current = planId;

    let cancelled = false;
    setLoading(true);
    setError(null);

    aiApi
      .reconcileAssist({ planId, userId })
      .then((response) => {
        if (cancelled) return;
        if (response.aiAvailable) {
          setData(response);
        }
        setLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        // Graceful silent fallback — AI unavailable or errored.
        setError("AI suggestions unavailable");
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // aiApi is a new object each render but is memoised inside useAiApi by authToken.
    // Intentionally omitting it from deps to avoid re-fetching on unrelated renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planId, userId, enabled]);

  return { data, loading, error };
}
