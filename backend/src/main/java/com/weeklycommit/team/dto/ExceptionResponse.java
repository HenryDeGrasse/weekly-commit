package com.weeklycommit.team.dto;

import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a persisted {@link ManagerReviewException}. */
public record ExceptionResponse(UUID id, UUID teamId, UUID planId, UUID userId,
		/** Human-readable display name of the user associated with this exception. */
		String displayName,
		ExceptionType exceptionType,
		ExceptionSeverity severity, String description, LocalDate weekStartDate, boolean resolved, String resolution,
		Instant resolvedAt, UUID resolvedById, Instant createdAt) {

	/** Convenience factory without a display name (displayName will be null). */
	public static ExceptionResponse from(ManagerReviewException e) {
		return from(e, null);
	}

	/** Factory that includes the resolved display name for the user. */
	public static ExceptionResponse from(ManagerReviewException e, String displayName) {
		return new ExceptionResponse(e.getId(), e.getTeamId(), e.getPlanId(), e.getUserId(), displayName,
				e.getExceptionType(), e.getSeverity(), e.getDescription(), e.getWeekStartDate(), e.isResolved(),
				e.getResolution(), e.getResolvedAt(), e.getResolvedById(), e.getCreatedAt());
	}
}
