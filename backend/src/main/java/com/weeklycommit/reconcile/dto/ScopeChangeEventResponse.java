package com.weeklycommit.reconcile.dto;

import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import java.time.Instant;
import java.util.UUID;

/** Read model for a single scope-change audit event. */
public record ScopeChangeEventResponse(UUID id, UUID planId, UUID commitId, ScopeChangeCategory category,
		UUID changedByUserId, String reason, String previousValue, String newValue, Instant createdAt) {

	public static ScopeChangeEventResponse from(ScopeChangeEvent e) {
		return new ScopeChangeEventResponse(e.getId(), e.getPlanId(), e.getCommitId(), e.getCategory(),
				e.getChangedByUserId(), e.getReason(), e.getPreviousValue(), e.getNewValue(), e.getCreatedAt());
	}
}
