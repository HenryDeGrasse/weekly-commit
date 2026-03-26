package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.repository.TeamRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SemanticQueryService}.
 *
 * <p>
 * Mocks all upstream services so no real HTTP / DB calls are made.
 */
@ExtendWith(MockitoExtension.class)
class SemanticQueryServiceTest {

	private static final UUID TEAM_ID = UUID.randomUUID();
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID ORG_ID = UUID.randomUUID();
	private static final String QUESTION = "What did the team commit to last week?";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private PineconeClient pineconeClient;

	@Mock
	private EmbeddingService embeddingService;

	@Mock
	private AiProviderRegistry aiProviderRegistry;

	@Mock
	private AiSuggestionService aiSuggestionService;

	@Mock
	private TeamRepository teamRepo;

	private SemanticQueryService service;

	@BeforeEach
	void setUp() {
		service = new SemanticQueryService(pineconeClient, embeddingService, aiProviderRegistry, aiSuggestionService,
				objectMapper, teamRepo);
	}

	// ── Availability guards ───────────────────────────────────────────────

	@Test
	void query_pineconeUnavailable_returnsUnavailableResult() {
		when(pineconeClient.isAvailable()).thenReturn(false);

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isFalse();
		assertThat(result.answer()).isNull();
		verify(embeddingService, never()).embed(anyString());
	}

	@Test
	void query_embeddingUnavailable_returnsUnavailableResult() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(false);

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isFalse();
		verify(embeddingService, never()).embed(anyString());
	}

	@Test
	void query_nullQuestion_returnsUnavailableResult() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(true);

		SemanticQueryService.RagQueryResult result = service.query(null, TEAM_ID, USER_ID);

		assertThat(result.available()).isFalse();
	}

	@Test
	void query_blankQuestion_returnsUnavailableResult() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(true);

		SemanticQueryService.RagQueryResult result = service.query("   ", TEAM_ID, USER_ID);

		assertThat(result.available()).isFalse();
	}

	@Test
	void query_embeddingReturnsEmptyVector_returnsUnavailableResult() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(true);
		when(aiProviderRegistry.generateSuggestion(any())).thenReturn(AiSuggestionResult.unavailable());
		when(embeddingService.embed(anyString())).thenReturn(new float[0]);

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isFalse();
		verify(pineconeClient, never()).query(anyString(), any(), anyInt(), any());
	}

	// ── Successful pipeline ───────────────────────────────────────────────

	@Test
	void query_successfulPipeline_returnsAvailableResult() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		float[] embedding = {0.1f, 0.2f, 0.3f};
		when(embeddingService.embed(QUESTION)).thenReturn(embedding);
		mockPineconeMatches(List.of("commit:uuid-1"), List.of(0.92));
		mockRagAnswer("The team committed to deploying the new API.", 0.9);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		assertThat(result.answer()).isEqualTo("The team committed to deploying the new API.");
		assertThat(result.confidence()).isEqualTo(0.9);
		assertThat(result.suggestionId()).isNotNull();
	}

	@Test
	void query_successfulPipeline_returnsCorrectSources() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of("commit:uuid-1"), List.of(0.92));
		String answerWithSource = """
				{
				  "answer": "Team committed to API work.",
				  "sources": [
				    {"entityType": "commit", "entityId": "uuid-1", "weekStartDate": "2025-01-06", "snippet": "Deploy new API"}
				  ],
				  "confidence": 0.85
				}
				""";
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_QUERY)))
				.thenReturn(new AiSuggestionResult(true, answerWithSource.trim(), "RAG answer", 0.85, "stub-v1"));
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.sources()).hasSize(1);
		assertThat(result.sources().get(0).entityType()).isEqualTo("commit");
		assertThat(result.sources().get(0).entityId()).isEqualTo("uuid-1");
		assertThat(result.sources().get(0).weekStartDate()).isEqualTo("2025-01-06");
	}

	// ── Intent failure degrades gracefully ────────────────────────────────

	@Test
	void query_intentClassificationFails_proceedsWithDefaults() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		// Intent returns unavailable
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_INTENT)))
				.thenReturn(AiSuggestionResult.unavailable());
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("No data found.", 0.3);
		stubSuggestionStore(UUID.randomUUID());

		// Should not throw; should proceed with empty filter defaults
		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		// Pinecone is still called (with default/empty filter)
		verify(pineconeClient).query(anyString(), any(), eq(5), any());
		assertThat(result.available()).isTrue();
	}

	@Test
	void query_intentPayloadUnparseable_proceedsWithDefaults() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_INTENT)))
				.thenReturn(new AiSuggestionResult(true, "not-valid-json", "intent", 0.5, "stub"));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("Answer.", 0.5);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
	}

	// ── Audit storage ─────────────────────────────────────────────────────

	@Test
	void query_successfulResult_storesSuggestionWithCorrectType() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("Answer.", 0.8);
		UUID suggestionId = UUID.randomUUID();
		stubSuggestionStore(suggestionId);

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
		verify(aiSuggestionService).storeSuggestion(typeCaptor.capture(), eq(USER_ID), isNull(), isNull(), anyString(),
				any(AiSuggestionResult.class));
		assertThat(typeCaptor.getValue()).isEqualTo(AiContext.TYPE_RAG_QUERY);
		assertThat(result.suggestionId()).isEqualTo(suggestionId);
	}

	@Test
	void query_suggestionStoreFails_stillReturnsResult() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of()));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("Answer.", 0.7);
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenThrow(new RuntimeException("DB error"));

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		assertThat(result.suggestionId()).isNull();
	}

	// ── Namespace resolution ──────────────────────────────────────────────

	@Test
	void query_usesOrgIdAsNamespace() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("general_query", null, null, null, List.of()));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		mockRagAnswer("Answer.", 0.5);
		stubSuggestionStore(UUID.randomUUID());

		service.query(QUESTION, TEAM_ID, USER_ID);

		ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
		verify(pineconeClient).query(namespaceCaptor.capture(), any(), anyInt(), any());
		assertThat(namespaceCaptor.getValue()).isEqualTo(ORG_ID.toString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void query_intentFiltersApplyUserAndDateRangeToPineconeQuery() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("general_query", "self", "2025-01-06", "2025-01-31", List.of("commit")));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		mockRagAnswer("Answer.", 0.5);
		stubSuggestionStore(UUID.randomUUID());

		service.query(QUESTION, TEAM_ID, USER_ID);

		ArgumentCaptor<Map<String, Object>> filterCaptor = ArgumentCaptor.forClass(Map.class);
		verify(pineconeClient).query(anyString(), any(), eq(5), filterCaptor.capture());
		Map<String, Object> filter = filterCaptor.getValue();
		assertThat(filter.get("teamId")).isEqualTo(TEAM_ID.toString());
		assertThat(filter.get("userId")).isEqualTo(USER_ID.toString());
		assertThat((Map<String, Object>) filter.get("entityType")).containsEntry("$in", List.of("commit"));
		assertThat((Map<String, Object>) filter.get("weekStartDate")).containsEntry("$gte", "2025-01-06")
				.containsEntry("$lte", "2025-01-31");
	}

	// ── Helpers ───────────────────────────────────────────────────────────

	private void setUpAvailableServices() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(true);
	}

	private void setUpTeam() {
		Team team = new Team();
		team.setId(TEAM_ID);
		team.setOrganizationId(ORG_ID);
		when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(team));
	}

	private void mockIntentResponse(String payload) {
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_INTENT)))
				.thenReturn(new AiSuggestionResult(true, payload, "intent", 0.9, "stub-v1"));
	}

	private void mockRagAnswer(String answer, double confidence) throws Exception {
		String payload = objectMapper
				.writeValueAsString(Map.of("answer", answer, "sources", List.of(), "confidence", confidence));
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_QUERY)))
				.thenReturn(new AiSuggestionResult(true, payload, "rag answer", confidence, "stub-v1"));
	}

	private void mockPineconeMatches(List<String> ids, List<Double> scores) {
		List<PineconeClient.PineconeMatch> matches = new java.util.ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			matches.add(new PineconeClient.PineconeMatch(ids.get(i), scores.get(i), Map.of()));
		}
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(matches);
	}

	private void stubSuggestionStore(UUID suggestionId) {
		AiSuggestion stored = new AiSuggestion();
		stored.setId(suggestionId);
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);
	}

	private static String intentPayload(String intent, String userFilter, String from, String to,
			List<String> entityTypes) throws Exception {
		Map<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("intent", intent);
		payload.put("userFilter", userFilter);
		payload.put("entityTypes", entityTypes);
		payload.put("keywords", List.of());
		if (from != null || to != null) {
			payload.put("timeRange", Map.of("from", from, "to", to));
		} else {
			payload.put("timeRange", null);
		}
		return new ObjectMapper().writeValueAsString(payload);
	}

	/** Matches an AiContext by its suggestionType for Mockito. */
	private static AiContext argOfType(String type) {
		return org.mockito.ArgumentMatchers.argThat(ctx -> ctx != null && type.equals(ctx.suggestionType()));
	}
}
