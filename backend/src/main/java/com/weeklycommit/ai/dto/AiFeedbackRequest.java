package com.weeklycommit.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to record user feedback on an AI suggestion.
 */
public record AiFeedbackRequest(
		/** Suggestion being acted on. */
		@NotNull UUID suggestionId,
		/** User submitting feedback. */
		@NotNull UUID userId,
		/**
		 * Feedback action: ACCEPTED, DISMISSED, or EDITED. Stored as accepted=true for
		 * ACCEPTED/EDITED, accepted=false for DISMISSED.
		 */
		@NotNull FeedbackAction action, /** Optional notes (used for EDITED action to capture what was changed). */
		String notes) {

	public enum FeedbackAction {
		ACCEPTED, DISMISSED, EDITED
	}
}
