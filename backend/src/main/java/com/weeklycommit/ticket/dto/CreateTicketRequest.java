package com.weeklycommit.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create a new native ticket (work item).
 *
 * @param teamId
 *            team that owns the ticket
 * @param title
 *            short title (required)
 * @param description
 *            optional detailed description
 * @param assigneeUserId
 *            optional initial assignee
 * @param reporterUserId
 *            reporter (required)
 * @param estimatePoints
 *            optional Fibonacci-scale estimate (1,2,3,5,8)
 * @param rcdoNodeId
 *            optional RCDO strategy linkage
 * @param targetWeekStartDate
 *            optional target week (Monday)
 */
public record CreateTicketRequest(@NotNull UUID teamId, @NotBlank String title, String description, UUID assigneeUserId,
		@NotNull UUID reporterUserId, Integer estimatePoints, UUID rcdoNodeId, LocalDate targetWeekStartDate) {
}
