package com.weeklycommit.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates the faithfulness of AI suggestion outputs using an LLM-as-judge
 * approach.
 *
 * <p>
 * Faithfulness measures whether every factual claim in the generated answer can
 * be directly attributed to the retrieved context. Per the Manus RAG evaluation
 * report (§2.1), faithfulness is the single most critical metric for enterprise
 * risk-signal generation.
 *
 * <p>
 * Scoring is done asynchronously to avoid blocking suggestion storage. Results
 * are written back to the {@link AiSuggestion} row.
 */
@Service
public class FaithfulnessEvaluator {

	private static final Logger log = LoggerFactory.getLogger(FaithfulnessEvaluator.class);

	/** Suggestion types where faithfulness is critical (always evaluate). */
	private static final java.util.Set<String> HIGH_STAKES_TYPES = java.util.Set.of(AiContext.TYPE_RISK_SIGNAL,
			AiContext.TYPE_TEAM_INSIGHT, AiContext.TYPE_PERSONAL_INSIGHT, AiContext.TYPE_RAG_QUERY);

	/** Sampling rate for lower-stakes suggestion types. */
	private static final double LOW_STAKES_SAMPLE_RATE = 0.10;

	private final AiProviderRegistry aiProviderRegistry;
	private final AiSuggestionRepository suggestionRepo;
	private final ObjectMapper objectMapper;

	public FaithfulnessEvaluator(AiProviderRegistry aiProviderRegistry, AiSuggestionRepository suggestionRepo,
			ObjectMapper objectMapper) {
		this.aiProviderRegistry = aiProviderRegistry;
		this.suggestionRepo = suggestionRepo;
		this.objectMapper = objectMapper;
	}

	/**
	 * Decides whether to score a suggestion and, if so, enqueues the eval
	 * asynchronously. High-stakes types (risk signals, insights, RAG queries) are
	 * always scored. Lower-stakes types are sampled at 10%.
	 *
	 * @param suggestionId
	 *            the persisted suggestion to evaluate
	 */
	@Async
	public void maybeScopeAsync(UUID suggestionId) {
		try {
			AiSuggestion suggestion = suggestionRepo.findById(suggestionId).orElse(null);
			if (suggestion == null) {
				return;
			}
			// Already scored
			if (suggestion.getEvalFaithfulnessScore() != null) {
				return;
			}
			boolean highStakes = HIGH_STAKES_TYPES.contains(suggestion.getSuggestionType());
			if (!highStakes && Math.random() > LOW_STAKES_SAMPLE_RATE) {
				return;
			}
			scoreFaithfulness(suggestion);
		} catch (Exception e) {
			log.warn("FaithfulnessEvaluator: failed for suggestion {} — {}", suggestionId, e.getMessage());
		}
	}

	/**
	 * Scores a suggestion's faithfulness and writes the result back to the DB.
	 *
	 * <p>
	 * Uses the judge prompt to decompose the suggestion payload into atomic claims,
	 * then checks each claim against the context (stored in the suggestion's
	 * {@code prompt} field).
	 */
	@Transactional
	public void scoreFaithfulness(AiSuggestion suggestion) {
		if (!aiProviderRegistry.isAiEnabled()) {
			return;
		}
		try {
			String answer = suggestion.getSuggestionPayload();
			String context = suggestion.getPrompt();

			// Build judge context
			Map<String, Object> judgeInput = Map.of("answer", answer, "retrievedContext", context, "suggestionType",
					suggestion.getSuggestionType());

			AiContext judgeContext = new AiContext("FAITHFULNESS_EVAL", null, null, null, Map.of(), Map.of(),
					java.util.List.of(), java.util.List.of(), judgeInput);

			AiSuggestionResult judgeResult = aiProviderRegistry.generateSuggestion(judgeContext);

			if (!judgeResult.available()) {
				log.debug("FaithfulnessEvaluator: judge unavailable for suggestion {}", suggestion.getId());
				return;
			}

			// Parse the judge's score
			float faithfulness = parseScore(judgeResult.payload(), "faithfulnessScore");
			float relevancy = parseScore(judgeResult.payload(), "relevancyScore");

			// Write back
			suggestion.setEvalFaithfulnessScore(faithfulness >= 0 ? faithfulness : null);
			suggestion.setEvalRelevancyScore(relevancy >= 0 ? relevancy : null);
			suggestion.setEvalScoredAt(Instant.now());
			suggestionRepo.save(suggestion);

			log.info("FaithfulnessEvaluator: scored suggestion {} — faithfulness={}, relevancy={}", suggestion.getId(),
					faithfulness, relevancy);

			// Alert on low scores
			if (faithfulness >= 0 && faithfulness < 0.85) {
				log.warn("FaithfulnessEvaluator: LOW FAITHFULNESS ({}) for {} suggestion {}", faithfulness,
						suggestion.getSuggestionType(), suggestion.getId());
			}
		} catch (Exception e) {
			log.warn("FaithfulnessEvaluator: scoring failed for suggestion {} — {}", suggestion.getId(),
					e.getMessage());
		}
	}

	/**
	 * Parses a float score from the judge's JSON payload.
	 *
	 * @return the score, or -1.0 if not parseable
	 */
	private float parseScore(String payload, String fieldName) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			JsonNode scoreNode = root.path(fieldName);
			if (scoreNode.isMissingNode() || scoreNode.isNull()) {
				return -1.0f;
			}
			return (float) scoreNode.asDouble(-1.0);
		} catch (Exception e) {
			log.debug("FaithfulnessEvaluator: could not parse {} from payload — {}", fieldName, e.getMessage());
			return -1.0f;
		}
	}
}
