package com.weeklycommit.ai.dto;

import java.util.UUID;

/**
 * A single lint message returned by the commit quality lint endpoint.
 *
 * <p>
 * Hard validation messages must be resolved before a plan can be locked. Soft
 * guidance messages are informational only.
 */
public record LintMessage(
		/** Machine-readable lint rule code. */
		String code,
		/** Human-readable explanation. */
		String message,
		/** The commit this message relates to, if applicable. */
		UUID commitId) {
}
