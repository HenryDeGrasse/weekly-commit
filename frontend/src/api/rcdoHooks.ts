/**
 * React hooks for RCDO data fetching.
 * Builds an ApiClient from the host bridge and delegates to rcdoApi.
 */

import { useMemo } from "react";
import { useHostBridge } from "../host/HostProvider.js";
import { createApiClient } from "./client.js";
import { useQuery, type QueryState } from "./hooks.js";
import { createRcdoApi, type RcdoApi } from "./rcdoApi.js";
import type { RcdoTreeNode, RcdoNodeWithPath } from "./rcdoTypes.js";

/**
 * Returns a stable RcdoApi instance bound to the current user's auth token.
 * Re-creates the API object only when the authToken or userId changes.
 */
export function useRcdoApi(): RcdoApi {
  const bridge = useHostBridge();
  const { authToken, authenticatedUser } = bridge.context;
  return useMemo(
    () => {
      const client = createApiClient({
        baseUrl: "/api",
        getAuthToken: () => bridge.context.authToken,
      });
      return createRcdoApi(client, authenticatedUser.id);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [authToken, authenticatedUser.id],
  );
}

/** Fetches the full RCDO hierarchy tree. */
export function useRcdoTree(): QueryState<RcdoTreeNode[]> {
  const api = useRcdoApi();
  return useQuery<RcdoTreeNode[]>("rcdo-tree", () => api.getTree());
}

/** Fetches a single RCDO node with its ancestor path. Returns null when id is null. */
export function useRcdoNode(
  id: string | null,
): QueryState<RcdoNodeWithPath | null> {
  const api = useRcdoApi();
  return useQuery<RcdoNodeWithPath | null>(
    `rcdo-node-${id ?? "none"}`,
    () => (id ? api.getNode(id) : Promise.resolve(null)),
  );
}
