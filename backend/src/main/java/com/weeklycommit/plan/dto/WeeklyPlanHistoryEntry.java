package com.weeklycommit.plan.dto;

import com.weeklycommit.domain.enums.PlanState;
import java.time.LocalDate;
import java.util.UUID;

public record WeeklyPlanHistoryEntry(UUID planId, LocalDate weekStartDate, PlanState planState, boolean compliant,
		int commitCount, int plannedPoints, int achievedPoints, int carryForwardCount) {
}
