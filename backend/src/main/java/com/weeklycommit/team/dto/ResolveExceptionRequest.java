package com.weeklycommit.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for {@code PUT /api/exceptions/{id}/resolve}. */
public record ResolveExceptionRequest(@NotNull UUID resolverId, @NotBlank String resolution) {
}
