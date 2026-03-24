package com.weeklycommit.rcdo.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MoveRcdoNodeRequest(@NotNull UUID newParentId) {
}
