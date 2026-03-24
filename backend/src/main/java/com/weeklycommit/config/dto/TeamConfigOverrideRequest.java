package com.weeklycommit.config.dto;

/**
 * Request body for creating or replacing a team-level cadence override via
 * {@code PUT /api/config/teams/{id}}.
 *
 * <p>
 * All fields are nullable. A {@code null} value means "remove this override and
 * fall back to the org-level default".
 */
public record TeamConfigOverrideRequest(String weekStartDay, Integer draftOpenOffsetHours, Integer lockDueOffsetHours,
		Integer reconcileOpenOffsetHours, Integer reconcileDueOffsetHours, Integer defaultWeeklyBudget,
		String timezone) {
}
