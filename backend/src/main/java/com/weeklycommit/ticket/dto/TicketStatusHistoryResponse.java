package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of a single ticket status-history entry.
 */
public record TicketStatusHistoryResponse(UUID id, UUID ticketId, TicketStatus fromStatus, TicketStatus toStatus,
		UUID changedByUserId, Instant changedAt, String note) {

	public static TicketStatusHistoryResponse from(WorkItemStatusHistory h) {
		return new TicketStatusHistoryResponse(h.getId(), h.getWorkItemId(), mapStatus(h.getFromStatus()),
				mapStatus(h.getToStatus()), h.getChangedByUserId(), h.getCreatedAt(), null);
	}

	private static TicketStatus mapStatus(String status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case "BACKLOG", "READY" -> TicketStatus.TODO;
			default -> TicketStatus.valueOf(status);
		};
	}
}
