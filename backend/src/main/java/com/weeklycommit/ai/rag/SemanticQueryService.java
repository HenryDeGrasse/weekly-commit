package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.repository.TeamRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
	private static final int TOP_K = 40;

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
			UUID suggestionId) {

		/** Convenience factory for a degraded/unavailable result. */
		public static RagQueryResult unavailable() {
			return new RagQueryResult(false, null, List.of(), 0.0, null);
		}
	}

	// ── Dependencies ──────────────────────────────────────────────────────

	private final PineconeClient pineconeClient;
	private final EmbeddingService embeddingService;
	private final AiProviderRegistry aiProviderRegistry;
	private final AiSuggestionService aiSuggestionService;
	private final ObjectMapper objectMapper;
	private final TeamRepository teamRepo;

	public SemanticQueryService(PineconeClient pineconeClient, EmbeddingService embeddingService,
			AiProviderRegistry aiProviderRegistry, AiSuggestionService aiSuggestionService, ObjectMapper objectMapper,
			TeamRepository teamRepo) {
		this.pineconeClient = pineconeClient;
		this.embeddingService = embeddingService;
		this.aiProviderRegistry = aiProviderRegistry;
		this.aiSuggestionService = aiSuggestionService;
		this.objectMapper = objectMapper;
		this.teamRepo = teamRepo;
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
			// Step 2: intent classification
			IntentResult intent = classifyIntent(question, teamId, userId);

			// Step 3: embed the question
			float[] vector = embeddingService.embed(question);
			if (vector.length == 0) {
				log.debug("SemanticQueryService: embedding returned empty vector — unavailable");
				return RagQueryResult.unavailable();
			}

			// Step 4: build metadata filter
			Map<String, Object> filter = buildFilter(intent, teamId, userId);

			// Step 5: query Pinecone
			String namespace = resolveNamespace(teamId);
			List<PineconeClient.PineconeMatch> matches = pineconeClient.query(namespace, vector, TOP_K, filter);

			// Step 6 & 7: build RAG prompt context and generate answer
			String contextString = buildContextString(question, matches);
			AiContext ragContext = new AiContext(AiContext.TYPE_RAG_QUERY, userId, null, null, Map.of(), Map.of(),
					List.of(), List.of(), buildRagAdditionalContext(question, matches));
			AiSuggestionResult llmResult = aiProviderRegistry.generateSuggestion(ragContext);

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
					suggestionId);

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
