package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detailed ticket view including status history and linked commits. Includes
 * resolved display names so the frontend never shows raw UUIDs.
 */
public record TicketDetailResponse(UUID id, String key, String title, String description, TicketStatus status,
		TicketPriority priority, UUID assigneeUserId, String assigneeDisplayName, UUID reporterUserId,
		String reporterDisplayName, UUID teamId, String teamName, UUID rcdoNodeId, Integer estimatePoints,
		LocalDate targetWeekStartDate, List<TicketStatusHistoryResponse> statusHistory,
		List<LinkedCommitEntry> linkedCommits, Instant createdAt, Instant updatedAt) {

	/** Factory without resolved names (backwards compat). */
	public static TicketDetailResponse from(TicketResponse ticket, List<TicketStatusHistoryResponse> statusHistory,
			List<LinkedCommitEntry> linkedCommits) {
		return new TicketDetailResponse(ticket.id(), ticket.key(), ticket.title(), ticket.description(),
				ticket.status(), ticket.priority(), ticket.assigneeUserId(), null, ticket.reporterUserId(), null,
				ticket.teamId(), null, ticket.rcdoNodeId(), ticket.estimatePoints(), ticket.targetWeekStartDate(),
				statusHistory, linkedCommits, ticket.createdAt(), ticket.updatedAt());
	}

	/** Factory with resolved names. */
	public static TicketDetailResponse from(TicketResponse ticket, List<TicketStatusHistoryResponse> statusHistory,
			List<LinkedCommitEntry> linkedCommits, String assigneeDisplayName, String reporterDisplayName,
			String teamName) {
		return new TicketDetailResponse(ticket.id(), ticket.key(), ticket.title(), ticket.description(),
				ticket.status(), ticket.priority(), ticket.assigneeUserId(), assigneeDisplayName,
				ticket.reporterUserId(), reporterDisplayName, ticket.teamId(), teamName, ticket.rcdoNodeId(),
				ticket.estimatePoints(), ticket.targetWeekStartDate(), statusHistory, linkedCommits, ticket.createdAt(),
				ticket.updatedAt());
	}
}
