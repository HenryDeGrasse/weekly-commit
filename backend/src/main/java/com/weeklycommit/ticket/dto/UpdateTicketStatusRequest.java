package com.weeklycommit.ticket.dto;

import com.weeklycommit.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to transition a ticket's status.
 *
 * @param status
 *            target status (must be a legal transition from the current status)
 * @param changedByUserId
 *            user performing the transition
 */
public record UpdateTicketStatusRequest(@NotNull TicketStatus status, @NotNull UUID changedByUserId) {
}
