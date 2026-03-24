package com.weeklycommit.team.dto;

import com.weeklycommit.domain.enums.PlanState;
import java.util.List;
import java.util.UUID;

/**
 * Peer-visibility version of a team member's weekly plan. Sensitive fields
 * (outcomes, AI risk flags, manager comments, carry-forward history) are
 * stripped.
 */
public record PeerMemberWeekView(UUID userId, String displayName, UUID planId, PlanState planState,
		List<PeerCommitView> commits) {
}
