package com.weeklycommit.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to generate reconciliation assistance for a plan entering the
 * reconcile phase.
 */
public record ReconcileAssistRequest(
		/** Plan being reconciled. */
		@NotNull UUID planId,
		/** Requesting user. */
		@NotNull UUID userId) {
}
