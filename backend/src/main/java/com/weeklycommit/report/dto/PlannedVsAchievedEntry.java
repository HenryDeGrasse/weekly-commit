package com.weeklycommit.report.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row in the planned-vs-achieved report: team-level points comparison for a
 * single week.
 */
public record PlannedVsAchievedEntry(UUID teamId, LocalDate weekStart, int totalPlannedPoints, int totalAchievedPoints,
		int memberCount, int reconciledCount) {
}
