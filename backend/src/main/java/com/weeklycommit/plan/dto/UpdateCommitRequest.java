package com.weeklycommit.plan.dto;

import com.weeklycommit.domain.enums.ChessPiece;
import java.util.UUID;

/**
 * All fields are optional; {@code null} means "do not change".
 *
 * <p>
 * To clear an optional field (e.g. remove the RCDO link) a dedicated clear
 * endpoint can be added in a later step. For now null == no-op.
 */
public record UpdateCommitRequest(String title, ChessPiece chessPiece, String description, UUID rcdoNodeId,
		UUID workItemId, Integer estimatePoints, String successCriteria) {
}
