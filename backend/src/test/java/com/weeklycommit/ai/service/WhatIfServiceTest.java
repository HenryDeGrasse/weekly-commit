package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.weeklycommit.ai.dto.WhatIfRequest;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation.WhatIfAction;
import com.weeklycommit.ai.dto.WhatIfResponse;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatIfServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private ScopeChangeEventRepository scopeChangeRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private WorkItemStatusHistoryRepository statusHistoryRepo;

	private WhatIfService service;

	private UUID planId;
	private UUID userId;
	private WeeklyPlan plan;

	@BeforeEach
	void setUp() {
		service = new WhatIfService(planRepo, commitRepo, scopeChangeRepo, workItemRepo, statusHistoryRepo);
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();

		plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(userId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 23));
		plan.setCapacityBudgetPoints(10);
		plan.setState(PlanState.DRAFT);
	}

	// -------------------------------------------------------------------------
	// (a) ADD_COMMIT pushes over budget → OVERCOMMIT in projectedState
	// -------------------------------------------------------------------------

	@Test
	void simulate_addCommitPushesOverBudget_overcommitInProjectedState() {
		// Budget=10, existing=8 pts (no OVERCOMMIT), add 5 pts → 13 pts > 10 →
		// OVERCOMMIT
		WeeklyCommit existing = buildCommit(ChessPiece.ROOK, 8, 0, null);

		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(existing));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());

		WhatIfMutation add = new WhatIfMutation(WhatIfAction.ADD_COMMIT, null, "New Task", "ROOK", 5, null);
		WhatIfRequest request = new WhatIfRequest(planId, userId, List.of(add));

		WhatIfResponse response = service.simulate(request);

		assertThat(response.available()).isTrue();
		assertThat(response.currentState().riskSignals()).doesNotContain("OVERCOMMIT");
		assertThat(response.projectedState().riskSignals()).contains("OVERCOMMIT");
		assertThat(response.riskDelta().newRisks()).contains("OVERCOMMIT");
		assertThat(response.capacityDelta()).isEqualTo(5);
	}

	// -------------------------------------------------------------------------
	// (b) REMOVE_COMMIT resolves OVERCOMMIT → riskDelta shows resolved
	// -------------------------------------------------------------------------

	@Test
	void simulate_removeCommitResolvesOvercommit_riskDeltaShowsResolved() {
		// Budget=10, two commits of 6 pts each → 12 pts → OVERCOMMIT
		// Remove one → 6 pts (60% of budget, no UNDERCOMMIT) → OVERCOMMIT resolved
		UUID commitToRemove = UUID.randomUUID();
		WeeklyCommit c1 = buildCommitWithId(commitToRemove, ChessPiece.ROOK, 6, 0, null);
		WeeklyCommit c2 = buildCommit(ChessPiece.ROOK, 6, 0, null);

		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1, c2));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());

		WhatIfMutation remove = new WhatIfMutation(WhatIfAction.REMOVE_COMMIT, commitToRemove, null, null, null, null);
		WhatIfRequest request = new WhatIfRequest(planId, userId, List.of(remove));

		WhatIfResponse response = service.simulate(request);

		assertThat(response.currentState().riskSignals()).contains("OVERCOMMIT");
		assertThat(response.projectedState().riskSignals()).doesNotContain("OVERCOMMIT");
		assertThat(response.riskDelta().resolvedRisks()).contains("OVERCOMMIT");
		assertThat(response.capacityDelta()).isEqualTo(-6);
	}

	// -------------------------------------------------------------------------
	// (c) MODIFY_COMMIT changes rcdoNodeId → rcdoCoverageChanges shows shift
	// -------------------------------------------------------------------------

	@Test
	void simulate_modifyCommitChangesRcdoNode_coverageChangesReflected() {
		UUID originalNodeId = UUID.randomUUID();
		UUID newNodeId = UUID.randomUUID();
		UUID commitId = UUID.randomUUID();

		WeeklyCommit commit = buildCommitWithId(commitId, ChessPiece.ROOK, 5, 0, null);
		commit.setRcdoNodeId(originalNodeId);

		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());

		WhatIfMutation modify = new WhatIfMutation(WhatIfAction.MODIFY_COMMIT, commitId, null, null, null, newNodeId);
		WhatIfRequest request = new WhatIfRequest(planId, userId, List.of(modify));

		WhatIfResponse response = service.simulate(request);

		assertThat(response.rcdoCoverageChanges()).hasSize(2);

		WhatIfResponse.RcdoCoverageChange origChange = response.rcdoCoverageChanges().stream()
				.filter(c -> originalNodeId.equals(c.rcdoNodeId())).findFirst().orElseThrow();
		assertThat(origChange.beforePoints()).isEqualTo(5);
		assertThat(origChange.afterPoints()).isEqualTo(0);

		WhatIfResponse.RcdoCoverageChange newChange = response.rcdoCoverageChanges().stream()
				.filter(c -> newNodeId.equals(c.rcdoNodeId())).findFirst().orElseThrow();
		assertThat(newChange.beforePoints()).isEqualTo(0);
		assertThat(newChange.afterPoints()).isEqualTo(5);
	}

	// -------------------------------------------------------------------------
	// (d) empty mutations → currentState equals projectedState
	// -------------------------------------------------------------------------

	@Test
	void simulate_emptyMutations_currentEqualsProjected() {
		UUID rcdoId = UUID.randomUUID();
		WeeklyCommit c = buildCommit(ChessPiece.ROOK, 7, 0, null);
		c.setRcdoNodeId(rcdoId);

		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());

		WhatIfRequest request = new WhatIfRequest(planId, userId, List.of());

		WhatIfResponse response = service.simulate(request);

		assertThat(response.available()).isTrue();
		assertThat(response.currentState().totalPoints()).isEqualTo(response.projectedState().totalPoints());
		assertThat(response.currentState().riskSignals()).isEqualTo(response.projectedState().riskSignals());
		assertThat(response.currentState().rcdoCoverage()).isEqualTo(response.projectedState().rcdoCoverage());
		assertThat(response.capacityDelta()).isZero();
		assertThat(response.rcdoCoverageChanges()).isEmpty();
		assertThat(response.riskDelta().newRisks()).isEmpty();
		assertThat(response.riskDelta().resolvedRisks()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// (e) plan not found → ResourceNotFoundException
	// -------------------------------------------------------------------------

	@Test
	void simulate_planNotFound_throwsResourceNotFoundException() {
		when(planRepo.findById(planId)).thenReturn(Optional.empty());

		WhatIfRequest request = new WhatIfRequest(planId, userId, List.of());

		assertThatThrownBy(() -> service.simulate(request)).isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining(planId.toString());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit buildCommit(ChessPiece chessPiece, int estimatePoints, int carryForwardStreak,
			UUID workItemId) {
		return buildCommitWithId(UUID.randomUUID(), chessPiece, estimatePoints, carryForwardStreak, workItemId);
	}

	private WeeklyCommit buildCommitWithId(UUID id, ChessPiece chessPiece, int estimatePoints, int carryForwardStreak,
			UUID workItemId) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(id);
		c.setPlanId(planId);
		c.setOwnerUserId(userId);
		c.setTitle("Commit title");
		c.setChessPiece(chessPiece);
		c.setEstimatePoints(estimatePoints);
		c.setCarryForwardStreak(carryForwardStreak);
		c.setWorkItemId(workItemId);
		c.setPriorityOrder(1);
		return c;
	}
}
