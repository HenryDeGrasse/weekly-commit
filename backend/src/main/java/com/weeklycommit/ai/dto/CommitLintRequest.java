package com.weeklycommit.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to run commit quality lint for a plan.
 */
public record CommitLintRequest(
		/** Plan whose commits should be linted. */
		@NotNull UUID planId,
		/** Requesting user (for personalisation). */
		@NotNull UUID userId) {
}
