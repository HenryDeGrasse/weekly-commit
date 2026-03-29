package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cross-encoder reranking service for the RAG pipeline.
 *
 * <p>
 * Sits between Pinecone retrieval and LLM context assembly. Takes a larger
 * candidate set (e.g. top-80 from Pinecone) and re-scores it to produce a
 * smaller, higher-quality top-N list for the answer generation step.
 *
 * <p>
 * Implementation priority (in order):
 * <ol>
 * <li><b>Disabled (default)</b> — when {@code ai.rerank.enabled=false} (the
 * default), reranking is skipped entirely. The top-N candidates are returned by
 * truncating the Pinecone result list by score, which is the same behaviour as
 * before reranking was added. Teams opt-in once a Cohere API key is
 * configured.</li>
 * <li><b>Cohere Rerank API</b> — preferred production path when
 * {@code ai.rerank.api-key} is set. Minimal latency overhead (~200 ms).</li>
 * <li><b>LLM-based batch reranking</b> — last-resort development/testing
 * fallback when Cohere is unavailable but reranking is explicitly enabled via
 * {@code ai.rerank.enabled=true}. This adds a third LLM call per RAG query
 * (intent + answer + rerank) and can add 5–30 s of latency. Do NOT enable in
 * production without a Cohere key.</li>
 * </ol>
 *
 * <p>
 * Graceful degradation: any failure in reranking falls back to the original
 * candidate list truncated to top-N.
 */
@Service
public class RerankService {

	private static final Logger log = LoggerFactory.getLogger(RerankService.class);

	private static final String COHERE_RERANK_URL = "https://api.cohere.ai/v1/rerank";
	private static final String COHERE_DEFAULT_MODEL = "rerank-english-v3.0";

	/**
	 * Maximum chunks to send per LLM-based rerank call to avoid massive prompts.
	 */
	private static final int LLM_RERANK_MAX_CHUNKS = 40;

	/** Suggestion type string for the LLM rerank batch call. */
	private static final String TYPE_RAG_RERANK = "RAG_RERANK";

	/**
	 * A chunk returned by the reranker, carrying both its original Pinecone score
	 * and its computed rerank score.
	 */
	public record RankedChunk(String id, double score, Map<String, Object> metadata, double rerankScore) {
	}

	// ── Configuration ─────────────────────────────────────────────────────

	private final boolean enabled;
	private final int topN;
	private final String cohereApiKey;

	// ── Dependencies ──────────────────────────────────────────────────────

	private final AiProviderRegistry aiProviderRegistry;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	// ── Constructors ─────────────────────────────────────────────────────

	/**
	 * Spring-managed constructor.
	 *
	 * <p>
	 * Defaults: {@code ai.rerank.enabled=false} (opt-in),
	 * {@code ai.rerank.top-n=20}, {@code ai.rerank.api-key} empty (Cohere
	 * disabled).
	 */
	@Autowired
	public RerankService(@Value("${ai.rerank.enabled:false}") boolean enabled, @Value("${ai.rerank.top-n:20}") int topN,
			@Value("${ai.rerank.api-key:}") String cohereApiKey, AiProviderRegistry aiProviderRegistry,
			ObjectMapper objectMapper) {
		this(enabled, topN, cohereApiKey, aiProviderRegistry, objectMapper,
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		if (enabled) {
			if (cohereApiKey != null && !cohereApiKey.isBlank()) {
				log.info("RerankService: Cohere reranking enabled (top-n={})", topN);
			} else {
				log.warn("RerankService: LLM-based reranking enabled (top-n={}) — this adds ~5-30 s latency per RAG "
						+ "query. Configure ai.rerank.api-key for Cohere Rerank instead.", topN);
			}
		} else {
			log.debug("RerankService: reranking disabled (pass-through mode)");
		}
	}

	/**
	 * Package-private constructor for unit tests — accepts a mock
	 * {@link HttpClient} so no real HTTP calls are made.
	 */
	RerankService(boolean enabled, int topN, String cohereApiKey, AiProviderRegistry aiProviderRegistry,
			ObjectMapper objectMapper, HttpClient httpClient) {
		this.enabled = enabled;
		this.topN = topN;
		this.cohereApiKey = cohereApiKey;
		this.aiProviderRegistry = aiProviderRegistry;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
	}

	// ── Public API ────────────────────────────────────────────────────────

	/** Returns the configured top-N value (used by callers to size the result). */
	public int getTopN() {
		return topN;
	}

	/**
	 * Reranks the candidate chunks against the query and returns the
	 * top-{@code topN} results.
	 *
	 * <p>
	 * When reranking is disabled, this method truncates {@code candidates} to
	 * {@code topN} by their Pinecone score (pass-through). When reranking is
	 * enabled and fails for any reason, the same pass-through behaviour is applied
	 * and a warning is logged.
	 *
	 * @param query
	 *            the user's original question
	 * @param candidates
	 *            Pinecone retrieval results to rerank
	 * @param topN
	 *            maximum number of results to return
	 * @return reranked list capped at {@code topN}, never {@code null}
	 */
	public List<RankedChunk> rerank(String query, List<PineconeClient.PineconeMatch> candidates, int topN) {
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}
		if (!enabled) {
			return passThrough(candidates, topN);
		}
		try {
			if (cohereApiKey != null && !cohereApiKey.isBlank()) {
				return rerankWithCohere(query, candidates, topN);
			} else {
				return rerankWithLlm(query, candidates, topN);
			}
		} catch (Exception e) {
			log.warn("RerankService: reranking failed — falling back to pass-through (top-{}): {}", topN,
					e.getMessage());
			return passThrough(candidates, topN);
		}
	}

	// ── Private: pass-through ────────────────────────────────────────────

	private List<RankedChunk> passThrough(List<PineconeClient.PineconeMatch> candidates, int topN) {
		return candidates.stream().limit(topN).map(m -> new RankedChunk(m.id(), m.score(), m.metadata(), m.score()))
				.toList();
	}

	// ── Private: Cohere Rerank API ───────────────────────────────────────

	/**
	 * Calls the Cohere Rerank API to score candidates against the query.
	 *
	 * <p>
	 * Sends all candidates to Cohere (which handles large lists efficiently) and
	 * maps the ranked results back to {@link RankedChunk} instances.
	 */
	private List<RankedChunk> rerankWithCohere(String query, List<PineconeClient.PineconeMatch> candidates, int topN)
			throws Exception {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("query", query);
		body.put("model", COHERE_DEFAULT_MODEL);
		body.put("top_n", topN);
		body.put("return_documents", false);

		ArrayNode docs = body.putArray("documents");
		for (PineconeClient.PineconeMatch m : candidates) {
			String text = extractText(m);
			docs.add(text);
		}

		String json = objectMapper.writeValueAsString(body);
		HttpRequest req = HttpRequest.newBuilder().uri(URI.create(COHERE_RERANK_URL))
				.header("Authorization", "Bearer " + cohereApiKey).header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			throw new IllegalStateException("Cohere API returned " + resp.statusCode() + ": " + resp.body());
		}

		JsonNode root = objectMapper.readTree(resp.body());
		JsonNode results = root.get("results");
		if (results == null || !results.isArray()) {
			throw new IllegalStateException("Cohere response missing 'results' array");
		}

		List<RankedChunk> ranked = new ArrayList<>(results.size());
		for (JsonNode result : results) {
			int index = result.path("index").asInt(-1);
			double score = result.path("relevance_score").asDouble(0.0);
			if (index >= 0 && index < candidates.size()) {
				PineconeClient.PineconeMatch original = candidates.get(index);
				ranked.add(new RankedChunk(original.id(), original.score(), original.metadata(), score));
			}
		}

		log.debug("RerankService: Cohere reranked {} → {} candidates", candidates.size(), ranked.size());
		return Collections.unmodifiableList(ranked);
	}

	// ── Private: LLM-based batch reranking ──────────────────────────────

	/**
	 * Uses the LLM as a last-resort batch reranker.
	 *
	 * <p>
	 * Sends up to {@value #LLM_RERANK_MAX_CHUNKS} chunks in a single LLM call and
	 * asks it to return relevance scores as a JSON array. This is ONE LLM call
	 * regardless of the number of chunks. However, it adds significant latency
	 * (typically 5–30 s) compared to Cohere (~200 ms).
	 */
	private List<RankedChunk> rerankWithLlm(String query, List<PineconeClient.PineconeMatch> candidates, int topN)
			throws Exception {
		// Safety cap to keep the prompt manageable
		List<PineconeClient.PineconeMatch> batch = candidates.size() > LLM_RERANK_MAX_CHUNKS
				? candidates.subList(0, LLM_RERANK_MAX_CHUNKS)
				: candidates;

		// Build document list for the prompt
		List<Map<String, Object>> documents = new ArrayList<>(batch.size());
		for (int i = 0; i < batch.size(); i++) {
			documents.add(Map.of("index", i, "text", extractText(batch.get(i))));
		}

		Map<String, Object> additionalContext = Map.of("task",
				"Relevance ranking: rate each document's relevance to the query", "instruction",
				"Return ONLY valid JSON: {\"rankings\": [{\"chunkIndex\": 0, \"score\": 0.0}, ...]}", "query", query,
				"documents", documents);

		AiContext ctx = new AiContext(TYPE_RAG_RERANK, null, null, null, Map.of(), Map.of(), List.of(), List.of(),
				additionalContext);
		AiSuggestionResult result = aiProviderRegistry.generateSuggestion(ctx);

		if (!result.available()) {
			throw new IllegalStateException("LLM rerank call returned unavailable");
		}

		// Parse ranking scores from the LLM response
		JsonNode root = objectMapper.readTree(result.payload());
		JsonNode rankingsNode = root.get("rankings");
		if (rankingsNode == null || !rankingsNode.isArray()) {
			throw new IllegalStateException("LLM rerank response missing 'rankings' array");
		}

		// Build a score map from chunkIndex → score
		Map<Integer, Double> scores = new java.util.HashMap<>();
		for (JsonNode rn : rankingsNode) {
			int idx = rn.path("chunkIndex").asInt(-1);
			double score = rn.path("score").asDouble(0.0);
			if (idx >= 0) {
				scores.put(idx, score);
			}
		}

		// Map scores back to candidates, sort by score descending, take topN
		List<RankedChunk> ranked = new ArrayList<>(batch.size());
		for (int i = 0; i < batch.size(); i++) {
			PineconeClient.PineconeMatch m = batch.get(i);
			double rerankScore = scores.getOrDefault(i, 0.0);
			ranked.add(new RankedChunk(m.id(), m.score(), m.metadata(), rerankScore));
		}
		ranked.sort(Comparator.comparingDouble(RankedChunk::rerankScore).reversed());

		List<RankedChunk> result2 = ranked.stream().limit(topN).toList();
		log.debug("RerankService: LLM reranked {} → {} candidates", batch.size(), result2.size());
		return result2;
	}

	// ── Private helpers ──────────────────────────────────────────────────

	/**
	 * Extracts a text representation from a Pinecone match for use as a reranking
	 * document. Tries the {@code "text"} metadata field first (set by
	 * {@code SemanticIndexService.upsertChunk()}), falling back to the chunk ID.
	 */
	private String extractText(PineconeClient.PineconeMatch match) {
		if (match.metadata() != null) {
			Object text = match.metadata().get("text");
			if (text instanceof String s && !s.isBlank()) {
				// Truncate very long texts to keep request sizes reasonable
				return s.length() > 500 ? s.substring(0, 500) : s;
			}
		}
		return match.id();
	}
}
