package com.weeklycommit.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/comments}.
 *
 * <p>
 * Exactly one of {@code planId} or {@code commitId} must be provided.
 */
public record AddCommentRequest(@NotNull UUID managerId, UUID planId, UUID commitId, @NotBlank String text) {
}
