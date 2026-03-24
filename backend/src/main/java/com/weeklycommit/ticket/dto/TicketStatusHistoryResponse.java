package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of a single ticket status-history entry.
 */
public record TicketStatusHistoryResponse(UUID id, UUID workItemId, String fromStatus, String toStatus,
		UUID changedByUserId, Instant createdAt) {

	public static TicketStatusHistoryResponse from(WorkItemStatusHistory h) {
		return new TicketStatusHistoryResponse(h.getId(), h.getWorkItemId(), h.getFromStatus(), h.getToStatus(),
				h.getChangedByUserId(), h.getCreatedAt());
	}
}
