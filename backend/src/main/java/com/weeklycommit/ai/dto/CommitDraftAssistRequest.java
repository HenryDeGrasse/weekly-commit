package com.weeklycommit.ai.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to generate commit drafting suggestions for a commit being authored.
 */
public record CommitDraftAssistRequest(
		/** Plan the commit belongs to. */
		@NotNull UUID planId,
		/** Requesting user. */
		@NotNull UUID userId,
		/**
		 * Existing commit id, if editing an existing commit. May be null for new
		 * commits.
		 */
		UUID commitId,
		/** Current draft title. */
		String currentTitle,
		/** Current draft description. */
		String currentDescription,
		/** Current draft success criteria. */
		String currentSuccessCriteria,
		/** Current estimated points. */
		Integer currentEstimatePoints,
		/** Chess piece classification. */
		ChessPiece chessPiece) {
}
