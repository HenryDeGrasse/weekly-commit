package com.weeklycommit.rcdo.dto;

import com.weeklycommit.domain.enums.RcdoNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateRcdoNodeRequest(@NotNull RcdoNodeType nodeType, UUID parentId, @NotBlank String title,
		String description, UUID ownerTeamId, UUID ownerUserId) {
}
