package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.evidence.ConfidenceTierCalculator;
import com.weeklycommit.ai.evidence.ConfidenceTierCalculator.ConfidenceTier;
import com.weeklycommit.ai.experiment.ExperimentService;
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

	@Mock
	private RerankService rerankService;

	@Mock
	private ConfidenceTierCalculator confidenceTierCalculator;

	@Mock
	private QueryRewriter queryRewriter;

	@Mock
	private ExperimentService experimentService;

	@Mock
	private HydeService hydeService;

	@Mock
	private SqlQueryRouter sqlQueryRouter;

	private SemanticQueryService service;

	@BeforeEach
	void setUp() {
		// SparseEncoder is pure computation — use the real instance (no mock needed)
		service = new SemanticQueryService(pineconeClient, embeddingService, aiProviderRegistry, aiSuggestionService,
				objectMapper, teamRepo, new SparseEncoder(), rerankService, confidenceTierCalculator, queryRewriter,
				experimentService, hydeService, sqlQueryRouter);
		lenient()
				.when(confidenceTierCalculator
						.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>any()))
				.thenReturn(ConfidenceTier.MEDIUM);
		// Default: QueryRewriter is a no-op passthrough for existing tests
		lenient().when(queryRewriter.rewrite(anyString())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(queryRewriter.decompose(anyString())).thenAnswer(inv -> List.of((String) inv.getArgument(0)));
		// Default: all experiments disabled so existing tests are unaffected
		lenient().when(experimentService.isEnabled(anyString())).thenReturn(false);
		// Default: SQL router does not handle anything (falls through to vector search)
		lenient().when(sqlQueryRouter.canHandle(anyString(), any())).thenReturn(false);
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
		verify(pineconeClient, never()).query(anyString(), any(), anyInt(), any(), any());
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
	void query_confidenceTier_isComputedFromRerankedMatches() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});

		List<PineconeClient.PineconeMatch> matches = List.of(
				new PineconeClient.PineconeMatch("commit:high-1", 0.95, Map.of()),
				new PineconeClient.PineconeMatch("commit:high-2", 0.93, Map.of()),
				new PineconeClient.PineconeMatch("commit:high-3", 0.91, Map.of()));
		when(pineconeClient.query(anyString(), any(), anyInt(), any(), any())).thenReturn(matches);
		when(rerankService.getTopN()).thenReturn(20);
		when(rerankService.rerank(anyString(), any(), anyInt()))
				.thenReturn(List.of(new RerankService.RankedChunk("commit:high-1", 0.95, Map.of(), 0.20),
						new RerankService.RankedChunk("commit:high-2", 0.93, Map.of(), 0.15)));
		when(confidenceTierCalculator
				.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>argThat(
						rerankedMatches -> rerankedMatches != null && rerankedMatches.size() == 2
								&& rerankedMatches.get(0).score() == 0.20 && rerankedMatches.get(1).score() == 0.15)))
				.thenReturn(ConfidenceTier.LOW);
		mockRagAnswer("Answer.", 0.6);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.confidenceTier()).isEqualTo(ConfidenceTier.LOW);
		verify(confidenceTierCalculator)
				.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>argThat(
						rerankedMatches -> rerankedMatches != null && rerankedMatches.size() == 2
								&& rerankedMatches.get(0).score() == 0.20 && rerankedMatches.get(1).score() == 0.15));
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
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_QUERY), any()))
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
		verify(pineconeClient).query(anyString(), any(), eq(80), any(), any());
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
		when(pineconeClient.query(anyString(), any(), anyInt(), any(), any())).thenReturn(List.of());
		stubRerankerPassThrough(List.of());
		mockRagAnswer("Answer.", 0.5);
		stubSuggestionStore(UUID.randomUUID());

		service.query(QUESTION, TEAM_ID, USER_ID);

		ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
		verify(pineconeClient).query(namespaceCaptor.capture(), any(), anyInt(), any(), any());
		assertThat(namespaceCaptor.getValue()).isEqualTo(ORG_ID.toString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void query_intentFiltersApplyUserAndDateRangeToPineconeQuery() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("general_query", "self", "2025-01-06", "2025-01-31", List.of("commit")));
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any(), any())).thenReturn(List.of());
		stubRerankerPassThrough(List.of());
		mockRagAnswer("Answer.", 0.5);
		stubSuggestionStore(UUID.randomUUID());

		service.query(QUESTION, TEAM_ID, USER_ID);

		ArgumentCaptor<Map<String, Object>> filterCaptor = ArgumentCaptor.forClass(Map.class);
		verify(pineconeClient).query(anyString(), any(), eq(80), filterCaptor.capture(), any());
		Map<String, Object> filter = filterCaptor.getValue();
		assertThat(filter.get("teamId")).isEqualTo(TEAM_ID.toString());
		assertThat(filter.get("userId")).isEqualTo(USER_ID.toString());
		// entityType is now filtered by exact string match (single type)
		assertThat(filter.get("entityType")).isEqualTo("commit");
		// date range is now stored as numeric epoch days
		@SuppressWarnings("unchecked")
		Map<String, Object> epochRange = (Map<String, Object>) filter.get("weekStartEpochDay");
		assertThat(epochRange).isNotNull();
		assertThat((Long) epochRange.get("$gte")).isEqualTo(java.time.LocalDate.parse("2025-01-06").toEpochDay());
		assertThat((Long) epochRange.get("$lte")).isEqualTo(java.time.LocalDate.parse("2025-01-31").toEpochDay());
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
		// The service calls the 2-arg experiment-aware overload for answer generation
		when(aiProviderRegistry.generateSuggestion(argOfType(AiContext.TYPE_RAG_QUERY), any()))
				.thenReturn(new AiSuggestionResult(true, payload, "rag answer", confidence, "stub-v1"));
	}

	private void mockPineconeMatches(List<String> ids, List<Double> scores) {
		List<PineconeClient.PineconeMatch> matches = new java.util.ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			matches.add(new PineconeClient.PineconeMatch(ids.get(i), scores.get(i), Map.of()));
		}
		// SemanticQueryService calls the 5-arg hybrid query overload
		when(pineconeClient.query(anyString(), any(), anyInt(), any(), any())).thenReturn(matches);
		// Stub reranker to pass through (default test behaviour)
		stubRerankerPassThrough(matches);
	}

	/**
	 * Stubs the reranker to pass through the given matches as-is (no reordering).
	 * Call this in tests that use a direct Pinecone stub instead of
	 * {@link #mockPineconeMatches}.
	 */
	private void stubRerankerPassThrough(List<PineconeClient.PineconeMatch> matches) {
		when(rerankService.getTopN()).thenReturn(20);
		List<RerankService.RankedChunk> reranked = matches.stream()
				.map(m -> new RerankService.RankedChunk(m.id(), m.score(), m.metadata(), m.score())).toList();
		when(rerankService.rerank(anyString(), any(), anyInt())).thenReturn(reranked);
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

	// ── SQL routing ───────────────────────────────────────────────────────

	@Test
	void query_sqlRouterHandlesAnalyticalIntent_returnsSqlResultEarly() throws Exception {
		setUpAvailableServices();
		// No setUpTeam() — resolveNamespace is never reached when SQL routing returns
		// early
		mockIntentResponse(intentPayload("analytical", "team", null, null, List.of("total", "rate")));
		when(sqlQueryRouter.canHandle(eq("analytical"), any())).thenReturn(true);
		SqlQueryRouter.SqlQueryResult sqlResult = new SqlQueryRouter.SqlQueryResult(true,
				"The carry-forward rate is 18%.", "teamWeekRollups", 0.9, "model-v1");
		when(sqlQueryRouter.query(anyString(), anyString(), any(), any(), any(), any(), any())).thenReturn(sqlResult);

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		assertThat(result.answer()).isEqualTo("The carry-forward rate is 18%.");
		assertThat(result.confidenceTier()).isEqualTo(ConfidenceTier.HIGH);
		// Embedding and Pinecone should NOT be called when SQL router handles the query
		verify(embeddingService, never()).embed(anyString());
		verify(pineconeClient, never()).query(anyString(), any(), anyInt(), any(), any());
	}

	@Test
	void query_sqlRouterReturnsUnavailable_fallsThroughToVectorSearch() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("analytical", "team", null, null, List.of("total")));
		when(sqlQueryRouter.canHandle(eq("analytical"), any())).thenReturn(true);
		when(sqlQueryRouter.query(anyString(), anyString(), any(), any(), any(), any(), any()))
				.thenReturn(SqlQueryRouter.SqlQueryResult.unavailable());
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("Fallback answer.", 0.7);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		// Falls through to vector search
		assertThat(result.available()).isTrue();
		verify(embeddingService).embed(anyString());
	}

	// ── HyDE conditional fallback ────────────────────────────────────────
	// HyDE fires automatically when first-pass retrieval confidence is LOW
	// (Pinecone returned results but scores were weak). It does NOT fire on
	// INSUFFICIENT (zero matches) — a cold/empty index would waste an LLM
	// call with no benefit.

	@Test
	void query_lowConfidence_hydeFiresAndImproves_usesHydeResults() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		String hypothetical = "The team committed to 8 work items with 2 KING priorities.";
		// First-pass returns LOW confidence (matches exist but weak) — triggers HyDE
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(confidenceTierCalculator.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>any()))
				.thenReturn(ConfidenceTier.LOW) // first-pass: poor retrieval
				.thenReturn(ConfidenceTier.MEDIUM); // HyDE-pass: improved
		when(hydeService.generateHypothetical(eq(QUESTION), any()))
				.thenReturn(new HydeService.HydeResult(true, hypothetical));
		when(embeddingService.embed(hypothetical)).thenReturn(new float[]{0.5f, 0.6f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("Answer based on HyDE.", 0.8);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		// First-pass embed with original question, then HyDE retry embed
		verify(embeddingService).embed(QUESTION);
		verify(embeddingService).embed(hypothetical);
		verify(hydeService).generateHypothetical(eq(QUESTION), any());
	}

	@Test
	void query_lowConfidence_hydeFails_keepsOriginalResults() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		// First-pass returns LOW confidence — triggers HyDE; HyDE service itself fails
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(confidenceTierCalculator.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>any()))
				.thenReturn(ConfidenceTier.LOW);
		when(hydeService.generateHypothetical(eq(QUESTION), any())).thenReturn(HydeService.HydeResult.unavailable());
		mockPineconeMatches(List.of("commit:uuid-1"), List.of(0.35));
		mockRagAnswer("Fallback answer.", 0.7);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		// Original embedding was used; HyDE was attempted but gracefully skipped
		verify(embeddingService).embed(QUESTION);
		verify(hydeService).generateHypothetical(eq(QUESTION), any());
		// HyDE embedding never called because HyDE service was unavailable
		// (only the original QUESTION embed should have fired)
	}

	@Test
	void query_lowConfidence_hydeDoesNotImprove_keepsOriginalResults() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		String hypothetical = "Hypothetical that does not improve retrieval.";
		// Both first-pass and HyDE-pass return LOW — HyDE ran but did not help
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(embeddingService.embed(hypothetical)).thenReturn(new float[]{0.2f});
		when(confidenceTierCalculator.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>any()))
				.thenReturn(ConfidenceTier.LOW) // first-pass: poor retrieval
				.thenReturn(ConfidenceTier.LOW); // HyDE-pass: no improvement
		when(hydeService.generateHypothetical(eq(QUESTION), any()))
				.thenReturn(new HydeService.HydeResult(true, hypothetical));
		mockPineconeMatches(List.of("commit:uuid-1"), List.of(0.35));
		mockRagAnswer("Original answer.", 0.6);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		// HyDE was attempted but original results were kept because confidence did not
		// improve
		verify(hydeService).generateHypothetical(eq(QUESTION), any());
		verify(embeddingService).embed(hypothetical); // HyDE embed did fire
	}

	@Test
	void query_insufficientConfidence_hydeSkipped_coldIndexGuard() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		// INSUFFICIENT = zero matches from Pinecone (empty/cold index). HyDE must NOT
		// fire — embedding a hypothetical against an empty index wastes an LLM call.
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		when(confidenceTierCalculator.calculate(org.mockito.ArgumentMatchers.<List<PineconeClient.PineconeMatch>>any()))
				.thenReturn(ConfidenceTier.INSUFFICIENT);
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("No evidence answer.", 0.3);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		// HyDE must never be called when index returns no matches
		verify(hydeService, never()).generateHypothetical(anyString(), any());
	}

	@Test
	void query_mediumOrHighConfidence_hydeNotTriggered() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		mockIntentResponse(intentPayload("status_query", null, null, null, List.of("commit")));
		// Default confidenceTierCalculator mock returns MEDIUM — HyDE should be skipped
		when(embeddingService.embed(QUESTION)).thenReturn(new float[]{0.1f});
		mockPineconeMatches(List.of(), List.of());
		mockRagAnswer("Normal answer.", 0.8);
		stubSuggestionStore(UUID.randomUUID());

		SemanticQueryService.RagQueryResult result = service.query(QUESTION, TEAM_ID, USER_ID);

		assertThat(result.available()).isTrue();
		verify(embeddingService).embed(QUESTION);
		verify(hydeService, never()).generateHypothetical(anyString(), any());
	}
}
