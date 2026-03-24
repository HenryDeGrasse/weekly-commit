package com.weeklycommit.lock.dto;

import com.weeklycommit.plan.dto.PlanWithCommitsResponse;
import java.util.List;

/**
 * Response envelope for POST /api/plans/{id}/lock.
 *
 * <ul>
 * <li>{@code success=true} — plan was locked; {@code plan} contains the locked
 * plan with commits.</li>
 * <li>{@code success=false} — hard validation failed; {@code errors} lists
 * every violation that prevented the lock.</li>
 * </ul>
 */
public record LockResponse(boolean success, PlanWithCommitsResponse plan, List<ValidationError> errors) {

	public static LockResponse success(PlanWithCommitsResponse plan) {
		return new LockResponse(true, plan, List.of());
	}

	public static LockResponse validationFailed(List<ValidationError> errors) {
		return new LockResponse(false, null, errors);
	}
}
