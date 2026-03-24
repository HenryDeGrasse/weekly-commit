package com.weeklycommit.team.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** Request body for {@code PUT /api/capacity-overrides}. */
public record SetCapacityOverrideRequest(@NotNull UUID managerId, @NotNull UUID userId,
		@NotNull LocalDate weekStartDate, @Positive int overridePoints, String reason) {
}
