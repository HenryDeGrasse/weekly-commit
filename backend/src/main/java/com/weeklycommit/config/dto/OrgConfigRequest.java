package com.weeklycommit.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for creating or replacing the organisation-level cadence
 * configuration via {@code PUT /api/config/org}.
 *
 * <p>
 * All fields are required; a {@code PUT} replaces the current configuration
 * entirely.
 */
public record OrgConfigRequest(@NotBlank String weekStartDay, int draftOpenOffsetHours, int lockDueOffsetHours,
		int reconcileOpenOffsetHours, int reconcileDueOffsetHours, @Positive int defaultWeeklyBudget,
		@NotBlank String timezone) {
}
