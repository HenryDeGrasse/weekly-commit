package com.weeklycommit.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request to simulate hypothetical plan mutations without persisting any
 * changes.
 */
public record WhatIfRequest(
		/** Plan to simulate against. */
		@NotNull UUID planId,
		/** Requesting user. */
		@NotNull UUID userId,
		/** Hypothetical commit mutations to apply in-memory. */
		@Valid List<WhatIfMutation> hypotheticalChanges) {

	/**
	 * A single hypothetical mutation to apply to the plan's commit list.
	 */
	public record WhatIfMutation(
			/** Action to perform. */
			@NotNull WhatIfAction action,
			/** Commit id to remove or modify (null for ADD_COMMIT). */
			UUID commitId,
			/** Draft title (used for ADD_COMMIT or MODIFY_COMMIT). */
			String title,
			/**
			 * Chess piece name as a plain string (e.g. {@code "KING"}) — intentionally not
			 * the domain {@code ChessPiece} enum, to avoid coupling.
			 */
			String chessPiece,
			/** Estimated story points (used for ADD_COMMIT or MODIFY_COMMIT). */
			Integer estimatePoints,
			/** RCDO node id (used for ADD_COMMIT or MODIFY_COMMIT). */
			UUID rcdoNodeId) {

		/** Action enum for a what-if mutation. */
		public enum WhatIfAction {
			ADD_COMMIT, REMOVE_COMMIT, MODIFY_COMMIT
		}
	}
}
