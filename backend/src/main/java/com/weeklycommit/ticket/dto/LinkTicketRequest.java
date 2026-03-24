package com.weeklycommit.ticket.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to link a ticket to a commit.
 *
 * @param workItemId
 *            ticket to link (required; pass null to unlink)
 */
public record LinkTicketRequest(@NotNull UUID workItemId) {
}
