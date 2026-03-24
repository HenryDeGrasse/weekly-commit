package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.ReconcileAssistRequest;
import com.weeklycommit.ai.dto.ReconcileAssistResponse;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
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
class ReconcileAssistServiceTest {

	@Mock
	private AiProviderRegistry registry;

	@Mock
	private AiSuggestionService suggestionService;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private ScopeChangeEventRepository scopeChangeRepo;

	private ReconcileAssistService service;

	private UUID planId;
	private UUID userId;
	private WeeklyPlan plan;

	@BeforeEach
	void setUp() {
		service = new ReconcileAssistService(registry, suggestionService, planRepo, commitRepo, scopeChangeRepo,
				new ObjectMapper());
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();

		plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(userId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 23));
		plan.setState(PlanState.RECONCILING);
		plan.setCapacityBudgetPoints(10);
	}

	// -------------------------------------------------------------------------
	// AI disabled
	// -------------------------------------------------------------------------

	@Test
	void assist_whenAiDisabled_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(false);

		ReconcileAssistResponse resp = service.assist(new ReconcileAssistRequest(planId, userId));

		assertThat(resp.aiAvailable()).isFalse();
		assertThat(resp.likelyOutcomes()).isEmpty();
		assertThat(resp.draftSummary()).isNull();
		assertThat(resp.carryForwardRecommendations()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// AI available
	// -------------------------------------------------------------------------

	@Test
	void assist_whenAiAvailable_returnsResponse() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());

		String payload = "{\"likelyOutcomes\":[],\"draftSummary\":\"Good week.\",\"carryForwardRecommendations\":[]}";
		AiSuggestionResult result = new AiSuggestionResult(true, payload, "rationale", 0.9, "stub-v1");
		when(registry.generateSuggestion(any())).thenReturn(result);

		AiSuggestion stored = new AiSuggestion();
		UUID suggestionId = UUID.randomUUID();
		stored.setId(suggestionId);
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		ReconcileAssistResponse resp = service.assist(new ReconcileAssistRequest(planId, userId));

		assertThat(resp.aiAvailable()).isTrue();
		assertThat(resp.suggestionId()).isEqualTo(suggestionId);
		assertThat(resp.draftSummary()).isEqualTo("Good week.");
		assertThat(resp.likelyOutcomes()).isEmpty();
		assertThat(resp.carryForwardRecommendations()).isEmpty();
	}

	@Test
	void assist_whenProviderUnavailable_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(registry.generateSuggestion(any())).thenReturn(AiSuggestionResult.unavailable());

		ReconcileAssistResponse resp = service.assist(new ReconcileAssistRequest(planId, userId));

		assertThat(resp.aiAvailable()).isFalse();
	}

	@Test
	void assist_storesSuggestionInDb() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());

		String payload = "{\"likelyOutcomes\":[],\"draftSummary\":\"ok\",\"carryForwardRecommendations\":[]}";
		when(registry.generateSuggestion(any())).thenReturn(new AiSuggestionResult(true, payload, "r", 0.9, "v1"));

		AiSuggestion stored = new AiSuggestion();
		stored.setId(UUID.randomUUID());
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		service.assist(new ReconcileAssistRequest(planId, userId));

		verify(suggestionService).storeSuggestion(anyString(), any(), any(), any(), anyString(), any());
	}
}
