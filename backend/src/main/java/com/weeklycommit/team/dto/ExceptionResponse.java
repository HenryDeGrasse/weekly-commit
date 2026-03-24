package com.weeklycommit.team.dto;

import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a persisted {@link ManagerReviewException}. */
public record ExceptionResponse(UUID id, UUID teamId, UUID planId, UUID userId, ExceptionType exceptionType,
		ExceptionSeverity severity, String description, LocalDate weekStartDate, boolean resolved, String resolution,
		Instant resolvedAt, UUID resolvedById, Instant createdAt) {

	public static ExceptionResponse from(ManagerReviewException e) {
		return new ExceptionResponse(e.getId(), e.getTeamId(), e.getPlanId(), e.getUserId(), e.getExceptionType(),
				e.getSeverity(), e.getDescription(), e.getWeekStartDate(), e.isResolved(), e.getResolution(),
				e.getResolvedAt(), e.getResolvedById(), e.getCreatedAt());
	}
}
