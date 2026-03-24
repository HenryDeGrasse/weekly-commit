package com.weeklycommit.team.dto;

import com.weeklycommit.domain.enums.PlanState;
import java.util.UUID;

/** Compliance status for a single team member for the selected week. */
public record MemberComplianceSummary(UUID userId, String displayName, boolean lockCompliant,
		boolean reconcileCompliant, boolean autoLocked, PlanState planState, boolean hasPlan) {
}
