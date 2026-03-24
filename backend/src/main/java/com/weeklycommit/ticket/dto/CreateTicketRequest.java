package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create a new native ticket (work item).
 */
public record CreateTicketRequest(@NotNull UUID teamId, @NotBlank String title, String description, TicketStatus status,
		@NotNull TicketPriority priority, UUID assigneeUserId, @NotNull UUID reporterUserId, Integer estimatePoints,
		UUID rcdoNodeId, LocalDate targetWeekStartDate) {
}
