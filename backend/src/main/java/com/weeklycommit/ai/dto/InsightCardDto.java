package com.weeklycommit.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * A single AI-generated insight card, surfaced from a stored
 * {@link com.weeklycommit.domain.entity.AiSuggestion} row.
 *
 * @param suggestionId
 *            the persisted suggestion ID (for feedback / dismiss)
 * @param insightText
 *            the human-readable insight statement
 * @param severity
 *            {@code HIGH}, {@code MEDIUM}, or {@code LOW}
 * @param sourceEntityIds
 *            UUIDs of plans / commits / events that informed this insight
 * @param actionSuggestion
 *            specific, actionable recommendation
 * @param createdAt
 *            ISO-8601 timestamp when the insight was generated
 */
public record InsightCardDto(UUID suggestionId, String insightText, String severity, List<String> sourceEntityIds,
		String actionSuggestion, String createdAt) {
}
