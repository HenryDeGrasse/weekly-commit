package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.CommitDraftAssistRequest;
import com.weeklycommit.ai.dto.CommitDraftAssistResponse;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
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
class CommitDraftAssistServiceTest {

	@Mock
	private AiProviderRegistry registry;

	@Mock
	private AiSuggestionService suggestionService;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	private CommitDraftAssistService service;

	private UUID planId;
	private UUID userId;
	private WeeklyPlan plan;

	@BeforeEach
	void setUp() {
		service = new CommitDraftAssistService(registry, suggestionService, planRepo, commitRepo, new ObjectMapper());
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();

		plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(userId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 23));
		plan.setCapacityBudgetPoints(10);
	}

	// -------------------------------------------------------------------------
	// AI disabled
	// -------------------------------------------------------------------------

	@Test
	void assist_whenAiDisabled_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(false);

		CommitDraftAssistRequest req = new CommitDraftAssistRequest(planId, userId, null, "Do stuff", null, null, null,
				ChessPiece.PAWN);
		CommitDraftAssistResponse resp = service.assist(req);

		assertThat(resp.aiAvailable()).isFalse();
		assertThat(resp.suggestedTitle()).isNull();
	}

	// -------------------------------------------------------------------------
	// AI available — returns suggestions
	// -------------------------------------------------------------------------

	@Test
	void assist_whenAiAvailable_returnsStructuredSuggestions() throws Exception {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByOwnerUserId(userId)).thenReturn(List.of());

		String payload = "{\"suggestedTitle\":\"Better Title\"," + "\"suggestedDescription\":\"Better desc\","
				+ "\"suggestedSuccessCriteria\":\"Criteria\"," + "\"suggestedEstimatePoints\":3}";
		AiSuggestionResult result = new AiSuggestionResult(true, payload, "test rationale", 0.85, "stub-v1");
		when(registry.generateSuggestion(any())).thenReturn(result);

		AiSuggestion stored = new AiSuggestion();
		UUID suggestionId = UUID.randomUUID();
		stored.setId(suggestionId);
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		CommitDraftAssistRequest req = new CommitDraftAssistRequest(planId, userId, null, "Do stuff", null, null, null,
				ChessPiece.KING);
		CommitDraftAssistResponse resp = service.assist(req);

		assertThat(resp.aiAvailable()).isTrue();
		assertThat(resp.suggestionId()).isEqualTo(suggestionId);
		assertThat(resp.suggestedTitle()).isEqualTo("Better Title");
		assertThat(resp.suggestedDescription()).isEqualTo("Better desc");
		assertThat(resp.suggestedSuccessCriteria()).isEqualTo("Criteria");
		assertThat(resp.suggestedEstimatePoints()).isEqualTo(3);
		assertThat(resp.rationale()).isEqualTo("test rationale");
	}

	@Test
	void assist_whenAiResultUnavailable_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByOwnerUserId(userId)).thenReturn(List.of());
		when(registry.generateSuggestion(any())).thenReturn(AiSuggestionResult.unavailable());

		CommitDraftAssistRequest req = new CommitDraftAssistRequest(planId, userId, null, "Do stuff", null, null, null,
				ChessPiece.PAWN);
		CommitDraftAssistResponse resp = service.assist(req);

		assertThat(resp.aiAvailable()).isFalse();
	}

	@Test
	void assist_storesSuggestionInDb() throws Exception {
		when(registry.isAiEnabled()).thenReturn(true);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByOwnerUserId(userId)).thenReturn(List.of());

		String payload = "{\"suggestedTitle\":\"T\",\"suggestedDescription\":\"D\","
				+ "\"suggestedSuccessCriteria\":null,\"suggestedEstimatePoints\":null}";
		AiSuggestionResult result = new AiSuggestionResult(true, payload, "rationale", 0.8, "stub-v1");
		when(registry.generateSuggestion(any())).thenReturn(result);

		AiSuggestion stored = new AiSuggestion();
		stored.setId(UUID.randomUUID());
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		CommitDraftAssistRequest req = new CommitDraftAssistRequest(planId, userId, null, "title", null, null, null,
				ChessPiece.ROOK);
		service.assist(req);

		verify(suggestionService).storeSuggestion(anyString(), any(), any(), any(), anyString(), any());
	}
}
