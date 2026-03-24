package com.weeklycommit.reconcile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Single request envelope for all post-lock scope-change operations: ADD,
 * REMOVE, and EDIT.
 *
 * <ul>
 * <li>ADD — {@code commitData} is required; {@code commitId} is ignored.</li>
 * <li>REMOVE — {@code commitId} is required.</li>
 * <li>EDIT — {@code commitId} and {@code changes} are required.</li>
 * </ul>
 */
public record ScopeChangeRequest(@NotNull ScopeChangeAction action, @NotBlank String reason, UUID commitId,
		@Valid AddCommitData commitData, @Valid EditCommitChanges changes) {
}
