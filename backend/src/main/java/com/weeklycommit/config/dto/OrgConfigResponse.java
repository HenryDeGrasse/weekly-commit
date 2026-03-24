package com.weeklycommit.config.dto;

import com.weeklycommit.domain.entity.OrgConfig;
import java.time.Instant;
import java.util.UUID;

/** Response body for organisation-level cadence configuration. */
public record OrgConfigResponse(UUID id, UUID orgId, String weekStartDay, int draftOpenOffsetHours,
		int lockDueOffsetHours, int reconcileOpenOffsetHours, int reconcileDueOffsetHours, int defaultWeeklyBudget,
		String timezone, Instant createdAt, Instant updatedAt) {

	public static OrgConfigResponse from(OrgConfig c) {
		return new OrgConfigResponse(c.getId(), c.getOrgId(), c.getWeekStartDay(), c.getDraftOpenOffsetHours(),
				c.getLockDueOffsetHours(), c.getReconcileOpenOffsetHours(), c.getReconcileDueOffsetHours(),
				c.getDefaultWeeklyBudget(), c.getTimezone(), c.getCreatedAt(), c.getUpdatedAt());
	}
}
