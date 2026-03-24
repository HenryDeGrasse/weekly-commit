package com.weeklycommit.team.dto;

import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.plan.dto.CommitResponse;
import java.util.List;
import java.util.UUID;

/**
 * Summary of a single team member's weekly plan, as seen by a manager.
 *
 * <p>
 * {@code commits} is populated with full detail for managers; peers receive a
 * filtered view via {@link PeerMemberWeekView}.
 */
public record MemberWeekView(UUID userId, String displayName, UUID planId, PlanState planState,
		int capacityBudgetPoints, int totalCommittedPoints, List<CommitResponse> commits) {
}
