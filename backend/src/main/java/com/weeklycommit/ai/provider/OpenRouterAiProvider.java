package com.weeklycommit.ai.provider;

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
	private static final int MAX_REQUESTS_PER_MINUTE = 10;

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

		try {
			String systemPrompt = loadPromptTemplate(context.suggestionType());
			String userMessage = buildUserMessage(context);

			String responseBody = callOpenRouter(systemPrompt, userMessage);
			totalRequests.incrementAndGet();

			return parseResponse(responseBody, context.suggestionType());
		} catch (Exception e) {
			log.warn("OpenRouter request failed for type={}: {}", context.suggestionType(), e.getMessage());
			return AiSuggestionResult.unavailable();
		}
	}

	// ── HTTP call ────────────────────────────────────────────────────────

	private String callOpenRouter(String systemPrompt, String userMessage) throws IOException, InterruptedException {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", maxTokens);

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

	private AiSuggestionResult parseResponse(String responseBody, String suggestionType) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode choices = root.get("choices");
			if (choices == null || choices.isEmpty()) {
				return AiSuggestionResult.unavailable();
			}

			String content = choices.get(0).path("message").path("content").asText("");
			if (content.isBlank()) {
				return AiSuggestionResult.unavailable();
			}

			// The model should return JSON. Extract it — handle markdown code fences.
			String jsonPayload = extractJson(content);
			String rationale = extractRationale(content, jsonPayload);

			// Validate it's parseable JSON
			objectMapper.readTree(jsonPayload);

			return new AiSuggestionResult(true, jsonPayload, rationale, 0.85, model);
		} catch (Exception e) {
			log.warn("Failed to parse OpenRouter response for {}: {}", suggestionType, e.getMessage());
			return AiSuggestionResult.unavailable();
		}
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

	// ── Helpers ──────────────────────────────────────────────────────────

	/**
	 * Extracts JSON from model output, handling ```json fences.
	 */
	private String extractJson(String content) {
		String trimmed = content.trim();
		// Handle ```json ... ``` fences
		if (trimmed.contains("```json")) {
			int start = trimmed.indexOf("```json") + 7;
			int end = trimmed.indexOf("```", start);
			if (end > start) {
				return trimmed.substring(start, end).trim();
			}
		}
		if (trimmed.contains("```")) {
			int start = trimmed.indexOf("```") + 3;
			// Skip optional language identifier on the same line
			int lineEnd = trimmed.indexOf('\n', start);
			if (lineEnd > start) {
				start = lineEnd + 1;
			}
			int end = trimmed.indexOf("```", start);
			if (end > start) {
				return trimmed.substring(start, end).trim();
			}
		}
		// Try to find raw JSON object
		int braceStart = trimmed.indexOf('{');
		int braceEnd = trimmed.lastIndexOf('}');
		if (braceStart >= 0 && braceEnd > braceStart) {
			return trimmed.substring(braceStart, braceEnd + 1);
		}
		return trimmed;
	}

	private String extractRationale(String fullContent, String jsonPayload) {
		// Everything outside the JSON is rationale
		String without = fullContent.replace(jsonPayload, "").trim();
		// Strip markdown fences
		without = without.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
		return without.isBlank() ? "AI-generated suggestion" : without.substring(0, Math.min(500, without.length()));
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
}
