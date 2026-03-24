package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact ticket list item for tables and dropdowns.
 */
public record TicketSummaryResponse(UUID id, String key, String title, TicketStatus status, TicketPriority priority,
		UUID assigneeUserId, UUID teamId, UUID rcdoNodeId, Integer estimatePoints, LocalDate targetWeekStartDate,
		Instant createdAt, Instant updatedAt) {

	public static TicketSummaryResponse from(WorkItem item) {
		return new TicketSummaryResponse(item.getId(), item.getKey(), item.getTitle(), item.getStatus(),
				item.getPriority(), item.getAssigneeUserId(), item.getTeamId(), item.getRcdoNodeId(),
				item.getEstimatePoints(), item.getTargetWeekStartDate(), item.getCreatedAt(), item.getUpdatedAt());
	}
}
