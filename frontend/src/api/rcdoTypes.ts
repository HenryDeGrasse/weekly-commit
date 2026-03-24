/**
 * TypeScript types for RCDO API request/response payloads.
 * Mirror the backend DTO records.
 */

export type RcdoNodeType =
  | "RALLY_CRY"
  | "DEFINING_OBJECTIVE"
  | "OUTCOME";

export type RcdoNodeStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

/** Tree node returned by GET /api/rcdo/tree */
export interface RcdoTreeNode {
  readonly id: string;
  readonly nodeType: RcdoNodeType;
  readonly status: RcdoNodeStatus;
  readonly title: string;
  readonly children: RcdoTreeNode[];
}

/** Full node detail returned by GET /api/rcdo/nodes/{id} (inside RcdoNodeWithPath) */
export interface RcdoNodeDetail {
  readonly id: string;
  readonly nodeType: RcdoNodeType;
  readonly status: RcdoNodeStatus;
  readonly parentId?: string;
  readonly title: string;
  readonly description?: string;
  readonly ownerTeamId?: string;
  readonly ownerUserId?: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** GET /api/rcdo/nodes/{id} — node + ordered ancestor path [root → … → parent] */
export interface RcdoNodeWithPath {
  readonly node: RcdoNodeDetail;
  readonly ancestorPath: RcdoNodeDetail[];
}

/** POST /api/rcdo/nodes request body */
export interface CreateRcdoNodePayload {
  readonly nodeType: RcdoNodeType;
  readonly parentId?: string;
  readonly title: string;
  readonly description?: string;
  readonly ownerTeamId?: string;
  readonly ownerUserId?: string;
}

/** PUT /api/rcdo/nodes/{id} request body — null fields are ignored by the backend */
export interface UpdateRcdoNodePayload {
  readonly title?: string;
  readonly description?: string;
  readonly ownerTeamId?: string;
  readonly ownerUserId?: string;
}
