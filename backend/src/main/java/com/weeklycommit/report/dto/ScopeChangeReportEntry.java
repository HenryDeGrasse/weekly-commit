package com.weeklycommit.report.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row in the scope-change volume report: per-user per-week scope change
 * count.
 */
public record ScopeChangeReportEntry(UUID userId, LocalDate weekStart, int scopeChangeCount) {
}
