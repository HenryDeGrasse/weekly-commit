package com.weeklycommit.ai.provider;

/**
 * Strategy interface for AI suggestion providers.
 *
 * <p>
 * Implementations must be idempotent and must never throw unchecked exceptions
 * that would bubble up to the caller — all errors must be surfaced via
 * {@link AiSuggestionResult#unavailable()}.
 */
public interface AiProvider {

	/** Unique, human-readable name for this provider (e.g., "stub", "openai"). */
	String getName();

	/** Model/version identifier used for audit and reproducibility. */
	String getVersion();

	/**
	 * Returns {@code true} when the provider is reachable and ready.
	 *
	 * <p>
	 * This is a fast health check; implementations should cache results and avoid
	 * expensive round-trips on every call.
	 */
	boolean isAvailable();

	/**
	 * Generates a suggestion for the given context.
	 *
	 * <p>
	 * Must never throw. Returns {@link AiSuggestionResult#unavailable()} if the
	 * provider is down or an internal error occurs.
	 *
	 * @param context
	 *            the input context
	 * @return the suggestion result, never {@code null}
	 */
	AiSuggestionResult generateSuggestion(AiContext context);
}
