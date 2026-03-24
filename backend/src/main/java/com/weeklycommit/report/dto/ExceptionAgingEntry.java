package com.weeklycommit.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row in the exception-aging report: an unresolved manager exception with
 * its age.
 */
public record ExceptionAgingEntry(UUID exceptionId, UUID teamId, UUID userId, String exceptionType, String severity,
		LocalDate weekStartDate, Instant createdAt, long ageInHours) {
}
