package com.weeklycommit.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request body for the {@code POST /api/ai/query} RAG endpoint.
 *
 * @param question
 *            natural-language question to answer (required)
 * @param teamId
 *            optional team scope; when supplied, Pinecone search is filtered to
 *            this team's namespace
 * @param userId
 *            optional requesting user; used for personalisation and audit
 */
public record RagQueryRequest(
		/** Natural-language question (required). */
		@NotBlank String question,
		/** Optional team scope for vector retrieval. */
		UUID teamId,
		/** Optional user identity for personalisation and audit. */
		UUID userId) {
}
