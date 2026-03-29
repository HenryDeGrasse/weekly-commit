package com.weeklycommit.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.rag.SemanticQueryService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams RAG query answers as Server-Sent Events using Spring MVC's native
 * {@link SseEmitter}. No WebFlux / Reactor dependency is introduced — this runs
 * purely on the Spring MVC (servlet) stack.
 *
 * <p>
 * Event types emitted in order:
 * <ol>
 * <li>{@code delta} — one or more incremental answer tokens (word-level
 * chunks)</li>
 * <li>{@code sources} — JSON array of source citations after the answer
 * completes</li>
 * <li>{@code confidence} — confidence score as a decimal string</li>
 * <li>{@code done} — empty payload, signals the stream has ended</li>
 * <li>{@code error} — error message when anything fails; replaces the above
 * sequence</li>
 * </ol>
 *
 * <p>
 * The underlying RAG query ({@link SemanticQueryService#query}) is executed on
 * the shared {@code taskExecutor} thread pool so the servlet thread is not
 * blocked. The complete answer is then chunked into word-level delta events
 * before being sent.
 */
@RestController
public class AiStreamController {

	private static final Logger log = LoggerFactory.getLogger(AiStreamController.class);

	/**
	 * SSE connection timeout — client should reconnect if the answer takes longer.
	 */
	private static final long SSE_TIMEOUT_MS = 60_000L;

	private final SemanticQueryService semanticQueryService;
	private final ObjectMapper objectMapper;
	private final Executor taskExecutor;

	public AiStreamController(SemanticQueryService semanticQueryService, ObjectMapper objectMapper,
			@Qualifier("taskExecutor") Executor taskExecutor) {
		this.semanticQueryService = semanticQueryService;
		this.objectMapper = objectMapper;
		this.taskExecutor = taskExecutor;
	}

	// ── Endpoint ─────────────────────────────────────────────────────────

	/**
	 * Streams the RAG answer for {@code question} as Server-Sent Events.
	 *
	 * <p>
	 * All three query parameters are required. Missing or blank values return HTTP
	 * 400 immediately (before the stream is started).
	 *
	 * @param question
	 *            natural-language question from the user
	 * @param teamId
	 *            team scope for Pinecone namespace + metadata filter
	 * @param userId
	 *            requesting user (for audit)
	 * @param response
	 *            servlet response used to set proxy-compatibility headers
	 * @return SSE emitter that delivers the answer as incremental events
	 */
	@GetMapping(value = "/api/ai/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamRagQuery(@RequestParam String question, @RequestParam UUID teamId,
			@RequestParam UUID userId, HttpServletResponse response) {
		// Nginx / reverse-proxy: disable response buffering so events reach
		// the client immediately rather than being held in the proxy buffer.
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("Cache-Control", "no-cache");

		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

		taskExecutor.execute(() -> {
			try {
				SemanticQueryService.RagQueryResult result = semanticQueryService.query(question, teamId, userId);

				if (!result.available()) {
					emitter.send(SseEmitter.event().name("error").data("AI unavailable"));
					emitter.complete();
					return;
				}

				// Stream answer word-by-word as delta events
				String answer = result.answer() != null ? result.answer() : "";
				if (!answer.isBlank()) {
					String[] words = answer.split("(?<=\\s)|(?=\\s)");
					for (String token : words) {
						if (!token.isEmpty()) {
							emitter.send(SseEmitter.event().name("delta").data(token));
						}
					}
				}

				// Send the source citations as a JSON array
				emitter.send(
						SseEmitter.event().name("sources").data(objectMapper.writeValueAsString(result.sources())));

				// Send the confidence score
				emitter.send(SseEmitter.event().name("confidence").data(String.valueOf(result.confidence())));

				// Signal stream end
				emitter.send(SseEmitter.event().name("done").data(""));
				emitter.complete();

			} catch (Exception e) {
				log.warn("SSE streaming failed for question='{}': {}", question, e.getMessage());
				try {
					// Send error event so the client knows why the stream stopped, then complete
					// normally. completeWithError() would propagate the exception through the async
					// dispatch mechanism instead of delivering it as an SSE event to the client.
					emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
					emitter.complete();
				} catch (Exception ignored) {
					// Emitter already closed; nothing we can do
				}
			}
		});

		return emitter;
	}
}
