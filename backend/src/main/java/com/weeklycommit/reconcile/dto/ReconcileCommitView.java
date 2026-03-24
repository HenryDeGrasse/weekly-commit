package com.weeklycommit.reconcile.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-commit view within a {@link ReconciliationViewResponse}. Pairs the
 * immutable baseline snapshot data against the live commit state.
 */
public record ReconcileCommitView(UUID commitId, String currentTitle, ChessPiece currentChessPiece,
		Integer currentEstimatePoints, CommitOutcome currentOutcome, String currentOutcomeNotes,
		/**
		 * Parsed baseline snapshot fields (null if this commit was added post-lock).
		 */
		Map<String, Object> baselineSnapshot,
		/** All scope-change events related to this commit, in creation order. */
		List<ScopeChangeEventResponse> scopeChanges,
		/** Status name of the linked work item, or null if none. */
		String linkedTicketStatus,
		/** True when this commit was added via a post-lock COMMIT_ADDED event. */
		boolean addedPostLock,
		/** True when this commit was canceled via a post-lock COMMIT_REMOVED event. */
		boolean removedPostLock) {
}
