/**
 * Config API calls for org cadence, team overrides, and capacity management.
 * Maps to ConfigController endpoints.
 */
import type { ApiClient } from "./client.js";

// ── Response types ──────────────────────────────────────────────────────────

export interface OrgConfigResponse {
  readonly id: string;
  readonly orgId: string;
  readonly weekStartDay: string;
  readonly draftOpenOffsetHours: number;
  readonly lockDueOffsetHours: number;
  readonly reconcileOpenOffsetHours: number;
  readonly reconcileDueOffsetHours: number;
  readonly defaultWeeklyBudget: number;
  readonly timezone: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface OrgConfigRequest {
  readonly weekStartDay: string;
  readonly draftOpenOffsetHours: number;
  readonly lockDueOffsetHours: number;
  readonly reconcileOpenOffsetHours: number;
  readonly reconcileDueOffsetHours: number;
  readonly defaultWeeklyBudget: number;
  readonly timezone: string;
}

export interface EffectiveConfigResponse {
  readonly teamId: string;
  readonly orgId: string;
  readonly weekStartDay: string;
  readonly draftOpenOffsetHours: number;
  readonly lockDueOffsetHours: number;
  readonly reconcileOpenOffsetHours: number;
  readonly reconcileDueOffsetHours: number;
  readonly defaultWeeklyBudget: number;
  readonly timezone: string;
  readonly hasTeamOverride: boolean;
}

export interface TeamConfigOverrideRequest {
  readonly weekStartDay?: string | null;
  readonly draftOpenOffsetHours?: number | null;
  readonly lockDueOffsetHours?: number | null;
  readonly reconcileOpenOffsetHours?: number | null;
  readonly reconcileDueOffsetHours?: number | null;
  readonly defaultWeeklyBudget?: number | null;
  readonly timezone?: string | null;
}

export interface TeamConfigOverrideResponse {
  readonly id: string;
  readonly teamId: string;
  readonly weekStartDay: string | null;
  readonly draftOpenOffsetHours: number | null;
  readonly lockDueOffsetHours: number | null;
  readonly reconcileOpenOffsetHours: number | null;
  readonly reconcileDueOffsetHours: number | null;
  readonly defaultWeeklyBudget: number | null;
  readonly timezone: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface EffectiveCapacityResponse {
  readonly userId: string;
  readonly weekStart: string;
  readonly budgetPoints: number;
  readonly source: string;
}

// ── API factory ─────────────────────────────────────────────────────────────

export function createConfigApi(client: ApiClient, actorUserId: string) {
  const actorHeader = { "X-Actor-User-Id": actorUserId };

  return {
    getOrgConfig: (): Promise<OrgConfigResponse> =>
      client.get("/config/org", { headers: actorHeader }),

    updateOrgConfig: (body: OrgConfigRequest): Promise<OrgConfigResponse> =>
      client.put("/config/org", body, { headers: actorHeader }),

    getTeamConfig: (teamId: string): Promise<EffectiveConfigResponse> =>
      client.get(`/config/teams/${encodeURIComponent(teamId)}`),

    updateTeamConfig: (
      teamId: string,
      body: TeamConfigOverrideRequest,
    ): Promise<TeamConfigOverrideResponse> =>
      client.put(`/config/teams/${encodeURIComponent(teamId)}`, body, {
        headers: actorHeader,
      }),

    getEffectiveCapacity: (
      userId: string,
      week: string,
    ): Promise<EffectiveCapacityResponse> =>
      client.get(
        `/config/capacity/${encodeURIComponent(userId)}?week=${encodeURIComponent(week)}`,
      ),
  };
}

export type ConfigApi = ReturnType<typeof createConfigApi>;
