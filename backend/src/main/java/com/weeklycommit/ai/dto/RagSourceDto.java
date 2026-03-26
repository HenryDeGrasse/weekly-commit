package com.weeklycommit.ai.dto;

/**
 * A single source entity that contributed to a RAG answer.
 *
 * @param entityType
 *            e.g. {@code "commit"}, {@code "plan_summary"}
 * @param entityId
 *            UUID string of the source entity
 * @param weekStartDate
 *            ISO week-start date (YYYY-MM-DD) if available, otherwise empty
 * @param snippet
 *            brief excerpt from the source chunk text
 */
public record RagSourceDto(String entityType, String entityId, String weekStartDate, String snippet) {
}
