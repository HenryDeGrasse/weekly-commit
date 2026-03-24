package com.weeklycommit.config.dto;

import com.weeklycommit.domain.entity.TeamConfigOverride;
import java.time.Instant;
import java.util.UUID;

/** Response body for a team-level cadence override. */
public record TeamConfigOverrideResponse(UUID id, UUID teamId, String weekStartDay, Integer draftOpenOffsetHours,
		Integer lockDueOffsetHours, Integer reconcileOpenOffsetHours, Integer reconcileDueOffsetHours,
		Integer defaultWeeklyBudget, String timezone, Instant createdAt, Instant updatedAt) {

	public static TeamConfigOverrideResponse from(TeamConfigOverride o) {
		return new TeamConfigOverrideResponse(o.getId(), o.getTeamId(), o.getWeekStartDay(),
				o.getDraftOpenOffsetHours(), o.getLockDueOffsetHours(), o.getReconcileOpenOffsetHours(),
				o.getReconcileDueOffsetHours(), o.getDefaultWeeklyBudget(), o.getTimezone(), o.getCreatedAt(),
				o.getUpdatedAt());
	}
}
