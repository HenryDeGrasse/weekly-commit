package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Flat API view of a work item (ticket).
 */
public record TicketResponse(UUID id, UUID teamId, String key, String title, String description, TicketStatus status,
		TicketPriority priority, UUID assigneeUserId, UUID reporterUserId, Integer estimatePoints, UUID rcdoNodeId,
		LocalDate targetWeekStartDate, Instant createdAt, Instant updatedAt) {

	public static TicketResponse from(WorkItem item) {
		return new TicketResponse(item.getId(), item.getTeamId(), item.getKey(), item.getTitle(), item.getDescription(),
				item.getStatus(), item.getPriority(), item.getAssigneeUserId(), item.getReporterUserId(),
				item.getEstimatePoints(), item.getRcdoNodeId(), item.getTargetWeekStartDate(), item.getCreatedAt(),
				item.getUpdatedAt());
	}
}
