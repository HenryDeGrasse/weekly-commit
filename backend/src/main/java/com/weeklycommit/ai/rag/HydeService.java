package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hypothetical Document Embedding (HyDE) service.
 *
 * <p>
 * Given a user's question, generates a short hypothetical answer that can be
 * embedded and used to augment semantic search retrieval. By embedding a
 * plausible answer alongside the question, the resulting vector is closer to
 * the actual document embeddings in the index.
 *
 * <p>
 * Gracefully degrades: returns {@code available=false} if the AI provider is
 * unavailable or the response cannot be parsed.
 *
 * <p>
 * This service is intended to be used as an opt-in enhancement to the RAG
 * pipeline, controlled by the {@code hyde} experiment flag (default off).
 */
@Service
public class HydeService {

	private static final Logger log = LoggerFactory.getLogger(HydeService.class);

	private final AiProviderRegistry aiProviderRegistry;
	private final ObjectMapper objectMapper;

	public HydeService(AiProviderRegistry aiProviderRegistry, ObjectMapper objectMapper) {
		this.aiProviderRegistry = aiProviderRegistry;
		this.objectMapper = objectMapper;
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Generates a short hypothetical answer for the given {@code question}.
	 *
	 * <p>
	 * The hypothetical answer is intended for embedding — it should NOT be shown to
	 * the user directly.
	 *
	 * @param question
	 *            the user's natural-language question
	 * @param userId
	 *            the requesting user (for audit / experiment assignment)
	 * @return a {@link HydeResult} with the generated answer, or
	 *         {@code available=false} on any failure
	 */
	public HydeResult generateHypothetical(String question, UUID userId) {
		if (question == null || question.isBlank()) {
			return HydeResult.unavailable();
		}

		Map<String, Object> additionalContext = Map.of("question", question);
		AiContext context = new AiContext(AiContext.TYPE_HYDE, userId, null, null, Map.of(), Map.of(),
				java.util.List.of(), java.util.List.of(), additionalContext);

		try {
			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context, userId);
			if (!result.available()) {
				log.debug("HydeService: AI provider unavailable for question='{}'", question);
				return HydeResult.unavailable();
			}

			JsonNode payload = objectMapper.readTree(result.payload());
			JsonNode answerNode = payload.get("hypotheticalAnswer");
			if (answerNode == null || answerNode.isNull() || answerNode.asText("").isBlank()) {
				log.debug("HydeService: missing 'hypotheticalAnswer' in response for question='{}'", question);
				return HydeResult.unavailable();
			}

			String hypotheticalAnswer = answerNode.asText();
			log.debug("HydeService: generated hypothetical ({} chars) for question='{}'", hypotheticalAnswer.length(),
					question);
			return new HydeResult(true, hypotheticalAnswer);

		} catch (Exception e) {
			log.warn("HydeService: failed to generate hypothetical for question='{}': {}", question, e.getMessage());
			return HydeResult.unavailable();
		}
	}

	// ── Inner record ─────────────────────────────────────────────────────

	/**
	 * Result of a HyDE hypothetical-document generation request.
	 *
	 * @param available
	 *            {@code false} if the provider was unavailable or parsing failed
	 * @param hypotheticalAnswer
	 *            the generated hypothetical answer text, or {@code null} when
	 *            unavailable
	 */
	public record HydeResult(boolean available, String hypotheticalAnswer) {

		/** Convenience factory for a failed/degraded result. */
		public static HydeResult unavailable() {
			return new HydeResult(false, null);
		}
	}
}
