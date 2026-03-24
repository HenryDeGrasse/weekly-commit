package com.weeklycommit.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanHistoryServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@InjectMocks
	private PlanHistoryService service;

	@Test
	void getPlanHistory_returnsNewestFirstWithAggregates() {
		UUID userId = UUID.randomUUID();
		WeeklyPlan newer = plan(UUID.randomUUID(), userId, LocalDate.of(2026, 3, 24), PlanState.RECONCILED, true);
		WeeklyPlan older = plan(UUID.randomUUID(), userId, LocalDate.of(2026, 3, 17), PlanState.LOCKED, false);
		when(planRepo.findByOwnerUserIdOrderByWeekStartDateDesc(userId)).thenReturn(List.of(newer, older));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(newer.getId()))
				.thenReturn(List.of(commit(5, CommitOutcome.ACHIEVED, 1), commit(3, CommitOutcome.NOT_ACHIEVED, 0)));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(older.getId())).thenReturn(List.of(commit(2, null, 0)));

		var history = service.getPlanHistory(userId);

		assertThat(history).hasSize(2);
		assertThat(history.get(0).planId()).isEqualTo(newer.getId());
		assertThat(history.get(0).plannedPoints()).isEqualTo(8);
		assertThat(history.get(0).achievedPoints()).isEqualTo(5);
		assertThat(history.get(0).carryForwardCount()).isEqualTo(1);
		assertThat(history.get(1).planId()).isEqualTo(older.getId());
	}

	private WeeklyPlan plan(UUID id, UUID ownerUserId, LocalDate weekStart, PlanState state, boolean compliant) {
		WeeklyPlan plan = new WeeklyPlan();
		plan.setId(id);
		plan.setOwnerUserId(ownerUserId);
		plan.setTeamId(UUID.randomUUID());
		plan.setWeekStartDate(weekStart);
		plan.setState(state);
		plan.setCompliant(compliant);
		plan.setLockDeadline(Instant.now());
		plan.setReconcileDeadline(Instant.now());
		return plan;
	}

	private WeeklyCommit commit(int points, CommitOutcome outcome, int carryForwardStreak) {
		WeeklyCommit commit = new WeeklyCommit();
		commit.setId(UUID.randomUUID());
		commit.setTitle("Commit");
		commit.setPriorityOrder(1);
		commit.setEstimatePoints(points);
		commit.setOutcome(outcome);
		commit.setCarryForwardStreak(carryForwardStreak);
		return commit;
	}
}
