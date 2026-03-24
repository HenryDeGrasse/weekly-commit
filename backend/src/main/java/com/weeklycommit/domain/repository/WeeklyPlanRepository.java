package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {

	Optional<WeeklyPlan> findByOwnerUserIdAndWeekStartDate(UUID ownerUserId, LocalDate weekStartDate);

	List<WeeklyPlan> findByTeamIdAndWeekStartDate(UUID teamId, LocalDate weekStartDate);

	List<WeeklyPlan> findByOwnerUserIdOrderByWeekStartDateDesc(UUID ownerUserId);

	List<WeeklyPlan> findByState(PlanState state);

	/** Used by the auto-lock job to find expired DRAFT plans. */
	List<WeeklyPlan> findByStateAndLockDeadlineBefore(PlanState state, Instant deadline);

	boolean existsByOwnerUserIdAndWeekStartDate(UUID ownerUserId, LocalDate weekStartDate);
}
