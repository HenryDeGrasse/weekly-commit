package com.weeklycommit.ticket.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial-update request for a ticket. Null fields are ignored.
 */
public record UpdateTicketRequest(String title, String description, UUID assigneeUserId, Integer estimatePoints,
		UUID rcdoNodeId, LocalDate targetWeekStartDate) {
}
