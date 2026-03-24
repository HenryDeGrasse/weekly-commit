package com.weeklycommit.report.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Chess-piece distribution for a team for a specific week.
 *
 * <p>
 * {@code distribution} maps chess-piece name (e.g. "KING") to commit count.
 */
public record ChessDistributionReportEntry(UUID teamId, LocalDate weekStart, Map<String, Integer> distribution) {
}
