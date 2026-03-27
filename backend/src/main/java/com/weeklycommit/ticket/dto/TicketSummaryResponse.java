package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact ticket list item for tables and dropdowns. Includes resolved display
 * names for assignee and team so the frontend never needs to show raw UUIDs.
 */
public record TicketSummaryResponse(UUID id, String key, String title, TicketStatus status, TicketPriority priority,
		UUID assigneeUserId, String assigneeDisplayName, UUID teamId, String teamName, UUID rcdoNodeId,
		Integer estimatePoints, LocalDate targetWeekStartDate, Instant createdAt, Instant updatedAt) {

	/** Backwards-compatible factory — no resolved names. */
	public static TicketSummaryResponse from(WorkItem item) {
		return new TicketSummaryResponse(item.getId(), item.getKey(), item.getTitle(), item.getStatus(),
				item.getPriority(), item.getAssigneeUserId(), null, item.getTeamId(), null, item.getRcdoNodeId(),
				item.getEstimatePoints(), item.getTargetWeekStartDate(), item.getCreatedAt(), item.getUpdatedAt());
	}

	/** Factory with resolved display names. */
	public static TicketSummaryResponse from(WorkItem item, String assigneeDisplayName, String teamName) {
		return new TicketSummaryResponse(item.getId(), item.getKey(), item.getTitle(), item.getStatus(),
				item.getPriority(), item.getAssigneeUserId(), assigneeDisplayName, item.getTeamId(), teamName,
				item.getRcdoNodeId(), item.getEstimatePoints(), item.getTargetWeekStartDate(), item.getCreatedAt(),
				item.getUpdatedAt());
	}
}
