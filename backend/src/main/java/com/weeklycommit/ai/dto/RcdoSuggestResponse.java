package com.weeklycommit.ai.dto;

import java.util.UUID;

/**
 * Suggested RCDO node for a commit.
 *
 * <p>
 * Only surfaced when {@code confidence >= 0.7}. Never auto-linked.
 */
public record RcdoSuggestResponse(
		/** {@code false} when AI is disabled or unavailable. */
		boolean aiAvailable,
		/**
		 * {@code true} when a suggestion is available (confidence above threshold).
		 * {@code false} when AI is available but no high-confidence match was found.
		 */
		boolean suggestionAvailable,
		/** Stored suggestion id (null when unavailable or below threshold). */
		UUID suggestionId,
		/** Suggested RCDO node id. */
		UUID suggestedRcdoNodeId,
		/** Display title of the suggested node (for UI rendering). */
		String rcdoTitle,
		/** Confidence score [0.0, 1.0] — always >= 0.7 when surfaced. */
		double confidence,
		/** Rationale for the suggestion. */
		String rationale) {

	/** Convenience factory for the unavailable case. */
	public static RcdoSuggestResponse unavailable() {
		return new RcdoSuggestResponse(false, false, null, null, null, 0.0, null);
	}

	/**
	 * Convenience factory when AI is available but confidence is below threshold.
	 */
	public static RcdoSuggestResponse belowThreshold() {
		return new RcdoSuggestResponse(true, false, null, null, null, 0.0, "No high-confidence RCDO match found.");
	}
}
