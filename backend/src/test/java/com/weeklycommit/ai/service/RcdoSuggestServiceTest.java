package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.RcdoSuggestRequest;
import com.weeklycommit.ai.dto.RcdoSuggestResponse;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcdoSuggestServiceTest {

	@Mock
	private AiProviderRegistry registry;

	@Mock
	private AiSuggestionService suggestionService;

	@Mock
	private RcdoNodeRepository rcdoNodeRepo;

	private RcdoSuggestService service;

	private UUID planId;
	private UUID userId;
	private UUID rcdoId;

	@BeforeEach
	void setUp() {
		service = new RcdoSuggestService(registry, suggestionService, rcdoNodeRepo, new ObjectMapper());
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();
		rcdoId = UUID.randomUUID();
	}

	// -------------------------------------------------------------------------
	// AI disabled
	// -------------------------------------------------------------------------

	@Test
	void suggest_whenAiDisabled_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(false);

		RcdoSuggestResponse resp = service
				.suggest(new RcdoSuggestRequest(planId, userId, "Implement auth", null, null));

		assertThat(resp.aiAvailable()).isFalse();
		assertThat(resp.suggestionAvailable()).isFalse();
	}

	// -------------------------------------------------------------------------
	// Confidence threshold
	// -------------------------------------------------------------------------

	@Test
	void suggest_whenConfidenceAboveThreshold_returnsSuggestion() throws Exception {
		when(registry.isAiEnabled()).thenReturn(true);
		when(rcdoNodeRepo.findByStatus(RcdoNodeStatus.ACTIVE)).thenReturn(List.of());

		String payload = "{\"suggestedRcdoNodeId\":\"" + rcdoId + "\",\"confidence\":0.85}";
		AiSuggestionResult result = new AiSuggestionResult(true, payload, "good match", 0.85, "stub-v1");
		when(registry.generateSuggestion(any())).thenReturn(result);

		RcdoNode node = buildNode(rcdoId, "Outcome: Improve Security");
		when(rcdoNodeRepo.findById(rcdoId)).thenReturn(Optional.of(node));

		AiSuggestion stored = new AiSuggestion();
		stored.setId(UUID.randomUUID());
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		RcdoSuggestResponse resp = service
				.suggest(new RcdoSuggestRequest(planId, userId, "Implement auth security", null, null));

		assertThat(resp.aiAvailable()).isTrue();
		assertThat(resp.suggestionAvailable()).isTrue();
		assertThat(resp.suggestedRcdoNodeId()).isEqualTo(rcdoId);
		assertThat(resp.rcdoTitle()).isEqualTo("Outcome: Improve Security");
		assertThat(resp.confidence()).isGreaterThanOrEqualTo(0.7);
	}

	@Test
	void suggest_whenConfidenceBelowThreshold_doesNotSurface() throws Exception {
		when(registry.isAiEnabled()).thenReturn(true);
		when(rcdoNodeRepo.findByStatus(RcdoNodeStatus.ACTIVE)).thenReturn(List.of());

		// Confidence below the 0.7 threshold
		String payload = "{\"suggestedRcdoNodeId\":\"" + rcdoId + "\",\"confidence\":0.5}";
		AiSuggestionResult result = new AiSuggestionResult(true, payload, "low confidence", 0.5, "stub-v1");
		when(registry.generateSuggestion(any())).thenReturn(result);

		RcdoSuggestResponse resp = service
				.suggest(new RcdoSuggestRequest(planId, userId, "something vague", null, null));

		assertThat(resp.aiAvailable()).isTrue();
		assertThat(resp.suggestionAvailable()).isFalse();
		assertThat(resp.suggestedRcdoNodeId()).isNull();
	}

	@Test
	void suggest_whenSuggestedRcdoNodeNotFound_returnsNoSuggestion() throws Exception {
		when(registry.isAiEnabled()).thenReturn(true);
		when(rcdoNodeRepo.findByStatus(RcdoNodeStatus.ACTIVE)).thenReturn(List.of());

		String payload = "{\"suggestedRcdoNodeId\":\"" + rcdoId + "\",\"confidence\":0.9}";
		AiSuggestionResult result = new AiSuggestionResult(true, payload, "match", 0.9, "stub-v1");
		when(registry.generateSuggestion(any())).thenReturn(result);

		// Node not found in DB
		when(rcdoNodeRepo.findById(rcdoId)).thenReturn(Optional.empty());

		RcdoSuggestResponse resp = service
				.suggest(new RcdoSuggestRequest(planId, userId, "do important work", null, null));

		assertThat(resp.suggestionAvailable()).isFalse();
	}

	@Test
	void suggest_whenProviderUnavailable_returnsUnavailable() {
		when(registry.isAiEnabled()).thenReturn(true);
		when(rcdoNodeRepo.findByStatus(RcdoNodeStatus.ACTIVE)).thenReturn(List.of());
		when(registry.generateSuggestion(any())).thenReturn(AiSuggestionResult.unavailable());

		RcdoSuggestResponse resp = service.suggest(new RcdoSuggestRequest(planId, userId, "do work", null, null));

		assertThat(resp.aiAvailable()).isFalse();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private RcdoNode buildNode(UUID id, String title) {
		RcdoNode node = new RcdoNode();
		node.setId(id);
		node.setTitle(title);
		return node;
	}
}
