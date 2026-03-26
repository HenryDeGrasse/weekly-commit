package com.weeklycommit.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response from the {@code POST /api/ai/query} RAG endpoint.
 *
 * @param aiAvailable
 *            {@code false} when the RAG pipeline is unavailable (Pinecone or
 *            LLM down); all other fields are meaningless in that case
 * @param answer
 *            synthesised natural-language answer (null when unavailable)
 * @param sources
 *            source chunks that informed the answer
 * @param confidence
 *            model confidence [0.0, 1.0]
 * @param suggestionId
 *            ID of the persisted
 *            {@link com.weeklycommit.domain.entity.AiSuggestion} audit row
 *            (null when unavailable)
 */
public record RagQueryResponse(boolean aiAvailable, String answer, List<RagSourceDto> sources, double confidence,
		UUID suggestionId) {

	/** Convenience factory for a degraded/unavailable response. */
	public static RagQueryResponse unavailable() {
		return new RagQueryResponse(false, null, List.of(), 0.0, null);
	}
}
