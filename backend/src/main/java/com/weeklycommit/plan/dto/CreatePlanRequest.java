package com.weeklycommit.plan.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for POST /api/plans. If {@code weekStartDate} is omitted the
 * service defaults to the current week's Monday.
 */
public record CreatePlanRequest(@NotNull UUID userId, LocalDate weekStartDate) {
}
