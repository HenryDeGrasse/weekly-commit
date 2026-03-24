package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial-update request for a ticket. Null fields are ignored.
 */
public record UpdateTicketRequest(String title, String description, TicketStatus status, TicketPriority priority,
		UUID assigneeUserId, UUID teamId, Integer estimatePoints, UUID rcdoNodeId, LocalDate targetWeekStartDate) {
}
