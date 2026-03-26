package com.weeklycommit.ai.provider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Input context supplied to an {@link AiProvider} when requesting a suggestion.
 * All fields are optional except {@code suggestionType}.
 */
public record AiContext(
		/** Discriminator for the kind of suggestion being requested. */
		String suggestionType,
		/** Requesting user (for personalisation and audit). */
		UUID userId,
		/** Plan being analysed, if applicable. */
		UUID planId,
		/** Specific commit being analysed, if applicable. */
		UUID commitId,
		/** Serialised current commit fields (title, chessPiece, etc.). */
		Map<String, Object> commitData,
		/** Serialised plan summary (state, capacity, etc.). */
		Map<String, Object> planData,
		/** Recent historical commits for pattern matching. */
		List<Map<String, Object>> historicalCommits,
		/** Relevant RCDO tree nodes. */
		List<Map<String, Object>> rcdoTree,
		/** Free-form additional context (e.g., scope changes, risk inputs). */
		Map<String, Object> additionalContext) {

	/** Suggestion type constant — commit draft assistance. */
	public static final String TYPE_COMMIT_DRAFT = "COMMIT_DRAFT_ASSIST";

	/** Suggestion type constant — commit quality lint. */
	public static final String TYPE_COMMIT_LINT = "COMMIT_LINT";

	/** Suggestion type constant — RCDO link suggestion. */
	public static final String TYPE_RCDO_SUGGEST = "RCDO_SUGGEST";

	/** Suggestion type constant — risk signal detection. */
	public static final String TYPE_RISK_SIGNAL = "RISK_SIGNAL";

	/** Suggestion type constant — reconciliation assistance. */
	public static final String TYPE_RECONCILE_ASSIST = "RECONCILE_ASSIST";

	/** Suggestion type constant — team manager AI summary. */
	public static final String TYPE_TEAM_SUMMARY = "TEAM_SUMMARY";

	/** Suggestion type constant — RAG intent classification. */
	public static final String TYPE_RAG_INTENT = "RAG_INTENT";

	/** Suggestion type constant — RAG answer generation. */
	public static final String TYPE_RAG_QUERY = "RAG_QUERY";

	/** Suggestion type constant — proactive team-level insight. */
	public static final String TYPE_TEAM_INSIGHT = "TEAM_INSIGHT";

	/** Suggestion type constant — proactive personal/plan-level insight. */
	public static final String TYPE_PERSONAL_INSIGHT = "PERSONAL_INSIGHT";
}
