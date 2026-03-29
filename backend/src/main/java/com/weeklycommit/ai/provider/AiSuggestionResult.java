package com.weeklycommit.ai.provider;

import java.util.Map;

/**
 * Result returned by an {@link AiProvider}.
 *
 * <p>
 * When {@code available} is {@code false}, all other fields are meaningless and
 * callers should propagate {@code aiAvailable: false} to their response without
 * failing the request.
 */
public record AiSuggestionResult(
		/** {@code false} when the provider is down or AI is disabled. */
		boolean available,
		/**
		 * JSON-serialised suggestion payload. Structure varies by
		 * {@link AiContext#suggestionType()}.
		 */
		String payload,
		/** Human-readable rationale explaining why the suggestion was made. */
		String rationale,
		/**
		 * Provider confidence in the primary suggestion [0.0, 1.0]. Used for RCDO
		 * suggestions to filter below the surfacing threshold.
		 */
		double confidence,
		/** Provider model identifier for audit and reproducibility. */
		String modelVersion,
		/**
		 * Prompt template version identifier for A/B testing (e.g.
		 * "commit-draft-assist-v1"). May be null for older results.
		 */
		String promptVersion,
		/**
		 * Experiment variant assignments active for this request, keyed by experiment
		 * name (e.g. {@code "llm-model"} → {@code "treatment"}). Empty when no
		 * experiments were evaluated.
		 */
		Map<String, String> experimentAssignments) {

	/**
	 * Backward-compatible constructor for callers that provide a prompt version but
	 * no experiment assignments.
	 */
	public AiSuggestionResult(boolean available, String payload, String rationale, double confidence,
			String modelVersion, String promptVersion) {
		this(available, payload, rationale, confidence, modelVersion, promptVersion, Map.of());
	}

	/**
	 * Backward-compatible constructor for callers that do not yet provide a prompt
	 * version or experiment assignments.
	 */
	public AiSuggestionResult(boolean available, String payload, String rationale, double confidence,
			String modelVersion) {
		this(available, payload, rationale, confidence, modelVersion, null, Map.of());
	}

	/** Convenience factory for an unavailable (degraded) result. */
	public static AiSuggestionResult unavailable() {
		return new AiSuggestionResult(false, "{}", "AI provider unavailable", 0.0, "none", null, Map.of());
	}
}
