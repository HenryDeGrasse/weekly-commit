package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TicketDetailResponse(UUID id, String key, String title, String description, TicketStatus status,
		TicketPriority priority, UUID assigneeUserId, UUID reporterUserId, UUID teamId, UUID rcdoNodeId,
		Integer estimatePoints, LocalDate targetWeekStartDate, List<TicketStatusHistoryResponse> statusHistory,
		List<LinkedCommitEntry> linkedCommits, Instant createdAt, Instant updatedAt) {

	public static TicketDetailResponse from(TicketResponse ticket, List<TicketStatusHistoryResponse> statusHistory,
			List<LinkedCommitEntry> linkedCommits) {
		return new TicketDetailResponse(ticket.id(), ticket.key(), ticket.title(), ticket.description(),
				ticket.status(), ticket.priority(), ticket.assigneeUserId(), ticket.reporterUserId(), ticket.teamId(),
				ticket.rcdoNodeId(), ticket.estimatePoints(), ticket.targetWeekStartDate(), statusHistory,
				linkedCommits, ticket.createdAt(), ticket.updatedAt());
	}
}
