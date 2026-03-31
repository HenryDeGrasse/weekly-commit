/**
 * React hooks for RAG (Retrieval-Augmented Generation) API endpoints.
 */
import { useCallback, useMemo, useState } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import {
  createRagApi,
  type InsightListResponse,
  type RagApi,
  type RagQueryResponse,
} from "./ragApi.js";

// ── Base hook ─────────────────────────────────────────────────────────────────

/** Returns a stable {@link RagApi} instance bound to the current user. */
export function useRagApi(): RagApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(() => {
    const client = createApiClient({
      baseUrl: __WC_API_BASE_URL__,
      getAuthToken: () => bridge.context.authToken,
    });
    return createRagApi(client, authenticatedUser.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken, authenticatedUser.id]);
}

// ── Query hooks ───────────────────────────────────────────────────────────────

/**
 * Fetches TEAM_INSIGHT cards for the given team and week.
 *
 * @param teamId    team UUID string, or {@code null} to skip fetching
 * @param weekStart ISO date string (YYYY-MM-DD) for the Monday of the target week
 */
export function useTeamInsights(
  teamId: string | null,
  weekStart: string,
): QueryState<InsightListResponse> {
  const api = useRagApi();
  return useQuery<InsightListResponse>(
    `team-insights-${teamId ?? "none"}-${weekStart}`,
    () => {
      if (!teamId) return Promise.reject(new Error("No team ID"));
      return api.getTeamInsights(teamId, weekStart);
    },
    { enabled: teamId != null },
  );
}

/**
 * Fetches PERSONAL_INSIGHT cards for the given plan.
 *
 * @param planId plan UUID string, or {@code null} to skip fetching
 */
export function usePlanInsights(
  planId: string | null,
): QueryState<InsightListResponse> {
  const api = useRagApi();
  return useQuery<InsightListResponse>(
    `plan-insights-${planId ?? "none"}`,
    () => {
      if (!planId) return Promise.reject(new Error("No plan ID"));
      return api.getPlanInsights(planId);
    },
    { enabled: planId != null },
  );
}

// ── Mutation hook ─────────────────────────────────────────────────────────────

export interface SemanticQueryState {
  /** Executes the RAG query. */
  mutate: (question: string, teamId?: string, userId?: string) => Promise<void>;
  /** The most recent query response; {@code undefined} before first call. */
  data: RagQueryResponse | undefined;
  /** {@code true} while a query is in-flight. */
  loading: boolean;
  /** Error message if the last query failed. */
  error: string | null;
}

/**
 * Mutation hook for submitting a semantic RAG query.
 *
 * @example
 * ```tsx
 * const { mutate, data, loading, error } = useSemanticQuery();
 *
 * const handleSearch = (question: string) => {
 *   mutate(question, teamId, userId);
 * };
 * ```
 */
export function useSemanticQuery(): SemanticQueryState {
  const api = useRagApi();
  const [data, setData] = useState<RagQueryResponse | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutate = useCallback(
    async (question: string, teamId?: string, userId?: string) => {
      setLoading(true);
      setError(null);
      try {
        const result = await api.submitQuery({
          question,
          ...(teamId !== undefined && { teamId }),
          ...(userId !== undefined && { userId }),
        });
        setData(result);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : "Unexpected error");
        setData(undefined);
      } finally {
        setLoading(false);
      }
    },
    [api],
  );

  return { mutate, data, loading, error };
}
