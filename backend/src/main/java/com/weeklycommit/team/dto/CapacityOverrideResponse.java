package com.weeklycommit.team.dto;

import com.weeklycommit.domain.entity.CapacityOverride;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a capacity override record. */
public record CapacityOverrideResponse(UUID id, UUID userId, LocalDate weekStartDate, int budgetPoints, String reason,
		UUID setByManagerId, Instant createdAt) {

	public static CapacityOverrideResponse from(CapacityOverride co) {
		return new CapacityOverrideResponse(co.getId(), co.getUserId(), co.getWeekStartDate(), co.getBudgetPoints(),
				co.getReason(), co.getSetByManagerId(), co.getCreatedAt());
	}
}
