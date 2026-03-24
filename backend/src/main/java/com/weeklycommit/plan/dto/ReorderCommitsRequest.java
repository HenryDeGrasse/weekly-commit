package com.weeklycommit.plan.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReorderCommitsRequest(@NotNull @NotEmpty List<UUID> commitIds) {
}
