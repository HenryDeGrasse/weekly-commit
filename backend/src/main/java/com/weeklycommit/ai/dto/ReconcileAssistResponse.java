package com.weeklycommit.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Assistance suggestions for the reconciliation phase.
 */
public record ReconcileAssistResponse(
		/** {@code false} when AI is disabled or unavailable. */
		boolean aiAvailable,
		/** Stored suggestion id (null when unavailable). */
		UUID suggestionId,
		/** Likely outcome suggestions per unlinked commit. */
		List<CommitOutcomeSuggestion> likelyOutcomes,
		/** Draft textual summary of the week's changes to seed the reconcile note. */
		String draftSummary,
		/** Recommended carry-forward candidates with rationale. */
		List<CarryForwardRecommendation> carryForwardRecommendations) {

	/** Convenience factory for the unavailable case. */
	public static ReconcileAssistResponse unavailable() {
		return new ReconcileAssistResponse(false, null, List.of(), null, List.of());
	}

	/** Per-commit outcome suggestion. */
	public record CommitOutcomeSuggestion(UUID commitId, String commitTitle, String suggestedOutcome,
			String rationale) {
	}

	/** Carry-forward recommendation for a commit. */
	public record CarryForwardRecommendation(UUID commitId, String commitTitle, String rationale) {
	}
}
