package com.weeklycommit.team.dto;

import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Summary of a work item that has no linked commit in this week's plans. */
public record UncommittedTicketSummary(UUID id, String key, String title, TicketStatus status, UUID assigneeUserId,
		UUID teamId, UUID rcdoNodeId, Integer estimatePoints, LocalDate targetWeekStartDate) {

	public static UncommittedTicketSummary from(WorkItem wi) {
		return new UncommittedTicketSummary(wi.getId(), wi.getKey(), wi.getTitle(), wi.getStatus(),
				wi.getAssigneeUserId(), wi.getTeamId(), wi.getRcdoNodeId(), wi.getEstimatePoints(),
				wi.getTargetWeekStartDate());
	}
}
