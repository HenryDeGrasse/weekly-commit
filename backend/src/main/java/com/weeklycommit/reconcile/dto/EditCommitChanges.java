package com.weeklycommit.reconcile.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import java.util.UUID;

/**
 * Subset of commit fields that are mutable via a post-lock scope-change EDIT.
 * Only non-null values are applied; all fields are optional.
 */
public record EditCommitChanges(Integer estimatePoints, ChessPiece chessPiece, UUID rcdoNodeId, Integer priorityOrder) {
}
