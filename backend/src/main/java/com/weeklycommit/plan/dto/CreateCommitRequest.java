package com.weeklycommit.plan.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCommitRequest(@NotBlank String title, @NotNull ChessPiece chessPiece, String description,
		UUID rcdoNodeId, UUID workItemId, Integer estimatePoints, String successCriteria) {
}
