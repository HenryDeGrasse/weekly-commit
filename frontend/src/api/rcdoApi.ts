/**
 * RCDO-specific API call functions built on top of the generic ApiClient.
 * Each function maps to one backend endpoint in RcdoController.
 */

import type { ApiClient } from "./client.js";
import type {
  RcdoTreeNode,
  RcdoNodeDetail,
  RcdoNodeWithPath,
  CreateRcdoNodePayload,
  UpdateRcdoNodePayload,
} from "./rcdoTypes.js";

export function createRcdoApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /** GET /api/rcdo/tree — full hierarchy tree */
    getTree: (): Promise<RcdoTreeNode[]> => client.get("/rcdo/tree"),

    /** GET /api/rcdo/nodes/{id} — node with ancestor path */
    getNode: (id: string): Promise<RcdoNodeWithPath> =>
      client.get(`/rcdo/nodes/${encodeURIComponent(id)}`),

    /** GET /api/rcdo/nodes — flat list with optional filters */
    listNodes: (params?: {
      type?: string;
      status?: string;
      parentId?: string;
    }): Promise<RcdoNodeDetail[]> => {
      const qs = new URLSearchParams();
      if (params?.type) qs.set("type", params.type);
      if (params?.status) qs.set("status", params.status);
      if (params?.parentId) qs.set("parentId", params.parentId);
      const query = qs.toString();
      return client.get(query ? `/rcdo/nodes?${query}` : "/rcdo/nodes");
    },

    /** POST /api/rcdo/nodes — create a new node */
    createNode: (payload: CreateRcdoNodePayload): Promise<RcdoNodeDetail> =>
      client.post("/rcdo/nodes", payload, { headers: actorHeader }),

    /** PUT /api/rcdo/nodes/{id} — update mutable fields */
    updateNode: (
      id: string,
      payload: UpdateRcdoNodePayload,
    ): Promise<RcdoNodeDetail> =>
      client.put(`/rcdo/nodes/${encodeURIComponent(id)}`, payload, {
        headers: actorHeader,
      }),

    /** POST /api/rcdo/nodes/{id}/activate — DRAFT → ACTIVE */
    activateNode: (id: string): Promise<RcdoNodeDetail> =>
      client.post(
        `/rcdo/nodes/${encodeURIComponent(id)}/activate`,
        {},
        { headers: actorHeader },
      ),

    /** POST /api/rcdo/nodes/{id}/archive — archive node (fails if has active children) */
    archiveNode: (id: string): Promise<RcdoNodeDetail> =>
      client.post(
        `/rcdo/nodes/${encodeURIComponent(id)}/archive`,
        {},
        { headers: actorHeader },
      ),

    /** POST /api/rcdo/nodes/{id}/move — re-parent node */
    moveNode: (id: string, newParentId: string): Promise<RcdoNodeDetail> =>
      client.post(
        `/rcdo/nodes/${encodeURIComponent(id)}/move`,
        { newParentId },
        { headers: actorHeader },
      ),
  };
}

export type RcdoApi = ReturnType<typeof createRcdoApi>;
