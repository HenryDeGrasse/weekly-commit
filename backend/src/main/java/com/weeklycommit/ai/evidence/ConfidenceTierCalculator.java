package com.weeklycommit.ai.evidence;

import com.weeklycommit.ai.rag.PineconeClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure-computation service that maps Pinecone retrieval evidence to a
 * structured confidence tier for RAG answers.
 *
 * <p>
 * Named {@code ConfidenceTier} (distinct from
 * {@code CalibrationConfidenceTier}) because it measures the quality of the
 * retrieved evidence, not the amount of historical data available.
 */
@Component
public class ConfidenceTierCalculator {

	/** Score threshold for a "high-quality" match. */
	static final double HIGH_SCORE_THRESHOLD = 0.8;

	/** Score threshold for a "moderate-quality" match. */
	static final double MODERATE_SCORE_THRESHOLD = 0.5;

	/** Minimum high-score matches for the HIGH tier. */
	static final int HIGH_TIER_MIN_HIGH_MATCHES = 3;

	/** Minimum moderate-score matches for the MEDIUM tier (secondary condition). */
	static final int MEDIUM_TIER_MIN_MODERATE_MATCHES = 5;

	// ── Public API ────────────────────────────────────────────────────────

	/**
	 * Calculates the confidence tier from a list of Pinecone retrieval matches.
	 *
	 * <ul>
	 * <li>{@link ConfidenceTier#HIGH} — at least
	 * {@value #HIGH_TIER_MIN_HIGH_MATCHES} matches with score &gt;
	 * {@value #HIGH_SCORE_THRESHOLD}</li>
	 * <li>{@link ConfidenceTier#MEDIUM} — at least one match &gt;
	 * {@value #HIGH_SCORE_THRESHOLD}, OR at least
	 * {@value #MEDIUM_TIER_MIN_MODERATE_MATCHES} matches &gt;
	 * {@value #MODERATE_SCORE_THRESHOLD}</li>
	 * <li>{@link ConfidenceTier#LOW} — only low-scoring matches (&lt;
	 * {@value #MODERATE_SCORE_THRESHOLD})</li>
	 * <li>{@link ConfidenceTier#INSUFFICIENT} — no matches</li>
	 * </ul>
	 *
	 * @param matches
	 *            Pinecone matches (may be null or empty)
	 * @return confidence tier, never {@code null}
	 */
	public ConfidenceTier calculate(List<PineconeClient.PineconeMatch> matches) {
		if (matches == null || matches.isEmpty()) {
			return ConfidenceTier.INSUFFICIENT;
		}

		long highCount = matches.stream().filter(m -> m.score() > HIGH_SCORE_THRESHOLD).count();
		long moderateCount = matches.stream().filter(m -> m.score() > MODERATE_SCORE_THRESHOLD).count();

		if (highCount >= HIGH_TIER_MIN_HIGH_MATCHES) {
			return ConfidenceTier.HIGH;
		}
		if (highCount >= 1 || moderateCount >= MEDIUM_TIER_MIN_MODERATE_MATCHES) {
			return ConfidenceTier.MEDIUM;
		}
		if (moderateCount > 0) {
			return ConfidenceTier.LOW;
		}
		return ConfidenceTier.LOW;
	}

	/**
	 * Calculates the confidence tier from a {@link StructuredEvidence} bundle. Uses
	 * the semantic matches embedded in the evidence; other evidence dimensions (SQL
	 * facts, lineage) are currently not factored but may be in the future.
	 *
	 * @param evidence
	 *            structured evidence (may be null)
	 * @return confidence tier, never {@code null}
	 */
	public ConfidenceTier calculate(StructuredEvidence evidence) {
		if (evidence == null || evidence.semanticMatches() == null || evidence.semanticMatches().isEmpty()) {
			return ConfidenceTier.INSUFFICIENT;
		}

		// Convert SemanticMatch scores to synthetic PineconeMatch list for reuse
		long highCount = evidence.semanticMatches().stream().filter(m -> m.score() > HIGH_SCORE_THRESHOLD).count();
		long moderateCount = evidence.semanticMatches().stream().filter(m -> m.score() > MODERATE_SCORE_THRESHOLD)
				.count();

		if (highCount >= HIGH_TIER_MIN_HIGH_MATCHES) {
			return ConfidenceTier.HIGH;
		}
		if (highCount >= 1 || moderateCount >= MEDIUM_TIER_MIN_MODERATE_MATCHES) {
			return ConfidenceTier.MEDIUM;
		}
		return ConfidenceTier.LOW;
	}

	// ── Enum ──────────────────────────────────────────────────────────────

	/**
	 * Evidence-quality confidence tier for a RAG answer.
	 *
	 * <p>
	 * Distinct from {@code CalibrationConfidenceTier} (which measures
	 * data-sufficiency for the statistical calibration model).
	 */
	public enum ConfidenceTier {

		/** Strong retrieval evidence — ≥3 high-score matches. */
		HIGH("High confidence"),

		/** Moderate retrieval evidence. */
		MEDIUM("Medium confidence"),

		/** Weak retrieval evidence. */
		LOW("Low confidence"),

		/** No relevant matches found. */
		INSUFFICIENT("Insufficient evidence");

		private final String displayLabel;

		ConfidenceTier(String displayLabel) {
			this.displayLabel = displayLabel;
		}

		/** Human-readable label suitable for display in the UI. */
		public String getDisplayLabel() {
			return displayLabel;
		}
	}
}
