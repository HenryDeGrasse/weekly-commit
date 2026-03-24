package com.weeklycommit.report.dto;

/**
 * Aggregate AI suggestion acceptance statistics across all time.
 */
public record AiAcceptanceReportEntry(long totalSuggestions, long totalFeedbackGiven, long acceptedCount,
		long dismissedCount, double acceptanceRate) {
}
