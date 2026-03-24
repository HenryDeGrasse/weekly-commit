package com.weeklycommit.team.dto;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import java.util.UUID;

/**
 * Commit data visible to same-team peers. Strips sensitive fields: AI risk
 * flags, historical patterns (carry-forward streak/source), manager comments,
 * and reconcile notes (outcome, outcomeNotes).
 */
public record PeerCommitView(UUID id, UUID planId, UUID ownerUserId, String title, ChessPiece chessPiece,
		int priorityOrder, UUID rcdoNodeId, UUID workItemId, Integer estimatePoints) {

	public static PeerCommitView from(WeeklyCommit c) {
		return new PeerCommitView(c.getId(), c.getPlanId(), c.getOwnerUserId(), c.getTitle(), c.getChessPiece(),
				c.getPriorityOrder(), c.getRcdoNodeId(), c.getWorkItemId(), c.getEstimatePoints());
	}
}
