package com.weeklycommit.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Production AI provider that routes requests through OpenRouter.
 *
 * <p>
 * Activated when {@code ai.provider=openrouter} (the default). Falls back to
 * unavailable when the API key is blank or the service is unreachable.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Per-suggestion-type prompt templates loaded from classpath</li>
 * <li>Cached health check (60 s TTL) to avoid hammering the API</li>
 * <li>Simple rate limiting via token-bucket (10 req/min default)</li>
 * <li>Token counting from response headers for cost tracking</li>
 * <li>Structured output mode (response_format=json_object) eliminates markdown
 * fence parsing</li>
 * <li>One-retry fallback if the model returns non-JSON content</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openrouter", matchIfMissing = true)
public class OpenRouterAiProvider implements AiProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenRouterAiProvider.class);

	private final String apiKey;
	private final String model;
	private final int maxTokens;
	private final String baseUrl;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	/** Cached availability result: [timestamp, available]. */
	private final AtomicReference<Instant> lastHealthCheck = new AtomicReference<>(Instant.EPOCH);
	private volatile boolean lastHealthResult = false;
	private static final Duration HEALTH_CACHE_TTL = Duration.ofSeconds(60);

	/** Simple rate limiter: track request count per minute window. */
	private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
	private final AtomicLong windowCount = new AtomicLong(0);
	private static final int MAX_REQUESTS_PER_MINUTE = 30;

	/** Running totals for observability. */
	private final AtomicLong totalTokensUsed = new AtomicLong(0);
	private final AtomicLong totalRequests = new AtomicLong(0);

	public OpenRouterAiProvider(@Value("${ai.openrouter.api-key:}") String apiKey,
			@Value("${ai.openrouter.model:anthropic/claude-sonnet-4-20250514}") String model,
			@Value("${ai.openrouter.max-tokens:1024}") int maxTokens,
			@Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
			ObjectMapper objectMapper) {
		this.apiKey = apiKey;
		this.model = model;
		this.maxTokens = maxTokens;
		this.baseUrl = baseUrl;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

		if (apiKey == null || apiKey.isBlank()) {
			log.warn("OpenRouter API key is blank — provider will report unavailable");
		} else {
			log.info("OpenRouterAiProvider initialized: model={}, maxTokens={}", model, maxTokens);
		}
	}

	// ── AiProvider contract ──────────────────────────────────────────────

	@Override
	public String getName() {
		return "openrouter";
	}

	@Override
	public String getVersion() {
		return model;
	}

	@Override
	public boolean isAvailable() {
		if (apiKey == null || apiKey.isBlank()) {
			return false;
		}
		// Cached health check
		Instant now = Instant.now();
		if (Duration.between(lastHealthCheck.get(), now).compareTo(HEALTH_CACHE_TTL) < 0) {
			return lastHealthResult;
		}
		// Fresh check — just verify the API key works with a tiny request
		try {
			HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/models"))
					.header("Authorization", "Bearer " + apiKey).timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			lastHealthResult = resp.statusCode() == 200;
		} catch (Exception e) {
			log.debug("OpenRouter health check failed: {}", e.getMessage());
			lastHealthResult = false;
		}
		lastHealthCheck.set(now);
		return lastHealthResult;
	}

	@Override
	public AiSuggestionResult generateSuggestion(AiContext context) {
		if (apiKey == null || apiKey.isBlank()) {
			return AiSuggestionResult.unavailable();
		}

		// Rate limiting
		if (!tryAcquireRate()) {
			log.warn("OpenRouter rate limit exceeded for type={}", context.suggestionType());
			return AiSuggestionResult.unavailable();
		}

		String promptVersion = resolvePromptVersion(context.suggestionType());
		String systemPrompt = loadPromptTemplate(context.suggestionType());
		String userMessage = buildUserMessage(context);

		// Retry loop: if the model returns non-JSON content, retry once
		for (int attempt = 1; attempt <= 2; attempt++) {
			try {
				String responseBody = callOpenRouter(systemPrompt, userMessage);
				totalRequests.incrementAndGet();
				return parseResponse(responseBody, context.suggestionType(), promptVersion);
			} catch (ParseException e) {
				if (attempt < 2) {
					log.warn("OpenRouter response parse failed for type={} (attempt {}), retrying: {}",
							context.suggestionType(), attempt, e.getMessage());
				} else {
					log.warn("OpenRouter response parse failed after retry for type={}: {}", context.suggestionType(),
							e.getMessage());
					return AiSuggestionResult.unavailable();
				}
			} catch (Exception e) {
				log.warn("OpenRouter request failed for type={}: {}", context.suggestionType(), e.getMessage());
				return AiSuggestionResult.unavailable();
			}
		}

		return AiSuggestionResult.unavailable();
	}

	// ── HTTP call ────────────────────────────────────────────────────────

	private String callOpenRouter(String systemPrompt, String userMessage) throws IOException, InterruptedException {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", maxTokens);

		// Structured output: forces the model to return valid JSON without markdown
		// fences
		ObjectNode responseFormat = body.putObject("response_format");
		responseFormat.put("type", "json_object");

		ArrayNode messages = body.putArray("messages");
		messages.addObject().put("role", "system").put("content", systemPrompt);
		messages.addObject().put("role", "user").put("content", userMessage);

		String json = objectMapper.writeValueAsString(body);

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/chat/completions"))
				.header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
				.header("HTTP-Referer", "https://weeklycommit.dev").header("X-Title", "Weekly Commit")
				.timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			throw new IOException("OpenRouter returned " + response.statusCode() + ": " + response.body());
		}

		// Track tokens from response
		try {
			JsonNode respNode = objectMapper.readTree(response.body());
			JsonNode usage = respNode.get("usage");
			if (usage != null && usage.has("total_tokens")) {
				totalTokensUsed.addAndGet(usage.get("total_tokens").asLong());
			}
		} catch (Exception ignored) {
		}

		return response.body();
	}

	// ── Response parsing ─────────────────────────────────────────────────

	/**
	 * Parses the OpenRouter response body and returns an
	 * {@link AiSuggestionResult}.
	 *
	 * <p>
	 * With {@code response_format=json_object}, the model content is expected to be
	 * valid JSON directly — no markdown fence stripping needed.
	 *
	 * @throws ParseException
	 *             if the response content cannot be parsed as valid JSON (caller
	 *             should retry)
	 *
	 *             <p>
	 *             Package-private for testing.
	 */
	AiSuggestionResult parseResponse(String responseBody, String suggestionType, String promptVersion)
			throws ParseException {
		JsonNode root;
		try {
			root = objectMapper.readTree(responseBody);
		} catch (Exception e) {
			// The outer response envelope itself is malformed — not retry-worthy
			log.warn("Failed to parse OpenRouter response envelope for {}: {}", suggestionType, e.getMessage());
			return AiSuggestionResult.unavailable();
		}

		JsonNode choices = root.get("choices");
		if (choices == null || choices.isEmpty()) {
			return AiSuggestionResult.unavailable();
		}

		String content = choices.get(0).path("message").path("content").asText("");
		if (content.isBlank()) {
			return AiSuggestionResult.unavailable();
		}

		// With response_format=json_object the content should be valid JSON directly.
		// If it isn't, signal a retry-worthy failure via ParseException.
		JsonNode contentNode;
		try {
			contentNode = objectMapper.readTree(content);
		} catch (JsonProcessingException e) {
			throw new ParseException("Model returned non-JSON content for " + suggestionType + ": "
					+ content.substring(0, Math.min(200, content.length())), e);
		}

		// Extract rationale from the JSON payload if the model provided one
		String rationale = "AI-generated suggestion";
		JsonNode rationaleNode = contentNode.get("rationale");
		if (rationaleNode != null && !rationaleNode.isNull() && !rationaleNode.asText("").isBlank()) {
			rationale = rationaleNode.asText();
		}

		return new AiSuggestionResult(true, content, rationale, 0.85, model, promptVersion);
	}

	// ── Prompt construction ──────────────────────────────────────────────

	private String loadPromptTemplate(String suggestionType) {
		String filename = switch (suggestionType) {
			case AiContext.TYPE_COMMIT_DRAFT -> "commit-draft-assist.txt";
			case AiContext.TYPE_COMMIT_LINT -> "commit-lint.txt";
			case AiContext.TYPE_RCDO_SUGGEST -> "rcdo-suggest.txt";
			case AiContext.TYPE_RISK_SIGNAL -> "risk-signal.txt";
			case AiContext.TYPE_RECONCILE_ASSIST -> "reconcile-assist.txt";
			case AiContext.TYPE_TEAM_SUMMARY -> "team-summary.txt";
			case AiContext.TYPE_RAG_INTENT -> "rag-intent.txt";
			case AiContext.TYPE_RAG_QUERY -> "rag-query.txt";
			case AiContext.TYPE_TEAM_INSIGHT -> "team-insight.txt";
			case AiContext.TYPE_PERSONAL_INSIGHT -> "personal-insight.txt";
			case AiContext.TYPE_WHAT_IF -> "what-if.txt";
			case "FAITHFULNESS_EVAL" -> "faithfulness-eval.txt";
			default -> "generic.txt";
		};
		try {
			ClassPathResource resource = new ClassPathResource("prompts/" + filename);
			return new String(resource.getInputStream().readAllBytes());
		} catch (IOException e) {
			log.debug("Prompt template not found: prompts/{}, using default", filename);
			return "You are a helpful planning assistant for a weekly commitment tool. "
					+ "Respond with valid JSON matching the expected schema.";
		}
	}

	private String buildUserMessage(AiContext context) {
		try {
			Map<String, Object> msg = Map.of("suggestionType", context.suggestionType(), "commitData",
					context.commitData() != null ? context.commitData() : Map.of(), "planData",
					context.planData() != null ? context.planData() : Map.of(), "historicalCommits",
					context.historicalCommits() != null ? context.historicalCommits() : java.util.List.of(), "rcdoTree",
					context.rcdoTree() != null ? context.rcdoTree() : java.util.List.of(), "additionalContext",
					context.additionalContext() != null ? context.additionalContext() : Map.of());
			return objectMapper.writeValueAsString(msg);
		} catch (Exception e) {
			return "{}";
		}
	}

	/**
	 * Resolves the prompt version identifier for the given suggestion type. Uses
	 * the base prompt filename as the version key with a "-v1" suffix. When A/B
	 * testing, this method can be extended to read from feature flags and load
	 * variant templates (e.g. "commit-draft-assist-v2.txt").
	 */
	private String resolvePromptVersion(String suggestionType) {
		String base = switch (suggestionType) {
			case AiContext.TYPE_COMMIT_DRAFT -> "commit-draft-assist";
			case AiContext.TYPE_COMMIT_LINT -> "commit-lint";
			case AiContext.TYPE_RCDO_SUGGEST -> "rcdo-suggest";
			case AiContext.TYPE_RISK_SIGNAL -> "risk-signal";
			case AiContext.TYPE_RECONCILE_ASSIST -> "reconcile-assist";
			case AiContext.TYPE_TEAM_SUMMARY -> "team-summary";
			case AiContext.TYPE_RAG_INTENT -> "rag-intent";
			case AiContext.TYPE_RAG_QUERY -> "rag-query";
			case AiContext.TYPE_TEAM_INSIGHT -> "team-insight";
			case AiContext.TYPE_PERSONAL_INSIGHT -> "personal-insight";
			case AiContext.TYPE_WHAT_IF -> "what-if";
			default -> "generic";
		};
		// commit-draft-assist was updated to v2 (added title length rule + example).
		if ("commit-draft-assist".equals(base)) {
			return base + "-v2";
		}
		return base + "-v1";
	}

	private boolean tryAcquireRate() {
		long now = System.currentTimeMillis();
		long windowMs = 60_000;
		if (now - windowStart.get() > windowMs) {
			windowStart.set(now);
			windowCount.set(0);
		}
		return windowCount.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
	}

	// ── Observability ────────────────────────────────────────────────────

	/** Total tokens consumed across all requests (for metrics). */
	public long getTotalTokensUsed() {
		return totalTokensUsed.get();
	}

	/** Total requests made (for metrics). */
	public long getTotalRequests() {
		return totalRequests.get();
	}

	// ── Inner types ──────────────────────────────────────────────────────

	/**
	 * Thrown by {@link #parseResponse} when the model response content is not valid
	 * JSON. The caller should retry the request.
	 *
	 * <p>
	 * Package-private for testing.
	 */
	static class ParseException extends Exception {

		ParseException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
