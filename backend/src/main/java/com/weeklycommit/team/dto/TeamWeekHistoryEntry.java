package com.weeklycommit.team.dto;

import java.time.LocalDate;

public record TeamWeekHistoryEntry(LocalDate weekStartDate, int memberCount, double complianceRate, int plannedPoints,
		int achievedPoints, double carryForwardRate, long exceptionCount) {
}
