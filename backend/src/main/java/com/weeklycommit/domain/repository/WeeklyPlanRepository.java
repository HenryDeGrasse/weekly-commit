package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {

	Optional<WeeklyPlan> findByOwnerUserIdAndWeekStartDate(UUID ownerUserId, LocalDate weekStartDate);

	List<WeeklyPlan> findByTeamIdAndWeekStartDate(UUID teamId, LocalDate weekStartDate);

	List<WeeklyPlan> findByTeamIdOrderByWeekStartDateDesc(UUID teamId);

	List<WeeklyPlan> findByOwnerUserIdOrderByWeekStartDateDesc(UUID ownerUserId);

	List<WeeklyPlan> findByState(PlanState state);

	/** Used by the auto-lock job to find expired DRAFT plans. */
	List<WeeklyPlan> findByStateAndLockDeadlineBefore(PlanState state, Instant deadline);

	/**
	 * Used by the auto-reconcile job to find LOCKED plans past their reconcile-open
	 * time.
	 */
	List<WeeklyPlan> findByStateAndReconcileDeadlineBefore(PlanState state, Instant deadline);

	boolean existsByOwnerUserIdAndWeekStartDate(UUID ownerUserId, LocalDate weekStartDate);

	/**
	 * Used by the scheduled read-model refresh to find all plans for the current
	 * week.
	 */
	List<WeeklyPlan> findByWeekStartDate(LocalDate weekStartDate);

	/** Used by the daily RAG sweep to find recently updated plans. */
	@Query("SELECT p FROM WeeklyPlan p WHERE p.updatedAt > :since")
	List<WeeklyPlan> findUpdatedSince(@Param("since") Instant since);
}
