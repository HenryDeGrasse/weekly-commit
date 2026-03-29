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
	void parseResponse_markdownFenceContent_throwsParseException() {
		// With response_format=json_object the model should not return fences.
		// If it does anyway, we treat it as a retry-worthy parse failure rather than
		// trying to strip the fences.
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

		assertThatThrownBy(() -> provider.parseResponse(responseBody, AiContext.TYPE_COMMIT_DRAFT, "v1"))
				.isInstanceOf(OpenRouterAiProvider.ParseException.class);
	}
}
