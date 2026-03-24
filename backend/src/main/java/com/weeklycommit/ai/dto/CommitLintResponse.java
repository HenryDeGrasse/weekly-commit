package com.weeklycommit.ai.dto;

import java.util.List;

/**
 * Result of commit quality lint for a plan.
 *
 * <p>
 * {@code hardValidation} messages block lock. {@code softGuidance} messages are
 * informational only and never prevent locking.
 */
public record CommitLintResponse(
		/** {@code false} when AI is disabled or unavailable. */
		boolean aiAvailable,
		/** Messages that must be resolved before locking. */
		List<LintMessage> hardValidation,
		/** Informational messages that do not block locking. */
		List<LintMessage> softGuidance) {

	/** Convenience factory for the unavailable case. */
	public static CommitLintResponse unavailable() {
		return new CommitLintResponse(false, List.of(), List.of());
	}
}
