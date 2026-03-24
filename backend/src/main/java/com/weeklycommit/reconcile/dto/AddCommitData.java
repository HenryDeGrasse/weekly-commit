package com.weeklycommit.reconcile.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Fields needed when adding a new commit to a locked plan via a scope-change
 * event.
 */
public record AddCommitData(@NotBlank String title, @NotNull ChessPiece chessPiece, String description, UUID rcdoNodeId,
		UUID workItemId, Integer estimatePoints, String successCriteria) {
}
