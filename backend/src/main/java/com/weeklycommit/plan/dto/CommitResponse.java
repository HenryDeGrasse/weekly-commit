package com.weeklycommit.plan.dto;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import java.time.Instant;
import java.util.UUID;

public record CommitResponse(UUID id, UUID planId, UUID ownerUserId, String title, String description,
		ChessPiece chessPiece, int priorityOrder, UUID rcdoNodeId, UUID workItemId, String workItemKey,
		Integer estimatePoints, String successCriteria, CommitOutcome outcome, String outcomeNotes,
		int carryForwardStreak, Instant createdAt, Instant updatedAt) {

	/**
	 * Create a response without a resolved ticket key (legacy / single-commit
	 * endpoints).
	 */
	public static CommitResponse from(WeeklyCommit c) {
		return from(c, null);
	}

	/** Create a response with a resolved ticket key. */
	public static CommitResponse from(WeeklyCommit c, String workItemKey) {
		return new CommitResponse(c.getId(), c.getPlanId(), c.getOwnerUserId(), c.getTitle(), c.getDescription(),
				c.getChessPiece(), c.getPriorityOrder(), c.getRcdoNodeId(), c.getWorkItemId(), workItemKey,
				c.getEstimatePoints(), c.getSuccessCriteria(), c.getOutcome(), c.getOutcomeNotes(),
				c.getCarryForwardStreak(), c.getCreatedAt(), c.getUpdatedAt());
	}
}
