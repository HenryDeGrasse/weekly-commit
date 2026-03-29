package com.weeklycommit.ai.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.ai.evidence.ConfidenceTierCalculator.ConfidenceTier;
import com.weeklycommit.ai.rag.PineconeClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfidenceTierCalculator}.
 *
 * <p>
 * Covers all four tier conditions for both the Pinecone-match overload and the
 * StructuredEvidence overload.
 */
class ConfidenceTierCalculatorTest {

	private ConfidenceTierCalculator calculator;

	@BeforeEach
	void setUp() {
		calculator = new ConfidenceTierCalculator();
	}

	// ── Pinecone matches overload ─────────────────────────────────────────

	@Test
	void calculate_nullMatches_returnsInsufficient() {
		assertThat(calculator.calculate((List<PineconeClient.PineconeMatch>) null))
				.isEqualTo(ConfidenceTier.INSUFFICIENT);
	}

	@Test
	void calculate_emptyMatches_returnsInsufficient() {
		assertThat(calculator.calculate(List.of())).isEqualTo(ConfidenceTier.INSUFFICIENT);
	}

	@Test
	void calculate_threeHighScoreMatches_returnsHigh() {
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.9), match(0.85), match(0.95));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.HIGH);
	}

	@Test
	void calculate_exactlyThreeHighScoreMatches_returnsHigh() {
		// Boundary: exactly 3 matches above 0.8 → HIGH
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.81), match(0.82), match(0.83));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.HIGH);
	}

	@Test
	void calculate_twoHighScoreMatches_returnsMedium() {
		// 2 high-score matches: HIGH condition not met (need ≥3), but MEDIUM met (≥1)
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.9), match(0.85));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.MEDIUM);
	}

	@Test
	void calculate_oneHighScoreMatch_returnsMedium() {
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.9));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.MEDIUM);
	}

	@Test
	void calculate_fiveModerateScoreMatches_returnsMedium() {
		// No match above 0.8, but 5 above 0.5 → MEDIUM secondary condition
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.75), match(0.65), match(0.55), match(0.60),
				match(0.70));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.MEDIUM);
	}

	@Test
	void calculate_onlyLowScoringMatches_returnsLow() {
		// All scores below 0.5
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.3), match(0.4), match(0.2));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.LOW);
	}

	@Test
	void calculate_fewerThanFiveModerateMatches_returnsLow() {
		// 4 moderate matches (0.5–0.8 range) — not enough for MEDIUM secondary
		List<PineconeClient.PineconeMatch> matches = List.of(match(0.75), match(0.65), match(0.55), match(0.60));
		assertThat(calculator.calculate(matches)).isEqualTo(ConfidenceTier.LOW);
	}

	// ── StructuredEvidence overload ───────────────────────────────────────

	@Test
	void calculate_nullEvidence_returnsInsufficient() {
		assertThat(calculator.calculate((StructuredEvidence) null)).isEqualTo(ConfidenceTier.INSUFFICIENT);
	}

	@Test
	void calculate_evidenceWithNoSemanticMatches_returnsInsufficient() {
		StructuredEvidence evidence = new StructuredEvidence(null, null, List.of(), null);
		assertThat(calculator.calculate(evidence)).isEqualTo(ConfidenceTier.INSUFFICIENT);
	}

	@Test
	void calculate_evidenceWithThreeHighMatches_returnsHigh() {
		List<StructuredEvidence.SemanticMatch> semanticMatches = List.of(semanticMatch(0.9), semanticMatch(0.85),
				semanticMatch(0.95));
		StructuredEvidence evidence = new StructuredEvidence(null, null, semanticMatches, null);
		assertThat(calculator.calculate(evidence)).isEqualTo(ConfidenceTier.HIGH);
	}

	@Test
	void calculate_evidenceWithOneHighMatch_returnsMedium() {
		List<StructuredEvidence.SemanticMatch> semanticMatches = List.of(semanticMatch(0.9));
		StructuredEvidence evidence = new StructuredEvidence(null, null, semanticMatches, null);
		assertThat(calculator.calculate(evidence)).isEqualTo(ConfidenceTier.MEDIUM);
	}

	@Test
	void calculate_evidenceWithFiveModerateMatches_returnsMedium() {
		List<StructuredEvidence.SemanticMatch> semanticMatches = List.of(semanticMatch(0.75), semanticMatch(0.65),
				semanticMatch(0.55), semanticMatch(0.60), semanticMatch(0.70));
		StructuredEvidence evidence = new StructuredEvidence(null, null, semanticMatches, null);
		assertThat(calculator.calculate(evidence)).isEqualTo(ConfidenceTier.MEDIUM);
	}

	@Test
	void calculate_evidenceWithOnlyLowMatches_returnsLow() {
		List<StructuredEvidence.SemanticMatch> semanticMatches = List.of(semanticMatch(0.3), semanticMatch(0.4));
		StructuredEvidence evidence = new StructuredEvidence(null, null, semanticMatches, null);
		assertThat(calculator.calculate(evidence)).isEqualTo(ConfidenceTier.LOW);
	}

	// ── ConfidenceTier enum ────────────────────────────────────────────────

	@Test
	void confidenceTier_displayLabels_areNonBlank() {
		for (ConfidenceTier tier : ConfidenceTier.values()) {
			assertThat(tier.getDisplayLabel()).isNotBlank();
		}
	}

	// ── Private helpers ────────────────────────────────────────────────────

	private static PineconeClient.PineconeMatch match(double score) {
		return new PineconeClient.PineconeMatch("id-" + score, score, Map.of());
	}

	private static StructuredEvidence.SemanticMatch semanticMatch(double score) {
		return new StructuredEvidence.SemanticMatch("commit", "id-" + score, score, "2025-01-06", "text");
	}
}
