package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTicketFromCommitRequest(@NotNull UUID commitId, @NotNull UUID planId, String title,
		String description, TicketStatus status, TicketPriority priority, UUID assigneeUserId, UUID reporterUserId,
		UUID teamId, Integer estimatePoints, UUID rcdoNodeId, LocalDate targetWeekStartDate) {
}
