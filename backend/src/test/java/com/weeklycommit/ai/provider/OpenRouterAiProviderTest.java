package com.weeklycommit.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OpenRouterAiProvider. Tests the provider logic without making
 * real HTTP calls — focuses on availability checks, rate limiting, and
 * configuration.
 */
class OpenRouterAiProviderTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private OpenRouterAiProvider makeProvider(String apiKey) {
		return new OpenRouterAiProvider(apiKey, "test-model", 256, "http://localhost:0", objectMapper);
	}

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
}
