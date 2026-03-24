package com.weeklycommit.ai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * AI-generated team summary for a manager's weekly view.
 *
 * <p>
 * All cited items reference actual database objects so managers can drill down.
 * Only visible to team managers (not peers).
 */
public record ManagerAiSummaryResponse(
		/** {@code false} when AI is disabled or unavailable. */
		boolean aiAvailable,
		/** Stored suggestion id (null when unavailable). */
		UUID suggestionId,
		/** Team id this summary covers. */
		UUID teamId,
		/** Week start date this summary covers. */
		LocalDate weekStart,
		/** Prose summary of the team week. */
		String summaryText,
		/** RCDO branch titles with highest planned commitment this week. */
		List<String> topRcdoBranches,
		/** Unresolved exception ids (cited objects). */
		List<UUID> unresolvedExceptionIds,
		/** Textual patterns about carry-forwards (cited from plan data). */
		List<String> carryForwardPatterns,
		/** Work item ids for King/Queen commits currently BLOCKED (cited objects). */
		List<UUID> criticalBlockedItemIds,
		/** Model version for audit. */
		String modelVersion) {

	/** Convenience factory for the unavailable case. */
	public static ManagerAiSummaryResponse unavailable(UUID teamId, LocalDate weekStart) {
		return new ManagerAiSummaryResponse(false, null, teamId, weekStart, null, List.of(), List.of(), List.of(),
				List.of(), null);
	}
}
