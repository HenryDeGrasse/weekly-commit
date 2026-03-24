package com.weeklycommit.plan.dto;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import java.time.Instant;
import java.util.UUID;

public record CommitResponse(UUID id, UUID planId, UUID ownerUserId, String title, String description,
		ChessPiece chessPiece, int priorityOrder, UUID rcdoNodeId, UUID workItemId, Integer estimatePoints,
		String successCriteria, CommitOutcome outcome, String outcomeNotes, int carryForwardStreak, Instant createdAt,
		Instant updatedAt) {

	public static CommitResponse from(WeeklyCommit c) {
		return new CommitResponse(c.getId(), c.getPlanId(), c.getOwnerUserId(), c.getTitle(), c.getDescription(),
				c.getChessPiece(), c.getPriorityOrder(), c.getRcdoNodeId(), c.getWorkItemId(), c.getEstimatePoints(),
				c.getSuccessCriteria(), c.getOutcome(), c.getOutcomeNotes(), c.getCarryForwardStreak(),
				c.getCreatedAt(), c.getUpdatedAt());
	}
}
