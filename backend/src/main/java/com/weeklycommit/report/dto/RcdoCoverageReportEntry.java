package com.weeklycommit.report.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * One row in the RCDO coverage report: planned vs achieved effort for a
 * specific RCDO node for a single week.
 *
 * <p>
 * {@code teamContributionBreakdown} maps team ID (string) to planned-points
 * contribution.
 */
public record RcdoCoverageReportEntry(UUID rcdoNodeId, LocalDate weekStart, int plannedPoints, int achievedPoints,
		int commitCount, Map<String, Integer> teamContributionBreakdown) {
}
