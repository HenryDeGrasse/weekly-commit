package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RerankService}.
 *
 * <p>
 * Tests the three reranking paths: pass-through (disabled), LLM-based, and
 * Cohere. All HTTP calls are intercepted via a mock {@link HttpClient}.
 */
@ExtendWith(MockitoExtension.class)
class RerankServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private AiProviderRegistry aiProviderRegistry;

	@Mock
	private HttpClient httpClient;

	@Mock
	@SuppressWarnings("unchecked")
	private HttpResponse<String> httpResponse;

	// ── Feature flag disabled → pass-through ────────────────────────────

	@Test
	void rerank_disabled_returnsTruncatedByPineconeScore() {
		RerankService service = makeService(false, 2, "");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("a", 0.9, Map.of()),
				new PineconeClient.PineconeMatch("b", 0.8, Map.of()),
				new PineconeClient.PineconeMatch("c", 0.7, Map.of()));

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 2);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("a");
		assertThat(result.get(1).id()).isEqualTo("b");
		// No HTTP or LLM calls when disabled
		verify(aiProviderRegistry, never()).generateSuggestion(any());
	}

	@Test
	void rerank_disabled_topNGreaterThanCandidates_returnsAll() {
		RerankService service = makeService(false, 10, "");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("a", 0.9, Map.of()),
				new PineconeClient.PineconeMatch("b", 0.8, Map.of()));

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 10);

		assertThat(result).hasSize(2);
	}

	@Test
	void rerank_disabled_passThroughUsesOriginalScoreAsRerankScore() {
		RerankService service = makeService(false, 5, "");

		List<PineconeClient.PineconeMatch> candidates = List
				.of(new PineconeClient.PineconeMatch("doc-1", 0.77, Map.of()));

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 5);

		assertThat(result.get(0).rerankScore()).isEqualTo(0.77);
	}

	// ── Empty candidates ─────────────────────────────────────────────────

	@Test
	void rerank_emptyCandidates_returnsEmptyList() {
		RerankService service = makeService(false, 5, "");

		List<RerankService.RankedChunk> result = service.rerank("query", List.of(), 5);

		assertThat(result).isEmpty();
	}

	@Test
	void rerank_nullCandidates_returnsEmptyList() {
		RerankService service = makeService(false, 5, "");

		List<RerankService.RankedChunk> result = service.rerank("query", null, 5);

		assertThat(result).isEmpty();
	}

	// ── LLM-based reranking (enabled, no Cohere key) ────────────────────

	@Test
	void rerank_llmEnabled_reordersChunksByScore() throws Exception {
		RerankService service = makeService(true, 3, "");

		List<PineconeClient.PineconeMatch> candidates = List.of(
				new PineconeClient.PineconeMatch("low", 0.5, Map.of("text", "unrelated document")),
				new PineconeClient.PineconeMatch("high", 0.6, Map.of("text", "very relevant document")),
				new PineconeClient.PineconeMatch("mid", 0.55, Map.of("text", "somewhat relevant")));

		// LLM scores chunk at index 1 highest
		String llmPayload = """
				{"rankings": [
				  {"chunkIndex": 0, "score": 0.1},
				  {"chunkIndex": 1, "score": 0.95},
				  {"chunkIndex": 2, "score": 0.5}
				]}
				""";
		when(aiProviderRegistry.generateSuggestion(any()))
				.thenReturn(new AiSuggestionResult(true, llmPayload, "rerank", 0.9, "stub-v1"));

		List<RerankService.RankedChunk> result = service.rerank("relevant question", candidates, 3);

		assertThat(result).hasSize(3);
		// Must be sorted by rerank score descending
		assertThat(result.get(0).id()).isEqualTo("high");
		assertThat(result.get(0).rerankScore()).isEqualTo(0.95);
		assertThat(result.get(1).id()).isEqualTo("mid");
		assertThat(result.get(2).id()).isEqualTo("low");
	}

	@Test
	void rerank_llmEnabled_truncatesToTopN() throws Exception {
		RerankService service = makeService(true, 2, "");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("a", 0.9, Map.of()),
				new PineconeClient.PineconeMatch("b", 0.8, Map.of()),
				new PineconeClient.PineconeMatch("c", 0.7, Map.of()));

		String llmPayload = """
				{"rankings": [
				  {"chunkIndex": 0, "score": 0.3},
				  {"chunkIndex": 1, "score": 0.9},
				  {"chunkIndex": 2, "score": 0.6}
				]}
				""";
		when(aiProviderRegistry.generateSuggestion(any()))
				.thenReturn(new AiSuggestionResult(true, llmPayload, "rerank", 0.9, "stub-v1"));

		List<RerankService.RankedChunk> result = service.rerank("question", candidates, 2);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("b"); // score 0.9
		assertThat(result.get(1).id()).isEqualTo("c"); // score 0.6
	}

	// ── Graceful fallback on failure ─────────────────────────────────────

	@Test
	void rerank_llmReturnsUnavailable_fallsBackToPassThrough() {
		RerankService service = makeService(true, 2, "");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("x", 0.9, Map.of()),
				new PineconeClient.PineconeMatch("y", 0.7, Map.of()),
				new PineconeClient.PineconeMatch("z", 0.5, Map.of()));

		when(aiProviderRegistry.generateSuggestion(any())).thenReturn(AiSuggestionResult.unavailable());

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 2);

		// Falls back to pass-through: top-2 by original score
		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("x");
		assertThat(result.get(1).id()).isEqualTo("y");
	}

	@Test
	void rerank_llmReturnsMalformedJson_fallsBackToPassThrough() {
		RerankService service = makeService(true, 2, "");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("a", 0.9, Map.of()),
				new PineconeClient.PineconeMatch("b", 0.8, Map.of()));

		when(aiProviderRegistry.generateSuggestion(any()))
				.thenReturn(new AiSuggestionResult(true, "{\"unexpected\":\"shape\"}", "rerank", 0.5, "stub-v1"));

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 2);

		// Falls back to pass-through
		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("a");
	}

	// ── Cohere reranking (enabled + api key) ─────────────────────────────

	@Test
	void rerank_cohereEnabled_reordersChunksByRelevanceScore() throws Exception {
		RerankService service = makeService(true, 2, "cohere-test-key");

		List<PineconeClient.PineconeMatch> candidates = List.of(
				new PineconeClient.PineconeMatch("doc-low", 0.5, Map.of("text", "unrelated")),
				new PineconeClient.PineconeMatch("doc-high", 0.6, Map.of("text", "highly relevant")));

		// Cohere returns: index 1 is most relevant
		String cohereResp = """
				{"results": [
				  {"index": 1, "relevance_score": 0.97},
				  {"index": 0, "relevance_score": 0.12}
				]}
				""";
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(cohereResp);
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 2);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("doc-high");
		assertThat(result.get(0).rerankScore()).isEqualTo(0.97);
		assertThat(result.get(1).id()).isEqualTo("doc-low");
		// No LLM call was made
		verify(aiProviderRegistry, never()).generateSuggestion(any());
	}

	@Test
	void rerank_cohereHttpError_fallsBackToPassThrough() throws Exception {
		RerankService service = makeService(true, 2, "cohere-test-key");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("a", 0.9, Map.of()),
				new PineconeClient.PineconeMatch("b", 0.7, Map.of()),
				new PineconeClient.PineconeMatch("c", 0.5, Map.of()));

		when(httpResponse.statusCode()).thenReturn(429);
		when(httpResponse.body()).thenReturn("{\"error\":\"rate limit\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 2);

		// Falls back to pass-through
		assertThat(result).hasSize(2);
		assertThat(result.get(0).id()).isEqualTo("a");
	}

	@Test
	void rerank_cohereNetworkError_fallsBackToPassThrough() throws Exception {
		RerankService service = makeService(true, 2, "cohere-test-key");

		List<PineconeClient.PineconeMatch> candidates = List.of(new PineconeClient.PineconeMatch("a", 0.9, Map.of()));
		when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

		List<RerankService.RankedChunk> result = service.rerank("query", candidates, 1);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).id()).isEqualTo("a");
	}

	// ── getTopN ──────────────────────────────────────────────────────────

	@Test
	void getTopN_returnsConfiguredValue() {
		RerankService service = makeService(false, 15, "");
		assertThat(service.getTopN()).isEqualTo(15);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private RerankService makeService(boolean enabled, int topN, String cohereKey) {
		return new RerankService(enabled, topN, cohereKey, aiProviderRegistry, objectMapper, httpClient);
	}
}
