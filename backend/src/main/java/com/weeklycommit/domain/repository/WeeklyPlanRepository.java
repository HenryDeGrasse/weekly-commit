package com.weeklycommit.domain.repository;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
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

	boolean existsByOwnerUserIdAndWeekStartDate(UUID ownerUserId, LocalDate weekStartDate);
}
