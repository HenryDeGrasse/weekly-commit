package com.weeklycommit.reconcile.dto;

import java.util.List;

/**
 * Chronological list of scope-change events plus any manager-exception flags.
 */
public record ScopeChangeTimelineResponse(List<ScopeChangeEventResponse> events,
		List<ManagerException> managerExceptions) {
}
