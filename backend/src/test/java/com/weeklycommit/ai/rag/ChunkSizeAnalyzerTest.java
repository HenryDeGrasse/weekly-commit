package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChunkSizeAnalyzer}.
 *
 * <p>
 * Uses {@link SimpleMeterRegistry} so Micrometer metrics are recorded in-memory
 * and can be inspected without a full Spring context.
 */
class ChunkSizeAnalyzerTest {

	private MeterRegistry meterRegistry;
	private ChunkSizeAnalyzer analyzer;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		analyzer = new ChunkSizeAnalyzer(meterRegistry);
	}

	// ── Token estimation ─────────────────────────────────────────────────

	@Test
	void analyze_emptyText_returnsZeroTokens() {
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", "", Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.estimatedTokens()).isZero();
		assertThat(stats.charLength()).isZero();
	}

	@Test
	void analyze_exactlyCharsPerTokenChars_returnsOneToken() {
		// 4 chars == 1 token (integer division)
		String text = "abcd"; // 4 chars
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", text, Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.estimatedTokens()).isEqualTo(1);
		assertThat(stats.charLength()).isEqualTo(4);
	}

	@Test
	void analyze_knownCharLength_estimatesTokensCorrectly() {
		// 2500 chars / 4 = 625 tokens
		String text = "x".repeat(2500);
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", text, Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.estimatedTokens()).isEqualTo(625);
		assertThat(stats.charLength()).isEqualTo(2500);
	}

	@Test
	void analyze_nullText_treatedAsEmpty() {
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", null, Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.estimatedTokens()).isZero();
		assertThat(stats.charLength()).isZero();
	}

	// ── Threshold boundary ───────────────────────────────────────────────

	@Test
	void analyze_exactlyAtThreshold_doesNotExceed() {
		// 625 tokens == 2500 chars → NOT exceeding threshold (> 625, not >= 625)
		String text = "x".repeat(625 * ChunkSizeAnalyzer.CHARS_PER_TOKEN);
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", text, Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.estimatedTokens()).isEqualTo(625);
		assertThat(stats.exceedsRecommendedSize()).isFalse();
	}

	@Test
	void analyze_oneTokenOverThreshold_exceedsRecommendedSize() {
		// 626 tokens == 2504 chars
		String text = "x"
				.repeat((ChunkSizeAnalyzer.RECOMMENDED_TOKEN_THRESHOLD + 1) * ChunkSizeAnalyzer.CHARS_PER_TOKEN);
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", text, Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.estimatedTokens()).isEqualTo(626);
		assertThat(stats.exceedsRecommendedSize()).isTrue();
	}

	@Test
	void analyze_smallChunk_doesNotExceedRecommendedSize() {
		String text = "Short text.";
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", text, Map.of());
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.exceedsRecommendedSize()).isFalse();
	}

	// ── Metadata key count ───────────────────────────────────────────────

	@Test
	void analyze_metadataKeyCount_matchesMapSize() {
		Map<String, Object> meta = Map.of("a", "1", "b", "2", "c", "3");
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", "text", meta);
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.metadataKeyCount()).isEqualTo(3);
	}

	@Test
	void analyze_nullMetadata_returnsZeroKeyCount() {
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", "text", null);
		ChunkSizeAnalyzer.ChunkStats stats = analyzer.analyze(chunk);
		assertThat(stats.metadataKeyCount()).isZero();
	}

	// ── Metrics recording ────────────────────────────────────────────────

	@Test
	void analyze_recordsTokenCountInDistributionSummary() {
		String text = "x".repeat(400); // 100 tokens
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", text, Map.of());
		analyzer.analyze(chunk);

		DistributionSummary summary = meterRegistry.find("ai.chunk.tokens").summary();
		assertThat(summary).isNotNull();
		assertThat(summary.count()).isEqualTo(1);
		assertThat(summary.totalAmount()).isEqualTo(100.0);
	}

	@Test
	void analyze_multipleChunks_accumulatesInSummary() {
		analyzer.analyze(new ChunkBuilder.ChunkData("id1", "x".repeat(400), Map.of())); // 100 tokens
		analyzer.analyze(new ChunkBuilder.ChunkData("id2", "x".repeat(800), Map.of())); // 200 tokens

		DistributionSummary summary = meterRegistry.find("ai.chunk.tokens").summary();
		assertThat(summary).isNotNull();
		assertThat(summary.count()).isEqualTo(2);
		assertThat(summary.totalAmount()).isEqualTo(300.0);
	}

	// ── logWarningIfOversized ────────────────────────────────────────────

	@Test
	void logWarningIfOversized_smallChunk_doesNotThrow() {
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", "Short text.", Map.of());
		// Should not throw — just verifies graceful no-op for small chunks
		analyzer.logWarningIfOversized(chunk);
	}

	@Test
	void logWarningIfOversized_largeChunk_doesNotThrow() {
		String text = "x".repeat(5000); // ~1250 tokens — well over threshold
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("large-chunk", text, Map.of());
		// Should not throw — logs a warning internally
		analyzer.logWarningIfOversized(chunk);
	}

	@Test
	void logWarningIfOversized_nullText_doesNotThrow() {
		ChunkBuilder.ChunkData chunk = new ChunkBuilder.ChunkData("id1", null, Map.of());
		analyzer.logWarningIfOversized(chunk);
	}
}
