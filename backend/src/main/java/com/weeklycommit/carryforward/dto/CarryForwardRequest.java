package com.weeklycommit.carryforward.dto;

import com.weeklycommit.domain.enums.CarryForwardReason;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to carry a commit forward into a target week.
 *
 * @param targetWeekStart
 *            Monday of the target week (ISO date)
 * @param reason
 *            controlled enum reason for carrying forward
 * @param reasonText
 *            optional free-text elaboration
 * @param actorUserId
 *            user initiating the carry-forward (may be null if supplied via
 *            header)
 */
public record CarryForwardRequest(@NotNull LocalDate targetWeekStart, @NotNull CarryForwardReason reason,
		String reasonText, UUID actorUserId) {
}
