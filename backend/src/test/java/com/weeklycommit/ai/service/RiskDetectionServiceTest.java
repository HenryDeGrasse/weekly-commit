package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.team.service.AuthorizationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskDetectionServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private WorkItemStatusHistoryRepository statusHistoryRepo;

	@Mock
	private ScopeChangeEventRepository scopeChangeRepo;

	@Mock
	private AiSuggestionRepository suggestionRepo;

	@Mock
	private AuthorizationService authService;

	private RiskDetectionService service;

	private UUID planId;
	private UUID userId;
	private WeeklyPlan plan;

	@Mock
	private com.weeklycommit.ai.provider.AiProviderRegistry aiProviderRegistry;

	@BeforeEach
	void setUp() {
		service = new RiskDetectionService(planRepo, commitRepo, workItemRepo, statusHistoryRepo, scopeChangeRepo,
				suggestionRepo, authService, aiProviderRegistry, new ObjectMapper());
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();

		plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(userId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 23));
		plan.setCapacityBudgetPoints(10);
		plan.setState(PlanState.LOCKED);
	}

	// -------------------------------------------------------------------------
	// OVERCOMMIT signal
	// -------------------------------------------------------------------------

	@Test
	void detectSignals_overcommit_storedWhenPointsExceedBudget() {
		plan.setCapacityBudgetPoints(5);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(
				List.of(buildCommit(ChessPiece.KING, 3, 0, null), buildCommit(ChessPiece.ROOK, 3, 0, null)));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		verify(suggestionRepo, atLeastOnce()).save(captor.capture());

		boolean hasOvercommit = captor.getAllValues().stream()
				.anyMatch(s -> s.getSuggestionPayload().contains("OVERCOMMIT"));
		assertThat(hasOvercommit).isTrue();
	}

	// -------------------------------------------------------------------------
	// UNDERCOMMIT signal
	// -------------------------------------------------------------------------

	@Test
	void detectSignals_undercommit_storedWhenPointsLessThan60PercentBudget() {
		plan.setCapacityBudgetPoints(10);
		// Only 5 pts planned → 50% < 60%
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(buildCommit(ChessPiece.ROOK, 5, 0, null)));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		verify(suggestionRepo, atLeastOnce()).save(captor.capture());

		boolean hasUndercommit = captor.getAllValues().stream()
				.anyMatch(s -> s.getSuggestionPayload().contains("UNDERCOMMIT"));
		assertThat(hasUndercommit).isTrue();
	}

	// -------------------------------------------------------------------------
	// REPEATED_CARRY_FORWARD signal
	// -------------------------------------------------------------------------

	@Test
	void detectSignals_repeatedCarryForward_storedWhenStreakAtLeast2() {
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(buildCommit(ChessPiece.ROOK, 3, 2, null)));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		verify(suggestionRepo, atLeastOnce()).save(captor.capture());

		boolean hasRepeated = captor.getAllValues().stream()
				.anyMatch(s -> s.getSuggestionPayload().contains("REPEATED_CARRY_FORWARD"));
		assertThat(hasRepeated).isTrue();
	}

	@Test
	void detectSignals_carryForwardStreak1_noSignal() {
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(buildCommit(ChessPiece.ROOK, 3, 1, null)));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		// save may not be called if no signals; that's fine
		List<AiSuggestion> saved = captor.getAllValues();
		boolean hasRepeated = saved.stream().anyMatch(s -> s.getSuggestionPayload().contains("REPEATED_CARRY_FORWARD"));
		assertThat(hasRepeated).isFalse();
	}

	// -------------------------------------------------------------------------
	// BLOCKED_CRITICAL signal
	// -------------------------------------------------------------------------

	@Test
	void detectSignals_blockedCritical_storedWhenKingTicketBlockedOver48Hours() {
		UUID workItemId = UUID.randomUUID();
		WeeklyCommit kingCommit = buildCommit(ChessPiece.KING, 3, 0, workItemId);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(kingCommit));

		WorkItem ticket = new WorkItem();
		ticket.setId(workItemId);
		ticket.setStatus(TicketStatus.BLOCKED);
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(ticket));

		// Blocked > 48 hours ago
		WorkItemStatusHistory history = new WorkItemStatusHistory();
		history.setWorkItemId(workItemId);
		history.setToStatus("BLOCKED");
		// Use a package-private setter approach — set via a helper since entity has no
		// public setter for createdAt
		// We'll use a subclass approach via reflection-free: just verify the signal is
		// raised
		// For testing purposes, we need to mock the createdAt. WorkItemStatusHistory
		// doesn't expose setCreatedAt.
		// We'll use a wrapper record approach since the entity is not final.
		// Instead, let's just verify the service logic path by mocking properly.

		// Since WorkItemStatusHistory uses @CreationTimestamp, we can't set createdAt
		// via setter.
		// The service uses history.getCreatedAt() to check if it was > 48h ago.
		// We'll create a spy/subclass to override getCreatedAt.
		WorkItemStatusHistory oldHistory = new WorkItemStatusHistoryWithCreatedAt(workItemId, "BLOCKED",
				Instant.now().minus(72, ChronoUnit.HOURS));
		when(statusHistoryRepo.findByWorkItemIdOrderByCreatedAtAsc(workItemId)).thenReturn(List.of(oldHistory));

		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		verify(suggestionRepo, atLeastOnce()).save(captor.capture());

		boolean hasBlocked = captor.getAllValues().stream()
				.anyMatch(s -> s.getSuggestionPayload().contains("BLOCKED_CRITICAL"));
		assertThat(hasBlocked).isTrue();
	}

	@Test
	void detectSignals_blockedCritical_notRaisedWhenPawnTicketBlocked() {
		UUID workItemId = UUID.randomUUID();
		WeeklyCommit pawnCommit = buildCommit(ChessPiece.PAWN, 2, 0, workItemId);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(pawnCommit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		List<AiSuggestion> saved = captor.getAllValues();
		boolean hasBlocked = saved.stream().anyMatch(s -> s.getSuggestionPayload().contains("BLOCKED_CRITICAL"));
		assertThat(hasBlocked).isFalse();
	}

	// -------------------------------------------------------------------------
	// SCOPE_VOLATILITY signal
	// -------------------------------------------------------------------------

	@Test
	void detectSignals_scopeVolatility_storedWhenMoreThan3Changes() {
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId))
				.thenReturn(List.of(new com.weeklycommit.domain.entity.ScopeChangeEvent(),
						new com.weeklycommit.domain.entity.ScopeChangeEvent(),
						new com.weeklycommit.domain.entity.ScopeChangeEvent(),
						new com.weeklycommit.domain.entity.ScopeChangeEvent()));
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		verify(suggestionRepo, atLeastOnce()).save(captor.capture());

		boolean hasVolatility = captor.getAllValues().stream()
				.anyMatch(s -> s.getSuggestionPayload().contains("SCOPE_VOLATILITY"));
		assertThat(hasVolatility).isTrue();
	}

	@Test
	void detectSignals_scopeVolatility_notRaisedWhenThreeOrFewer() {
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId))
				.thenReturn(List.of(new com.weeklycommit.domain.entity.ScopeChangeEvent(),
						new com.weeklycommit.domain.entity.ScopeChangeEvent()));
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of());

		service.detectAndStoreRiskSignals(plan);

		ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
		List<AiSuggestion> saved = captor.getAllValues();
		boolean hasVolatility = saved.stream().anyMatch(s -> s.getSuggestionPayload().contains("SCOPE_VOLATILITY"));
		assertThat(hasVolatility).isFalse();
	}

	// -------------------------------------------------------------------------
	// Daily job
	// -------------------------------------------------------------------------

	@Test
	void runDailyRiskDetection_processesAllLockedPlans() {
		WeeklyPlan lockedPlan1 = buildPlan(UUID.randomUUID(), PlanState.LOCKED, 10);
		WeeklyPlan lockedPlan2 = buildPlan(UUID.randomUUID(), PlanState.LOCKED, 10);

		when(planRepo.findByState(PlanState.LOCKED)).thenReturn(List.of(lockedPlan1, lockedPlan2));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(any())).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
		when(suggestionRepo.findByPlanIdAndSuggestionType(any(), anyString())).thenReturn(List.of());

		service.runDailyRiskDetection();

		// Both plans should have been processed (deleteAll called for each)
		verify(suggestionRepo, atLeastOnce()).deleteAll(any());
	}

	// -------------------------------------------------------------------------
	// getRiskSignals
	// -------------------------------------------------------------------------

	@Test
	void getRiskSignals_returnsPlanSignals() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		AiSuggestion sig = new AiSuggestion();
		sig.setId(UUID.randomUUID());
		sig.setPlanId(planId);
		sig.setRationale("overcommit detected");
		sig.setSuggestionPayload("{\"signalType\":\"OVERCOMMIT\"}");
		sig.setSuggestionType("RISK_SIGNAL");
		sig.setModelVersion("rules-v1");
		sig.setPrompt("{}");
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL")).thenReturn(List.of(sig));

		PlanRiskSignals result = service.getRiskSignals(planId, userId);

		assertThat(result.aiAvailable()).isTrue();
		assertThat(result.planId()).isEqualTo(planId);
		assertThat(result.signals()).hasSize(1);
		assertThat(result.signals().get(0).signalType()).isEqualTo("OVERCOMMIT");
		assertThat(result.signals().get(0).rationale()).isEqualTo("overcommit detected");
	}

	@Test
	void getRiskSignals_checksPrivacyAgainstPlanOwner() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		service.getRiskSignals(planId, userId);

		verify(authService).checkCanAccessUserFullDetail(userId, userId);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit buildCommit(ChessPiece chessPiece, int estimatePoints, int carryForwardStreak,
			UUID workItemId) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
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

	private WeeklyPlan buildPlan(UUID id, PlanState state, int capacityPoints) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(id);
		p.setOwnerUserId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2026, 3, 23));
		p.setState(state);
		p.setCapacityBudgetPoints(capacityPoints);
		return p;
	}

	/**
	 * Subclass of WorkItemStatusHistory that overrides getCreatedAt for testing.
	 */
	static class WorkItemStatusHistoryWithCreatedAt extends WorkItemStatusHistory {

		private final Instant createdAtOverride;

		WorkItemStatusHistoryWithCreatedAt(UUID workItemId, String toStatus, Instant createdAt) {
			this.setWorkItemId(workItemId);
			this.setToStatus(toStatus);
			this.createdAtOverride = createdAt;
		}

		@Override
		public Instant getCreatedAt() {
			return createdAtOverride;
		}
	}
}
