package com.weeklycommit.config.dto;

import java.util.UUID;

/**
 * Resolved (merged) cadence configuration for a team. All fields contain the
 * effective value after applying team overrides on top of org defaults. The
 * {@code hasTeamOverride} flag is {@code true} when at least one field is
 * overridden at the team level.
 */
public record EffectiveConfigResponse(UUID teamId, UUID orgId, String weekStartDay, int draftOpenOffsetHours,
		int lockDueOffsetHours, int reconcileOpenOffsetHours, int reconcileDueOffsetHours, int defaultWeeklyBudget,
		String timezone, boolean hasTeamOverride) {
}
