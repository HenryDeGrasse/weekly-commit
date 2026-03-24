package com.weeklycommit.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Risk signal detected for a plan (stored in {@code ai_suggestion} with type
 * {@code RISK_SIGNAL}).
 *
 * <p>
 * Risk signals are hidden from peers; only the plan owner and their manager may
 * view them.
 */
public record RiskSignalResponse(
		/** Suggestion id (for feedback/dismiss). */
		UUID id,
		/** Signal type code (e.g., OVERCOMMIT, UNDERCOMMIT, REPEATED_CARRY_FORWARD). */
		String signalType,
		/** Human-readable rationale explaining why this signal was raised. */
		String rationale,
		/** Plan the signal relates to. */
		UUID planId,
		/** Specific commit the signal relates to (null for plan-level signals). */
		UUID commitId,
		/** When the signal was generated. */
		Instant createdAt) {

	/** Wraps a list of risk signals with the plan-level available flag. */
	public record PlanRiskSignals(boolean aiAvailable, UUID planId, List<RiskSignalResponse> signals) {

		public static PlanRiskSignals unavailable(UUID planId) {
			return new PlanRiskSignals(false, planId, List.of());
		}
	}
}
