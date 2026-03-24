package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.enums.ExceptionType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManagerReviewExceptionRepository extends JpaRepository<ManagerReviewException, UUID> {

	List<ManagerReviewException> findByTeamIdAndWeekStartDateAndResolved(UUID teamId, LocalDate weekStartDate,
			boolean resolved);

	List<ManagerReviewException> findByTeamIdAndWeekStartDate(UUID teamId, LocalDate weekStartDate);

	long countByTeamIdAndWeekStartDate(UUID teamId, LocalDate weekStartDate);

	/**
	 * Used for idempotent exception detection: find an existing unresolved
	 * exception for a specific plan + type combination.
	 */
	Optional<ManagerReviewException> findByPlanIdAndExceptionTypeAndResolved(UUID planId, ExceptionType exceptionType,
			boolean resolved);

	/**
	 * Used for plan-level exceptions not tied to a specific plan entity (e.g., team
	 * member has no plan at all). Look up by userId + week + type.
	 */
	Optional<ManagerReviewException> findByUserIdAndWeekStartDateAndExceptionTypeAndResolved(UUID userId,
			LocalDate weekStartDate, ExceptionType exceptionType, boolean resolved);
}
