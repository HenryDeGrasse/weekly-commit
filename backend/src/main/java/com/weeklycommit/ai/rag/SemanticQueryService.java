package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.evidence.ConfidenceTierCalculator;
import com.weeklycommit.ai.evidence.ConfidenceTierCalculator.ConfidenceTier;
import com.weeklycommit.ai.experiment.ExperimentService;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.repository.TeamRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles semantic (RAG) queries: embeds the question, retrieves relevant
 * chunks from Pinecone, and synthesises an answer via the LLM.
 *
 * <p>
 * Pipeline:
 * <ol>
 * <li>Intent classification — extracts entity-type filters and time-range from
 * the natural-language question.</li>
 * <li>Embedding — converts the question to a dense vector.</li>
 * <li>Pinecone retrieval — nearest-neighbour search with metadata filters.</li>
 * <li>Answer generation — packs retrieved chunks into a RAG prompt and calls
 * the LLM.</li>
 * <li>Audit — persists the interaction via {@link AiSuggestionService}.</li>
 * </ol>
 *
 * <p>
 * All steps degrade gracefully: if any upstream service is unavailable, the
 * method returns an unavailable {@link RagQueryResult} without throwing.
 */
@Service
public class SemanticQueryService {

	private static final Logger log = LoggerFactory.getLogger(SemanticQueryService.class);
	/**
	 * Retrieve more candidates from Pinecone; the reranker filters down to top-N.
	 */
	private static final int TOP_K = 80;

	// ── Inner records ─────────────────────────────────────────────────────

	/**
	 * A single source item that contributed to a RAG answer.
	 *
	 * @param entityType
	 *            e.g. {@code "commit"}, {@code "plan_summary"}
	 * @param entityId
	 *            the entity UUID string
	 * @param weekStartDate
	 *            ISO week-start date extracted from vector metadata (may be empty)
	 * @param snippet
	 *            brief excerpt from the chunk text
	 */
	public record RagSource(String entityType, String entityId, String weekStartDate, String snippet) {
	}

	/**
	 * Result of a RAG query.
	 *
	 * @param available
	 *            {@code false} if any upstream dependency is unavailable
	 * @param answer
	 *            synthesised natural-language answer (null when unavailable)
	 * @param sources
	 *            retrieved chunks that informed the answer
	 * @param confidence
	 *            model confidence [0.0, 1.0]
	 * @param suggestionId
	 *            ID of the persisted
	 *            {@link com.weeklycommit.domain.entity.AiSuggestion} audit record
	 *            (null when unavailable)
	 */
	public record RagQueryResult(boolean available, String answer, List<RagSource> sources, double confidence,
			UUID suggestionId, ConfidenceTier confidenceTier) {

		/** Convenience factory for a degraded/unavailable result. */
		public static RagQueryResult unavailable() {
			return new RagQueryResult(false, null, List.of(), 0.0, null, ConfidenceTier.INSUFFICIENT);
		}
	}

	// ── Dependencies ──────────────────────────────────────────────────────

	private final PineconeClient pineconeClient;
	private final EmbeddingService embeddingService;
	private final AiProviderRegistry aiProviderRegistry;
	private final AiSuggestionService aiSuggestionService;
	private final ObjectMapper objectMapper;
	private final TeamRepository teamRepo;
	private final SparseEncoder sparseEncoder;
	private final RerankService rerankService;
	private final ConfidenceTierCalculator confidenceTierCalculator;
	private final QueryRewriter queryRewriter;
	private final ExperimentService experimentService;
	private final HydeService hydeService;
	private final SqlQueryRouter sqlQueryRouter;

	public SemanticQueryService(PineconeClient pineconeClient, EmbeddingService embeddingService,
			AiProviderRegistry aiProviderRegistry, AiSuggestionService aiSuggestionService, ObjectMapper objectMapper,
			TeamRepository teamRepo, SparseEncoder sparseEncoder, RerankService rerankService,
			ConfidenceTierCalculator confidenceTierCalculator, QueryRewriter queryRewriter,
			ExperimentService experimentService, HydeService hydeService, SqlQueryRouter sqlQueryRouter) {
		this.pineconeClient = pineconeClient;
		this.embeddingService = embeddingService;
		this.aiProviderRegistry = aiProviderRegistry;
		this.aiSuggestionService = aiSuggestionService;
		this.objectMapper = objectMapper;
		this.teamRepo = teamRepo;
		this.sparseEncoder = sparseEncoder;
		this.rerankService = rerankService;
		this.confidenceTierCalculator = confidenceTierCalculator;
		this.queryRewriter = queryRewriter;
		this.experimentService = experimentService;
		this.hydeService = hydeService;
		this.sqlQueryRouter = sqlQueryRouter;
	}

	// ── Public API ────────────────────────────────────────────────────────

	/**
	 * Executes a RAG query for the given question scoped to a team.
	 *
	 * @param question
	 *            natural-language question from the user
	 * @param teamId
	 *            team scope for Pinecone namespace + metadata filter
	 * @param userId
	 *            requesting user (for audit)
	 * @return query result, never {@code null}; check
	 *         {@link RagQueryResult#available()}
	 */
	public RagQueryResult query(String question, UUID teamId, UUID userId) {
		if (!pineconeClient.isAvailable() || !embeddingService.isAvailable()) {
			log.debug("SemanticQueryService: unavailable (pinecone={}, embedding={})", pineconeClient.isAvailable(),
					embeddingService.isAvailable());
			return RagQueryResult.unavailable();
		}
		if (question == null || question.isBlank()) {
			return RagQueryResult.unavailable();
		}

		try {
			// Step 1a: rewrite the question for better embedding quality
			String rewritten = queryRewriter.rewrite(question);

			// Step 1b: decompose into sub-queries (detects multi-hop questions)
			List<String> subQueries = queryRewriter.decompose(rewritten);

			// Step 2: intent classification (uses the full rewritten question)
			IntentResult intent = classifyIntent(rewritten, teamId, userId);

			// Step 2.5: SQL routing — prefer read-model aggregate queries over vector
			// search
			if (sqlQueryRouter.canHandle(intent.intent(), intent.keywords())) {
				SqlQueryRouter.SqlQueryResult sqlResult = sqlQueryRouter.query(rewritten, intent.intent(),
						intent.keywords(), teamId, intent.timeRangeFrom(), intent.timeRangeTo(), userId);
				if (sqlResult.available()) {
					log.debug("SemanticQueryService: SQL route handled analytical query — skipping vector search");
					return new RagQueryResult(true, sqlResult.answer(), List.of(), sqlResult.confidence(), null,
							ConfidenceTier.HIGH);
				}
				log.debug("SemanticQueryService: SQL route unsuccessful — falling through to vector search");
			}

			// Step 3: build metadata filter
			Map<String, Object> filter = buildFilter(intent, teamId, userId);

			// Step 4: resolve namespace
			String namespace = resolveNamespace(teamId);

			// Step 4.5: HyDE experiment — optionally replace embed text with a
			// hypothetical document for improved semantic retrieval
			String textToEmbed = rewritten;
			if (experimentService.isEnabled("hyde")) {
				String hydeValue = experimentService.resolveValue("hyde", userId != null ? userId.toString() : "");
				if ("enabled".equals(hydeValue)) {
					HydeService.HydeResult hydeResult = hydeService.generateHypothetical(rewritten, userId);
					if (hydeResult.available() && hydeResult.hypotheticalAnswer() != null) {
						textToEmbed = hydeResult.hypotheticalAnswer();
						log.debug("SemanticQueryService: HyDE active — embedding hypothetical document");
					}
				}
			}

			// Step 4.6: embedding-model A/B — determine whether to route to the Voyage
			// index for this request. Treatment group gets voyage-4 embeddings queried
			// against the secondary 1024-d index; control gets the existing path.
			boolean useVoyageIndex = false;
			if (experimentService.isEnabled("embedding-model") && embeddingService.isVoyageAvailable()) {
				String embeddingValue = experimentService.resolveValue("embedding-model",
						userId != null ? userId.toString() : "anon");
				useVoyageIndex = "voyage-4".equals(embeddingValue);
				if (useVoyageIndex) {
					log.debug("SemanticQueryService: embedding-model experiment treatment — voyage-4 + voyage index");
				}
			}

			// Step 5: embed and query Pinecone — single query or multi-hop
			List<PineconeClient.PineconeMatch> matches;
			if (subQueries.size() <= 1) {
				if (useVoyageIndex) {
					// Voyage path: dense-only (no sparse vectors in the voyage index)
					float[] vector = embeddingService.embed(textToEmbed, "voyage-4");
					if (vector.length == 0) {
						log.debug("SemanticQueryService: voyage embedding returned empty vector — unavailable");
						return RagQueryResult.unavailable();
					}
					matches = pineconeClient.queryVoyage(namespace, vector, TOP_K, filter);
				} else {
					// Primary path: hybrid dense+sparse
					float[] vector = embeddingService.embed(textToEmbed);
					if (vector.length == 0) {
						log.debug("SemanticQueryService: embedding returned empty vector — unavailable");
						return RagQueryResult.unavailable();
					}
					java.util.Map<Integer, Float> sparseVector = null;
					try {
						java.util.Map<Integer, Float> sv = sparseEncoder.encode(textToEmbed);
						if (!sv.isEmpty()) {
							sparseVector = sv;
						}
					} catch (Exception ex) {
						log.debug("SemanticQueryService: sparse encoding failed — falling back to dense-only: {}",
								ex.getMessage());
					}
					matches = pineconeClient.query(namespace, vector, TOP_K, filter, sparseVector);
				}
			} else {
				// Multi-hop path: embed each sub-query, merge results
				matches = queryMultiHop(subQueries, namespace, filter, useVoyageIndex);
				if (matches == null) {
					return RagQueryResult.unavailable();
				}
			}

			// Step 5b: rerank candidates to find the most relevant top-N
			List<RerankService.RankedChunk> reranked = rerankService.rerank(rewritten, matches,
					rerankService.getTopN());

			// Step 5c: compute evidence-based confidence tier from the reranked chunks
			ConfidenceTier confidenceTier = confidenceTierCalculator.calculate(toConfidenceMatches(reranked));

			// Step 6 & 7: build RAG prompt context from reranked results and generate
			// answer using experiment-aware model selection
			String contextString = buildContextString(rewritten, matches);
			AiContext ragContext = new AiContext(AiContext.TYPE_RAG_QUERY, userId, null, null, Map.of(), Map.of(),
					List.of(), List.of(), buildRagAdditionalContextReranked(rewritten, reranked));
			AiSuggestionResult llmResult = aiProviderRegistry.generateSuggestion(ragContext, userId);

			// Record experiment assignments in audit context string
			if (llmResult.available() && !llmResult.experimentAssignments().isEmpty()) {
				contextString = enrichContextWithExperiments(contextString, llmResult.experimentAssignments());
			}

			// Step 7: parse the answer
			RagAnswer ragAnswer = parseRagAnswer(llmResult);

			// Step 8: persist audit record
			com.weeklycommit.domain.entity.AiSuggestion stored = null;
			try {
				stored = aiSuggestionService.storeSuggestion(AiContext.TYPE_RAG_QUERY, userId, null, null,
						contextString, llmResult);
			} catch (Exception ex) {
				log.warn("SemanticQueryService: failed to store suggestion audit record — {}", ex.getMessage());
			}

			// Step 9: return result
			UUID suggestionId = stored != null ? stored.getId() : null;
			return new RagQueryResult(true, ragAnswer.answer(), ragAnswer.sources(), ragAnswer.confidence(),
					suggestionId, confidenceTier);

		} catch (Exception e) {
			log.warn("SemanticQueryService: query failed — {}", e.getMessage());
			return RagQueryResult.unavailable();
		}
	}

	// ── Private pipeline steps ────────────────────────────────────────────

	/**
	 * Calls the LLM for intent classification. Returns safe defaults if the LLM
	 * call fails or returns unparseable JSON.
	 */
	private IntentResult classifyIntent(String question, UUID teamId, UUID userId) {
		try {
			String today = LocalDate.now().toString();
			// Last Monday = the previous Monday relative to today
			LocalDate todayDate = LocalDate.now();
			LocalDate lastMonday = todayDate.with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
			String lastWeekFrom = lastMonday.toString();
			String lastWeekTo = lastMonday.plusDays(6).toString();
			String thisWeekFrom = todayDate.with(java.time.DayOfWeek.MONDAY).toString();
			Map<String, Object> additionalCtx = new HashMap<>();
			additionalCtx.put("question", question);
			additionalCtx.put("teamId", teamId != null ? teamId.toString() : "");
			additionalCtx.put("currentDate", today);
			additionalCtx.put("lastWeekFrom", lastWeekFrom);
			additionalCtx.put("lastWeekTo", lastWeekTo);
			additionalCtx.put("thisWeekFrom", thisWeekFrom);
			AiContext intentContext = new AiContext(AiContext.TYPE_RAG_INTENT, userId, null, null, Map.of(), Map.of(),
					List.of(), List.of(), additionalCtx);
			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(intentContext);
			if (!result.available()) {
				return IntentResult.defaults();
			}
			return parseIntentResult(result.payload());
		} catch (Exception e) {
			log.debug("SemanticQueryService: intent classification failed — {}", e.getMessage());
			return IntentResult.defaults();
		}
	}

	private IntentResult parseIntentResult(String payload) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			List<String> entityTypes = new ArrayList<>();
			JsonNode et = root.path("entityTypes");
			if (et.isArray()) {
				for (JsonNode node : et) {
					entityTypes.add(node.asText());
				}
			}
			List<String> keywords = new ArrayList<>();
			JsonNode kw = root.path("keywords");
			if (kw.isArray()) {
				for (JsonNode node : kw) {
					keywords.add(node.asText());
				}
			}
			JsonNode timeRange = root.path("timeRange");
			String from = timeRange.isObject() ? timeRange.path("from").asText(null) : null;
			String to = timeRange.isObject() ? timeRange.path("to").asText(null) : null;
			String intent = root.path("intent").asText("");
			String userFilter = root.path("userFilter").isMissingNode() || root.path("userFilter").isNull()
					? null
					: root.path("userFilter").asText(null);
			return new IntentResult(intent, userFilter, entityTypes, from, to, keywords);
		} catch (Exception e) {
			log.debug("SemanticQueryService: could not parse intent — using defaults");
			return IntentResult.defaults();
		}
	}

	private Map<String, Object> buildFilter(IntentResult intent, UUID teamId, UUID userId) {
		Map<String, Object> filter = new HashMap<>();
		if (teamId != null) {
			filter.put("teamId", teamId.toString());
		}
		if (intent.userFilter() != null && userId != null
				&& ("self".equalsIgnoreCase(intent.userFilter()) || "me".equalsIgnoreCase(intent.userFilter()))) {
			filter.put("userId", userId.toString());
		}
		// Entity type filter: use $in for multi-type queries.
		if (intent.entityTypes() != null && !intent.entityTypes().isEmpty()) {
			if (intent.entityTypes().size() == 1) {
				filter.put("entityType", intent.entityTypes().get(0));
			} else {
				filter.put("entityType", Map.of("$in", intent.entityTypes()));
			}
		}
		// Date range filter using the numeric weekStartEpochDay field.
		// ISO date strings ("YYYY-MM-DD") are not supported by Pinecone's $gte/$lte,
		// but the equivalent epoch-day long is stored alongside each chunk.
		Map<String, Object> dateRange = new HashMap<>();
		if (intent.timeRangeFrom() != null && !intent.timeRangeFrom().isBlank()) {
			Long epochFrom = ChunkBuilder.toEpochDay(intent.timeRangeFrom());
			if (epochFrom != null)
				dateRange.put("$gte", epochFrom);
		}
		if (intent.timeRangeTo() != null && !intent.timeRangeTo().isBlank()) {
			Long epochTo = ChunkBuilder.toEpochDay(intent.timeRangeTo());
			if (epochTo != null)
				dateRange.put("$lte", epochTo);
		}
		if (!dateRange.isEmpty()) {
			filter.put("weekStartEpochDay", dateRange);
		}
		return filter.isEmpty() ? null : filter;
	}

	private String resolveNamespace(UUID teamId) {
		if (teamId == null) {
			return "";
		}
		return teamRepo.findById(teamId).map(Team::getOrganizationId)
				.map(orgId -> orgId != null ? orgId.toString() : "").orElse("");
	}

	private String buildContextString(String question, List<PineconeClient.PineconeMatch> matches) {
		try {
			Map<String, Object> ctx = new HashMap<>();
			ctx.put("question", question);
			ctx.put("retrievedChunks", matches.size());
			return objectMapper.writeValueAsString(ctx);
		} catch (Exception e) {
			return "{\"question\":\"" + question + "\"}";
		}
	}

	private Map<String, Object> buildRagAdditionalContext(String question, List<PineconeClient.PineconeMatch> matches) {
		List<Map<String, Object>> chunks = matches.stream().map(m -> {
			Map<String, Object> chunk = new HashMap<>();
			chunk.put("id", m.id());
			chunk.put("score", m.score());
			chunk.put("metadata", m.metadata());
			return chunk;
		}).toList();
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("question", question);
		ctx.put("currentDate", LocalDate.now().toString());
		ctx.put("retrievedChunks", chunks);
		return ctx;
	}

	/**
	 * Builds the additional context map for the RAG prompt using the reranked top-N
	 * chunks. Includes the rerank score so the LLM can see which chunks were most
	 * relevant.
	 */
	private Map<String, Object> buildRagAdditionalContextReranked(String question,
			List<RerankService.RankedChunk> reranked) {
		List<Map<String, Object>> chunks = reranked.stream().map(rc -> {
			Map<String, Object> chunk = new HashMap<>();
			chunk.put("id", rc.id());
			chunk.put("score", rc.score());
			chunk.put("rerankScore", rc.rerankScore());
			chunk.put("metadata", rc.metadata());
			return chunk;
		}).toList();
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("question", question);
		ctx.put("currentDate", java.time.LocalDate.now().toString());
		ctx.put("retrievedChunks", chunks);
		return ctx;
	}

	private List<PineconeClient.PineconeMatch> toConfidenceMatches(List<RerankService.RankedChunk> reranked) {
		if (reranked == null || reranked.isEmpty()) {
			return List.of();
		}
		return reranked.stream()
				.map(chunk -> new PineconeClient.PineconeMatch(chunk.id(), chunk.rerankScore(), chunk.metadata()))
				.toList();
	}

	private RagAnswer parseRagAnswer(AiSuggestionResult result) {
		if (!result.available()) {
			return new RagAnswer("AI provider unavailable", List.of(), 0.0);
		}
		try {
			JsonNode root = objectMapper.readTree(result.payload());
			String answer = root.path("answer").asText("No answer generated");
			double confidence = root.path("confidence").asDouble(0.0);

			List<RagSource> sources = new ArrayList<>();
			JsonNode sourcesNode = root.path("sources");
			if (sourcesNode.isArray()) {
				for (JsonNode s : sourcesNode) {
					sources.add(new RagSource(s.path("entityType").asText(""), s.path("entityId").asText(""),
							s.path("weekStartDate").asText(""), s.path("snippet").asText("")));
				}
			}
			return new RagAnswer(answer, sources, confidence);
		} catch (Exception e) {
			log.debug("SemanticQueryService: could not parse RAG answer — {}", e.getMessage());
			return new RagAnswer(result.payload(), List.of(), result.confidence());
		}
	}

	/**
	 * Executes a multi-hop query by embedding each sub-query independently,
	 * querying Pinecone for each, and merging the results.
	 *
	 * <p>
	 * Merge strategy: union of all matches, deduplicated by chunk ID, keeping the
	 * highest score for each chunk. Results are returned sorted by score descending
	 * so the reranker sees the best candidates first.
	 *
	 * <p>
	 * Error handling: if one sub-query fails (e.g. embedding error), it is skipped
	 * and the remaining sub-queries are processed. If <em>all</em> sub-queries
	 * fail, {@code null} is returned to signal that the caller should degrade to
	 * {@link RagQueryResult#unavailable()}.
	 *
	 * @param subQueries
	 *            decomposed sub-queries (2 – {@value QueryRewriter#MAX_SUB_QUERIES}
	 *            elements)
	 * @param namespace
	 *            Pinecone namespace
	 * @param filter
	 *            Pinecone metadata filter (shared across all sub-queries)
	 * @return merged, deduplicated match list sorted by score descending; or
	 *         {@code null} if all sub-queries failed
	 */
	private List<PineconeClient.PineconeMatch> queryMultiHop(List<String> subQueries, String namespace,
			Map<String, Object> filter, boolean useVoyageIndex) {
		// Map from chunk ID → best-scoring match seen so far
		Map<String, PineconeClient.PineconeMatch> best = new LinkedHashMap<>();
		boolean anySuccess = false;

		for (String subQuery : subQueries) {
			try {
				List<PineconeClient.PineconeMatch> subMatches;
				if (useVoyageIndex) {
					float[] vector = embeddingService.embed(subQuery, "voyage-4");
					if (vector.length == 0) {
						log.debug("SemanticQueryService: voyage sub-query embedding returned empty — skipping");
						continue;
					}
					subMatches = pineconeClient.queryVoyage(namespace, vector, TOP_K, filter);
				} else {
					float[] vector = embeddingService.embed(subQuery);
					if (vector.length == 0) {
						log.debug("SemanticQueryService: sub-query embedding returned empty — skipping sub-query");
						continue;
					}

					java.util.Map<Integer, Float> sparseVector = null;
					try {
						java.util.Map<Integer, Float> sv = sparseEncoder.encode(subQuery);
						if (!sv.isEmpty()) {
							sparseVector = sv;
						}
					} catch (Exception ex) {
						log.debug("SemanticQueryService: sparse encoding failed for sub-query — {}", ex.getMessage());
					}
					subMatches = pineconeClient.query(namespace, vector, TOP_K, filter, sparseVector);
				}
				anySuccess = true;

				// Merge: keep the highest score per chunk ID
				for (PineconeClient.PineconeMatch m : subMatches) {
					best.merge(m.id(), m,
							(existing, incoming) -> incoming.score() > existing.score() ? incoming : existing);
				}
			} catch (Exception ex) {
				log.debug("SemanticQueryService: sub-query '{}' failed — {}", subQuery, ex.getMessage());
			}
		}

		if (!anySuccess) {
			log.debug("SemanticQueryService: all multi-hop sub-queries failed — unavailable");
			return null;
		}

		// Return merged results sorted by score descending
		return best.values().stream().sorted(Comparator.comparingDouble(PineconeClient.PineconeMatch::score).reversed())
				.toList();
	}

	/**
	 * Enriches the audit context string with experiment assignment data. Silently
	 * returns the original string on any parse/serialisation failure.
	 */
	private String enrichContextWithExperiments(String contextString, Map<String, String> experiments) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> ctx = objectMapper.readValue(contextString, Map.class);
			ctx.put("experimentAssignments", experiments);
			return objectMapper.writeValueAsString(ctx);
		} catch (Exception e) {
			log.debug("SemanticQueryService: could not enrich context with experiment assignments — {}",
					e.getMessage());
			return contextString;
		}
	}

	// ── Private helper records ────────────────────────────────────────────

	private record IntentResult(String intent, String userFilter, List<String> entityTypes, String timeRangeFrom,
			String timeRangeTo, List<String> keywords) {

		static IntentResult defaults() {
			return new IntentResult("", null, List.of(), null, null, List.of());
		}
	}

	private record RagAnswer(String answer, List<RagSource> sources, double confidence) {
	}
}
