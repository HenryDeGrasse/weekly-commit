package com.weeklycommit.reconcile.dto;

import com.weeklycommit.domain.enums.CommitOutcome;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/plans/{planId}/commits/{commitId}/outcome}.
 *
 * <p>
 * {@code notes} is required when {@code outcome} is PARTIALLY_ACHIEVED,
 * NOT_ACHIEVED, or CANCELED.
 */
public record SetOutcomeRequest(@NotNull CommitOutcome outcome, String notes) {
}
