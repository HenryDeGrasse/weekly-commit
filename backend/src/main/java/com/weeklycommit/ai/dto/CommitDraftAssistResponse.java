package com.weeklycommit.ai.dto;

import java.util.UUID;

/**
 * Suggestion returned for commit drafting assistance.
 *
 * <p>
 * All suggestion fields are editable proposals — they are never auto-applied.
 * When {@code aiAvailable} is {@code false}, all suggestion fields are null.
 */
public record CommitDraftAssistResponse(
		/** {@code false} when AI is disabled or unavailable. */
		boolean aiAvailable,
		/** Stored suggestion id (null when unavailable). */
		UUID suggestionId,
		/** Proposed improved title. */
		String suggestedTitle,
		/** Proposed improved description. */
		String suggestedDescription,
		/** Proposed success criteria (especially for King/Queen). */
		String suggestedSuccessCriteria,
		/** Proposed estimate in story points. */
		Integer suggestedEstimatePoints,
		/** Explanation of why these suggestions were generated. */
		String rationale) {

	/** Convenience factory for the unavailable case. */
	public static CommitDraftAssistResponse unavailable() {
		return new CommitDraftAssistResponse(false, null, null, null, null, null, null);
	}
}
