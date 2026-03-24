package com.weeklycommit.report.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row in the compliance report: lock and reconcile compliance for a single
 * user for a single week.
 */
public record ComplianceReportEntry(UUID userId, LocalDate weekStart, boolean lockOnTime, boolean lockLate,
		boolean autoLocked, boolean reconcileOnTime, boolean reconcileLate, boolean reconcileMissed) {
}
