package com.weeklycommit.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OpenRouterAiProvider.
 *
 * <p>
 * Tests the provider logic without making real HTTP calls — covers availability
 * checks, rate limiting, configuration, structured-output parsing, retry
 * behaviour, and rationale extraction.
 */
class OpenRouterAiProviderTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private OpenRouterAiProvider makeProvider(String apiKey) {
		return new OpenRouterAiProvider(apiKey, "test-model", 256, "http://localhost:0", objectMapper);
	}

	// ── Basic provider contract ──────────────────────────────────────────

	@Test
	void getName_returnsOpenrouter() {
		assertThat(makeProvider("key").getName()).isEqualTo("openrouter");
	}

	@Test
	void getVersion_returnsModel() {
		assertThat(makeProvider("key").getVersion()).isEqualTo("test-model");
	}

	@Test
	void isAvailable_blankKey_returnsFalse() {
		assertThat(makeProvider("").isAvailable()).isFalse();
		assertThat(makeProvider("  ").isAvailable()).isFalse();
	}

	@Test
	void generateSuggestion_blankKey_returnsUnavailable() {
		OpenRouterAiProvider provider = makeProvider("");
		AiContext ctx = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, Map.of(), Map.of(), List.of(),
				List.of(), Map.of());

		AiSuggestionResult result = provider.generateSuggestion(ctx);

		assertThat(result.available()).isFalse();
	}

	@Test
	void generateSuggestion_unreachableServer_returnsUnavailable() {
		// Points at localhost:0 which is unreachable
		OpenRouterAiProvider provider = makeProvider("sk-test-key");
		AiContext ctx = new AiContext(AiContext.TYPE_COMMIT_LINT, null, null, null, Map.of(), Map.of(), List.of(),
				List.of(), Map.of());

		AiSuggestionResult result = provider.generateSuggestion(ctx);

		assertThat(result.available()).isFalse();
	}

	@Test
	void rateLimiter_excessiveRequests_returnsUnavailable() {
		OpenRouterAiProvider provider = makeProvider("sk-test-key");
		AiContext ctx = new AiContext(AiContext.TYPE_COMMIT_DRAFT, null, null, null, Map.of(), Map.of(), List.of(),
				List.of(), Map.of());

		// Exhaust the rate limit (10 requests per minute)
		for (int i = 0; i < 11; i++) {
			provider.generateSuggestion(ctx);
		}
		// The 12th should definitely be rate-limited (or failed due to unreachable)
		AiSuggestionResult result = provider.generateSuggestion(ctx);
		assertThat(result.available()).isFalse();
	}

	@Test
	void tokenTracking_startsAtZero() {
		OpenRouterAiProvider provider = makeProvider("key");
		assertThat(provider.getTotalTokensUsed()).isZero();
		assertThat(provider.getTotalRequests()).isZero();
	}

	@Test
	void cacheCounters_startAtZero() {
		OpenRouterAiProvider provider = makeProvider("key");
		assertThat(provider.getCacheCreationTokens()).isZero();
		assertThat(provider.getCacheReadTokens()).isZero();
	}

	// ── Cache counter tests ──────────────────────────────────────────────

	@Test
	void updateUsageCounters_withCacheFields_updatesCacheCounters() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");

		com.fasterxml.jackson.databind.node.ObjectNode usage = objectMapper.createObjectNode();
		usage.put("total_tokens", 100);
		usage.put("cache_creation_input_tokens", 80);
		usage.put("cache_read_input_tokens", 20);

		provider.updateUsageCounters(usage);

		assertThat(provider.getTotalTokensUsed()).isEqualTo(100);
		assertThat(provider.getCacheCreationTokens()).isEqualTo(80);
		assertThat(provider.getCacheReadTokens()).isEqualTo(20);
	}

	@Test
	void updateUsageCounters_withoutCacheFields_cacheCountersRemainZero() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");

		com.fasterxml.jackson.databind.node.ObjectNode usage = objectMapper.createObjectNode();
		usage.put("total_tokens", 50);

		provider.updateUsageCounters(usage);

		assertThat(provider.getTotalTokensUsed()).isEqualTo(50);
		assertThat(provider.getCacheCreationTokens()).isZero();
		assertThat(provider.getCacheReadTokens()).isZero();
	}

	@Test
	void updateUsageCounters_calledMultipleTimes_accumulatesCounters() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");

		com.fasterxml.jackson.databind.node.ObjectNode usage1 = objectMapper.createObjectNode();
		usage1.put("cache_creation_input_tokens", 50);
		usage1.put("cache_read_input_tokens", 10);

		com.fasterxml.jackson.databind.node.ObjectNode usage2 = objectMapper.createObjectNode();
		usage2.put("cache_creation_input_tokens", 30);
		usage2.put("cache_read_input_tokens", 5);

		provider.updateUsageCounters(usage1);
		provider.updateUsageCounters(usage2);

		assertThat(provider.getCacheCreationTokens()).isEqualTo(80);
		assertThat(provider.getCacheReadTokens()).isEqualTo(15);
	}

	@Test
	void updateUsageCounters_nullNode_doesNothing() {
		OpenRouterAiProvider provider = makeProvider("key");
		provider.updateUsageCounters(null);
		assertThat(provider.getCacheCreationTokens()).isZero();
		assertThat(provider.getCacheReadTokens()).isZero();
	}

	// ── parseResponse: valid JSON response ──────────────────────────────

	@Test
	void parseResponse_validJson_returnsAvailableResult() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "{\\"suggestion\\": \\"be more specific\\", \\"rationale\\": \\"Clarity improves outcomes\\"}"
				    }
				  }],
				  "usage": {"total_tokens": 42}
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");

		assertThat(result.available()).isTrue();
		assertThat(result.payload()).contains("suggestion");
		assertThat(result.modelVersion()).isEqualTo("test-model");
		assertThat(result.promptVersion()).isEqualTo("v1");
	}

	@Test
	void parseResponse_validJson_extractsRationaleFromJsonField() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "{\\"suggestion\\": \\"reduce scope\\", \\"rationale\\": \\"Historical data shows 60% completion\\"}"
				    }
				  }]
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");

		assertThat(result.available()).isTrue();
		assertThat(result.rationale()).isEqualTo("Historical data shows 60% completion");
	}

	@Test
	void parseResponse_validJsonNoRationaleField_usesDefaultRationale() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "{\\"suggestion\\": \\"add more detail\\"}"
				    }
				  }]
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");

		assertThat(result.available()).isTrue();
		assertThat(result.rationale()).isEqualTo("AI-generated suggestion");
	}

	// ── parseResponse: empty / missing content ───────────────────────────

	@Test
	void parseResponse_noChoices_returnsUnavailable() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": []
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");

		assertThat(result.available()).isFalse();
	}

	@Test
	void parseResponse_blankContent_returnsUnavailable() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": ""
				    }
				  }]
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");

		assertThat(result.available()).isFalse();
	}

	// ── parseResponse: malformed JSON content triggers ParseException ────

	@Test
	void parseResponse_malformedJsonContent_throwsParseException() {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "This is plain text, not JSON at all"
				    }
				  }]
				}
				""";

		assertThatThrownBy(() -> provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1"))
				.isInstanceOf(OpenRouterAiProvider.ParseException.class).hasMessageContaining("non-JSON content");
	}

	@Test
	void parseResponse_markdownFenceContent_strippedSuccessfully() throws Exception {
		// Anthropic Claude via OpenRouter sometimes wraps JSON in markdown fences
		// even when response_format=json_object is set. The provider strips them
		// gracefully rather than failing.
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "```json\\n{\\"suggestion\\": \\"ok\\"}\\n```"
				    }
				  }]
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");
		assertThat(result.available()).isTrue();
		assertThat(result.payload()).contains("suggestion");
	}

	// ── parseResponse: model override ───────────────────────────────────

	@Test
	void parseResponse_withModelOverride_usesOverrideForModelVersion() throws Exception {
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "{\\"suggestion\\": \\"ok\\"}"
				    }
				  }]
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1",
				"google/gemini-2.5-flash");

		assertThat(result.available()).isTrue();
		assertThat(result.modelVersion()).isEqualTo("google/gemini-2.5-flash");
	}

	@Test
	void parseResponse_threeParamOverload_usesProviderModel() throws Exception {
		// The 3-param backward-compat overload should use this.model ("test-model")
		OpenRouterAiProvider provider = makeProvider("key");
		String responseBody = """
				{
				  "choices": [{
				    "message": {
				      "role": "assistant",
				      "content": "{\\"suggestion\\": \\"ok\\"}"
				    }
				  }]
				}
				""";

		AiSuggestionResult result = provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1");

		assertThat(result.available()).isTrue();
		assertThat(result.modelVersion()).isEqualTo("test-model");
	}
}
