package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.LocalDate;
import java.util.UUID;

public record TicketListParams(TicketStatus status, UUID assigneeUserId, UUID teamId, UUID rcdoNodeId,
		LocalDate targetWeek, TicketPriority priority, int page, int pageSize, String sortBy, String sortDir) {

	public int normalizedPage() {
		return Math.max(1, page);
	}

	public int normalizedPageSize() {
		return Math.max(1, pageSize);
	}

	public String normalizedSortBy() {
		return sortBy == null || sortBy.isBlank() ? "updatedAt" : sortBy;
	}

	public String normalizedSortDir() {
		return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
	}
}
