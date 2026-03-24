/**
 * Team API call functions built on top of the generic ApiClient.
 * Each function maps to one backend endpoint in TeamController / TicketController.
 */

import type { ApiClient } from "./client.js";
import type {
  TeamWeekViewResponse,
  ExceptionResponse,
  CommentResponse,
  ResolveExceptionPayload,
  AddCommentPayload,
  QuickAssignPayload,
} from "./teamTypes.js";

export function createTeamApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    /**
     * GET /api/teams/{id}/week/{weekStart}
     * Returns the aggregated weekly view for all team members.
     * Full detail for managers; peer-filtered for ICs.
     */
    getTeamWeekView: (
      teamId: string,
      weekStart: string,
    ): Promise<TeamWeekViewResponse> =>
      client.get(
        `/teams/${encodeURIComponent(teamId)}/week/${encodeURIComponent(weekStart)}`,
        { headers: actorHeader },
      ),

    /**
     * GET /api/teams/{id}/week/{weekStart}/exceptions
     * Returns the unresolved exception queue for a team week, sorted by severity.
     */
    getExceptionQueue: (
      teamId: string,
      weekStart: string,
    ): Promise<ExceptionResponse[]> =>
      client.get(
        `/teams/${encodeURIComponent(teamId)}/week/${encodeURIComponent(weekStart)}/exceptions`,
        { headers: actorHeader },
      ),

    /**
     * PUT /api/exceptions/{id}/resolve
     * Marks a manager-review exception as resolved with a resolution note.
     */
    resolveException: (
      exceptionId: string,
      payload: ResolveExceptionPayload,
    ): Promise<ExceptionResponse> =>
      client.put(
        `/exceptions/${encodeURIComponent(exceptionId)}/resolve`,
        payload,
      ),

    /**
     * POST /api/comments
     * Adds a manager comment on a plan or commit.
     */
    addComment: (payload: AddCommentPayload): Promise<CommentResponse> =>
      client.post("/comments", payload),

    /**
     * PUT /api/tickets/{id}
     * Quick-assigns an unassigned ticket to a user.
     * Uses the partial-update endpoint with only assigneeUserId set.
     */
    quickAssignTicket: (
      ticketId: string,
      payload: QuickAssignPayload,
    ): Promise<unknown> =>
      client.put(`/tickets/${encodeURIComponent(ticketId)}`, payload),
  };
}

export type TeamApi = ReturnType<typeof createTeamApi>;
