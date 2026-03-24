package com.weeklycommit.plan.dto;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlanResponse(UUID id, UUID ownerUserId, UUID teamId, LocalDate weekStartDate, PlanState state,
		Instant lockDeadline, Instant reconcileDeadline, int capacityBudgetPoints, boolean compliant,
		boolean systemLockedWithErrors, Instant createdAt, Instant updatedAt) {

	public static PlanResponse from(WeeklyPlan plan) {
		return new PlanResponse(plan.getId(), plan.getOwnerUserId(), plan.getTeamId(), plan.getWeekStartDate(),
				plan.getState(), plan.getLockDeadline(), plan.getReconcileDeadline(), plan.getCapacityBudgetPoints(),
				plan.isCompliant(), plan.isSystemLockedWithErrors(), plan.getCreatedAt(), plan.getUpdatedAt());
	}
}
