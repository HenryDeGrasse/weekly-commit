package com.weeklycommit.ai.rag;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observability component that analyses the size of text chunks before they are
 * embedded and stored in Pinecone.
 *
 * <p>
 * Token estimation uses the rule-of-thumb approximation of 4 characters per
 * token, which is accurate enough for chunk-size monitoring purposes.
 *
 * <p>
 * Metrics published:
 * <ul>
 * <li>{@code ai.chunk.tokens} — distribution summary of estimated token counts
 * per chunk</li>
 * </ul>
 *
 * <p>
 * Chunks exceeding {@value #RECOMMENDED_TOKEN_THRESHOLD} estimated tokens
 * trigger a WARN log, as oversized chunks can degrade retrieval quality.
 */
@Component
public class ChunkSizeAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(ChunkSizeAnalyzer.class);

	/** Approximate characters-per-token ratio for token estimation. */
	static final int CHARS_PER_TOKEN = 4;

	/**
	 * Chunks exceeding this estimated-token count are considered oversized and
	 * trigger a WARN log. Corresponds to roughly 2 500 characters.
	 */
	static final int RECOMMENDED_TOKEN_THRESHOLD = 625;

	private final DistributionSummary tokenSummary;

	public ChunkSizeAnalyzer(MeterRegistry meterRegistry) {
		this.tokenSummary = DistributionSummary.builder("ai.chunk.tokens")
				.description("Estimated token count per indexed chunk").register(meterRegistry);
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Analyses the size of the given chunk, records the estimated token count in
	 * the {@code ai.chunk.tokens} distribution summary, and returns a
	 * {@link ChunkStats} snapshot.
	 *
	 * @param chunk
	 *            the chunk to analyse; must not be {@code null}
	 * @return stats for the chunk
	 */
	public ChunkStats analyze(ChunkBuilder.ChunkData chunk) {
		String text = chunk.text() != null ? chunk.text() : "";
		int charLength = text.length();
		int estimatedTokens = charLength / CHARS_PER_TOKEN;
		int metadataKeyCount = chunk.metadata() != null ? chunk.metadata().size() : 0;
		boolean exceedsRecommendedSize = estimatedTokens > RECOMMENDED_TOKEN_THRESHOLD;

		tokenSummary.record(estimatedTokens);

		return new ChunkStats(estimatedTokens, charLength, metadataKeyCount, exceedsRecommendedSize);
	}

	/**
	 * Logs a WARN-level message when the chunk's estimated token count exceeds
	 * {@value #RECOMMENDED_TOKEN_THRESHOLD}.
	 *
	 * @param chunk
	 *            the chunk to check; must not be {@code null}
	 */
	public void logWarningIfOversized(ChunkBuilder.ChunkData chunk) {
		String text = chunk.text() != null ? chunk.text() : "";
		int estimatedTokens = text.length() / CHARS_PER_TOKEN;
		if (estimatedTokens > RECOMMENDED_TOKEN_THRESHOLD) {
			log.warn("Chunk '{}' exceeds recommended size: ~{} estimated tokens (threshold: {})", chunk.id(),
					estimatedTokens, RECOMMENDED_TOKEN_THRESHOLD);
		}
	}

	// ── Inner record ─────────────────────────────────────────────────────

	/**
	 * Immutable snapshot of chunk size metrics.
	 *
	 * @param estimatedTokens
	 *            approximate token count (~4 chars/token)
	 * @param charLength
	 *            raw character count of the chunk text
	 * @param metadataKeyCount
	 *            number of metadata keys stored with the chunk
	 * @param exceedsRecommendedSize
	 *            {@code true} when estimatedTokens exceeds the recommended
	 *            threshold
	 */
	public record ChunkStats(int estimatedTokens, int charLength, int metadataKeyCount,
			boolean exceedsRecommendedSize) {
	}
}
