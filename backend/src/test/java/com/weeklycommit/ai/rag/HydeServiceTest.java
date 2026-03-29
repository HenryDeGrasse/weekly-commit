package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link HydeService}.
 *
 * <p>
 * Uses a mocked {@link AiProviderRegistry} so no real HTTP calls are made.
 */
@ExtendWith(MockitoExtension.class)
class HydeServiceTest {

	@Mock
	private AiProviderRegistry aiProviderRegistry;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private HydeService hydeService;

	@BeforeEach
	void setUp() {
		hydeService = new HydeService(aiProviderRegistry, objectMapper);
	}

	// ── Short-circuit paths ──────────────────────────────────────────────

	@Test
	void generateHypothetical_nullQuestion_returnsUnavailable() {
		HydeService.HydeResult result = hydeService.generateHypothetical(null, UUID.randomUUID());

		assertThat(result.available()).isFalse();
		assertThat(result.hypotheticalAnswer()).isNull();
		verify(aiProviderRegistry, never()).generateSuggestion(any(), any());
	}

	@Test
	void generateHypothetical_blankQuestion_returnsUnavailable() {
		HydeService.HydeResult result = hydeService.generateHypothetical("   ", UUID.randomUUID());

		assertThat(result.available()).isFalse();
		verify(aiProviderRegistry, never()).generateSuggestion(any(), any());
	}

	// ── Provider unavailable ─────────────────────────────────────────────

	@Test
	void generateHypothetical_providerUnavailable_returnsUnavailable() {
		when(aiProviderRegistry.generateSuggestion(any(), any())).thenReturn(AiSuggestionResult.unavailable());

		HydeService.HydeResult result = hydeService.generateHypothetical("What did the team commit to?",
				UUID.randomUUID());

		assertThat(result.available()).isFalse();
		assertThat(result.hypotheticalAnswer()).isNull();
	}

	// ── Happy path ───────────────────────────────────────────────────────

	@Test
	void generateHypothetical_validResponse_returnsHypotheticalAnswer() throws Exception {
		String payload = objectMapper.writeValueAsString(java.util.Map.of("hypotheticalAnswer",
				"The team committed to 10 work items with 2 KING priorities and 24 total points."));
		when(aiProviderRegistry.generateSuggestion(any(), any()))
				.thenReturn(new AiSuggestionResult(true, payload, "rationale", 0.85, "model-v1"));

		HydeService.HydeResult result = hydeService.generateHypothetical("What did the team commit to last week?",
				UUID.randomUUID());

		assertThat(result.available()).isTrue();
		assertThat(result.hypotheticalAnswer()).contains("10 work items").contains("KING");
	}

	@Test
	void generateHypothetical_usesTypeHydeContext() {
		when(aiProviderRegistry.generateSuggestion(any(), any())).thenReturn(AiSuggestionResult.unavailable());
		ArgumentCaptor<AiContext> captor = ArgumentCaptor.forClass(AiContext.class);

		hydeService.generateHypothetical("some question", UUID.randomUUID());

		verify(aiProviderRegistry).generateSuggestion(captor.capture(), any());
		assertThat(captor.getValue().suggestionType()).isEqualTo(AiContext.TYPE_HYDE);
	}

	@Test
	void generateHypothetical_passesQuestionInAdditionalContext() {
		when(aiProviderRegistry.generateSuggestion(any(), any())).thenReturn(AiSuggestionResult.unavailable());
		ArgumentCaptor<AiContext> captor = ArgumentCaptor.forClass(AiContext.class);
		String question = "What is the carry-forward rate?";

		hydeService.generateHypothetical(question, UUID.randomUUID());

		verify(aiProviderRegistry).generateSuggestion(captor.capture(), any());
		assertThat(captor.getValue().additionalContext()).containsEntry("question", question);
	}

	@Test
	void generateHypothetical_nullUserId_doesNotThrow() throws Exception {
		String payload = objectMapper.writeValueAsString(java.util.Map.of("hypotheticalAnswer", "A plausible answer."));
		when(aiProviderRegistry.generateSuggestion(any(), any()))
				.thenReturn(new AiSuggestionResult(true, payload, "rationale", 0.8, "model-v1"));

		HydeService.HydeResult result = hydeService.generateHypothetical("some question", null);

		assertThat(result.available()).isTrue();
	}

	// ── Malformed responses ──────────────────────────────────────────────

	@Test
	void generateHypothetical_missingHypotheticalAnswerField_returnsUnavailable() throws Exception {
		String payload = objectMapper.writeValueAsString(java.util.Map.of("other", "value"));
		when(aiProviderRegistry.generateSuggestion(any(), any()))
				.thenReturn(new AiSuggestionResult(true, payload, "rationale", 0.8, "model-v1"));

		HydeService.HydeResult result = hydeService.generateHypothetical("some question", UUID.randomUUID());

		assertThat(result.available()).isFalse();
	}

	@Test
	void generateHypothetical_malformedJson_returnsUnavailable() {
		when(aiProviderRegistry.generateSuggestion(any(), any()))
				.thenReturn(new AiSuggestionResult(true, "not-json", "rationale", 0.8, "model-v1"));

		HydeService.HydeResult result = hydeService.generateHypothetical("some question", UUID.randomUUID());

		assertThat(result.available()).isFalse();
	}

	// ── HydeResult record ────────────────────────────────────────────────

	@Test
	void hydeResult_unavailableFactory_setsCorrectFields() {
		HydeService.HydeResult result = HydeService.HydeResult.unavailable();
		assertThat(result.available()).isFalse();
		assertThat(result.hypotheticalAnswer()).isNull();
	}
}
