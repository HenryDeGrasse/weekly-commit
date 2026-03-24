package com.weeklycommit.report.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row in the carry-forward report: per-user per-week carry-forward counts
 * and rate.
 */
public record CarryForwardReportEntry(UUID userId, LocalDate weekStart, int commitCount, int carryForwardCount,
		double carryForwardRate) {
}
