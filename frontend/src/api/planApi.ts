/**
 * Plan and Commit API call functions built on top of the generic ApiClient.
 * Each function maps to one backend endpoint in PlanController.
 */

import type { ApiClient } from "./client.js";
import type {
  PlanWithCommitsResponse,
  CreateCommitPayload,
  UpdateCommitPayload,
  CommitResponse,
  LockResponse,
  ScopeChangePayload,
  ScopeChangeTimelineResponse,
  ReconciliationViewResponse,
  SetOutcomePayload,
  CarryForwardPayload,
} from "./planTypes.js";

export function createPlanApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /**
     * POST /api/plans — get-or-create the plan for a user + optional week.
     * If weekStartDate is omitted, the backend uses the current Monday.
     */
    getOrCreatePlan: (
      userId: string,
      weekStartDate?: string,
    ): Promise<PlanWithCommitsResponse> =>
      client.post("/plans", {
        userId,
        ...(weekStartDate ? { weekStartDate } : {}),
      }),

    /** GET /api/plans/{id} — plan detail with commits in priority order. */
    getPlan: (planId: string): Promise<PlanWithCommitsResponse> =>
      client.get(`/plans/${encodeURIComponent(planId)}`),

    /** POST /api/plans/{planId}/commits — create a commit on a DRAFT plan. */
    createCommit: (
      planId: string,
      payload: CreateCommitPayload,
    ): Promise<CommitResponse> =>
      client.post(
        `/plans/${encodeURIComponent(planId)}/commits`,
        payload,
        { headers: actorHeader },
      ),

    /** PUT /api/plans/{planId}/commits/{commitId} — update mutable commit fields. */
    updateCommit: (
      planId: string,
      commitId: string,
      payload: UpdateCommitPayload,
    ): Promise<CommitResponse> =>
      client.put(
        `/plans/${encodeURIComponent(planId)}/commits/${encodeURIComponent(commitId)}`,
        payload,
        { headers: actorHeader },
      ),

    /** DELETE /api/plans/{planId}/commits/{commitId} — delete a commit. */
    deleteCommit: (planId: string, commitId: string): Promise<void> =>
      client.delete(
        `/plans/${encodeURIComponent(planId)}/commits/${encodeURIComponent(commitId)}`,
        { headers: actorHeader },
      ),

    /**
     * PUT /api/plans/{planId}/commits/reorder — reorder commits by supplying
     * all commit IDs in the desired priority order.
     */
    reorderCommits: (
      planId: string,
      commitIds: string[],
    ): Promise<PlanWithCommitsResponse> =>
      client.put(
        `/plans/${encodeURIComponent(planId)}/commits/reorder`,
        { commitIds },
        { headers: actorHeader },
      ),

    /**
     * POST /api/plans/{id}/lock — manually lock a DRAFT plan.
     * Returns LockResponse: success=true with locked plan, or success=false
     * with validation errors that block the lock.
     */
    lockPlan: (planId: string): Promise<LockResponse> =>
      client.post(
        `/plans/${encodeURIComponent(planId)}/lock`,
        {},
        { headers: actorHeader },
      ),

    // ── Post-lock scope changes ──────────────────────────────────────────────

    /**
     * POST /api/plans/{id}/scope-changes — apply a post-lock scope change.
     * Action = ADD | REMOVE | EDIT, each with required fields in the payload.
     */
    applyScopeChange: (
      planId: string,
      payload: ScopeChangePayload,
    ): Promise<ScopeChangeTimelineResponse> =>
      client.post(
        `/plans/${encodeURIComponent(planId)}/scope-changes`,
        payload,
        { headers: actorHeader },
      ),

    /** GET /api/plans/{id}/scope-changes — chronological scope-change timeline. */
    getScopeChangeTimeline: (
      planId: string,
    ): Promise<ScopeChangeTimelineResponse> =>
      client.get(`/plans/${encodeURIComponent(planId)}/scope-changes`),

    // ── Reconciliation ───────────────────────────────────────────────────────

    /**
     * POST /api/plans/{id}/reconcile/open — transition LOCKED → RECONCILING.
     * Idempotent; returns the reconciliation view immediately.
     */
    openReconciliation: (planId: string): Promise<ReconciliationViewResponse> =>
      client.post(
        `/plans/${encodeURIComponent(planId)}/reconcile/open`,
        {},
        { headers: actorHeader },
      ),

    /** GET /api/plans/{id}/reconcile — reconciliation view (RECONCILING or RECONCILED). */
    getReconciliationView: (
      planId: string,
    ): Promise<ReconciliationViewResponse> =>
      client.get(`/plans/${encodeURIComponent(planId)}/reconcile`),

    /** PUT /api/plans/{id}/commits/{commitId}/outcome — set outcome for a commit. */
    setCommitOutcome: (
      planId: string,
      commitId: string,
      payload: SetOutcomePayload,
    ): Promise<CommitResponse> =>
      client.put(
        `/plans/${encodeURIComponent(planId)}/commits/${encodeURIComponent(commitId)}/outcome`,
        payload,
        { headers: actorHeader },
      ),

    /** POST /api/plans/{id}/reconcile/submit — submit (finalise) reconciliation. */
    submitReconciliation: (
      planId: string,
    ): Promise<ReconciliationViewResponse> =>
      client.post(
        `/plans/${encodeURIComponent(planId)}/reconcile/submit`,
        {},
        { headers: actorHeader },
      ),

    // ── Carry forward ────────────────────────────────────────────────────────

    /**
     * POST /api/plans/{planId}/commits/{commitId}/carry-forward
     * Creates a copy of the commit in the target week plan.
     */
    carryForward: (
      planId: string,
      commitId: string,
      payload: CarryForwardPayload,
    ): Promise<unknown> =>
      client.post(
        `/plans/${encodeURIComponent(planId)}/commits/${encodeURIComponent(commitId)}/carry-forward`,
        payload,
        { headers: actorHeader },
      ),
  };
}

export type PlanApi = ReturnType<typeof createPlanApi>;
