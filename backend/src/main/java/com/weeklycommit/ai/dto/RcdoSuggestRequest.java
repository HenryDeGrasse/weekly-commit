package com.weeklycommit.ai.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to suggest a primary RCDO node for a commit being authored.
 */
public record RcdoSuggestRequest(
		/** Plan context. */
		@NotNull UUID planId,
		/** Requesting user. */
		@NotNull UUID userId,
		/** Commit's current title (required for matching). */
		@NotBlank String title,
		/** Optional description for better matching. */
		String description,
		/** Chess piece classification, if known. */
		ChessPiece chessPiece) {
}
