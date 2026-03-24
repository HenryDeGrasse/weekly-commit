package com.weeklycommit.config.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Resolved weekly capacity (budget points) for a user for a specific week.
 * Indicates which layer of the resolution chain provided the value:
 * {@code PER_WEEK_OVERRIDE} > {@code TEAM_DEFAULT} > {@code ORG_DEFAULT} >
 * {@code SYSTEM_DEFAULT}.
 */
public record EffectiveCapacityResponse(UUID userId, LocalDate weekStart, int budgetPoints, String source) {
}
