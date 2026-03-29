package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SparseEncoder}.
 *
 * <p>
 * SparseEncoder is pure computation — no Spring context needed.
 */
class SparseEncoderTest {

	private final SparseEncoder encoder = new SparseEncoder();

	// ── Null / blank input ───────────────────────────────────────────────

	@Test
	void encode_nullInput_returnsEmptyMap() {
		assertThat(encoder.encode(null)).isEmpty();
	}

	@Test
	void encode_blankInput_returnsEmptyMap() {
		assertThat(encoder.encode("")).isEmpty();
		assertThat(encoder.encode("   ")).isEmpty();
	}

	// ── Basic tokenisation ───────────────────────────────────────────────

	@Test
	void encode_plainText_returnsNonEmptyMap() {
		Map<Integer, Float> result = encoder.encode("deploy the service to production");
		assertThat(result).isNotEmpty();
	}

	@Test
	void encode_weightsArePositive() {
		Map<Integer, Float> result = encoder.encode("deploy the service to production");
		result.values().forEach(w -> assertThat(w).isPositive());
	}

	@Test
	void encode_sameTextTwice_identicalResults() {
		String text = "plan commit king queen";
		assertThat(encoder.encode(text)).isEqualTo(encoder.encode(text));
	}

	@Test
	void encode_repeatedTerm_hasHigherWeight() {
		// "commit" appears twice in the second text — its TF weight should be higher
		Map<Integer, Float> single = encoder.encode("deploy commit to production");
		Map<Integer, Float> doubled = encoder.encode("commit to commit the deployment");
		// "commit" normalises to "commit" (no domain expansion), so use tokenIndex
		// directly
		int commitIdx = SparseEncoder.tokenIndex("commit");
		assertThat(single).containsKey(commitIdx);
		assertThat(doubled).containsKey(commitIdx);
		assertThat(doubled.get(commitIdx)).isGreaterThan(single.get(commitIdx));
	}

	// ── Domain-specific normalisation: carry-forward ─────────────────────

	@Test
	void encode_cfAbbreviation_normalisesToCarryForward() {
		Map<Integer, Float> result = encoder.encode("cf task needs attention");
		int cfIdx = SparseEncoder.tokenIndex("carry_forward");
		assertThat(result).containsKey(cfIdx);
	}

	@Test
	void encode_hyphenatedCarryForward_normalisesToCarryForward() {
		Map<Integer, Float> result = encoder.encode("carry-forward risk detected");
		int cfIdx = SparseEncoder.tokenIndex("carry_forward");
		assertThat(result).containsKey(cfIdx);
	}

	@Test
	void encode_spacedCarryForward_normalisesToCarryForward() {
		Map<Integer, Float> result = encoder.encode("carry forward this task");
		int cfIdx = SparseEncoder.tokenIndex("carry_forward");
		assertThat(result).containsKey(cfIdx);
	}

	@Test
	void encode_cfAndCarryForwardSameIndex() {
		// Both "cf" (abbreviation) and "carry forward" (phrase) should produce the
		// same token index for carry_forward
		Map<Integer, Float> cfResult = encoder.encode("cf task this week");
		Map<Integer, Float> cfResult2 = encoder.encode("carry forward task this week");
		int cfIdx = SparseEncoder.tokenIndex("carry_forward");
		assertThat(cfResult).containsKey(cfIdx);
		assertThat(cfResult2).containsKey(cfIdx);
	}

	// ── Domain-specific normalisation: chess pieces ──────────────────────

	@Test
	void encode_kingNormalisesToKingChessPiece() {
		Map<Integer, Float> result = encoder.encode("king commit overcommit risk");
		assertThat(result).containsKey(SparseEncoder.tokenIndex("king_chess_piece"));
	}

	@Test
	void encode_queenNormalisesToQueenChessPiece() {
		Map<Integer, Float> result = encoder.encode("queen and bishop commits");
		assertThat(result).containsKey(SparseEncoder.tokenIndex("queen_chess_piece"));
		assertThat(result).containsKey(SparseEncoder.tokenIndex("bishop_chess_piece"));
	}

	@Test
	void encode_allChessPiecesNormalised() {
		Map<Integer, Float> result = encoder.encode("king queen bishop rook knight pawn");
		assertThat(result).containsKey(SparseEncoder.tokenIndex("king_chess_piece"));
		assertThat(result).containsKey(SparseEncoder.tokenIndex("queen_chess_piece"));
		assertThat(result).containsKey(SparseEncoder.tokenIndex("bishop_chess_piece"));
		assertThat(result).containsKey(SparseEncoder.tokenIndex("rook_chess_piece"));
		assertThat(result).containsKey(SparseEncoder.tokenIndex("knight_chess_piece"));
		assertThat(result).containsKey(SparseEncoder.tokenIndex("pawn_chess_piece"));
	}

	// ── Domain-specific normalisation: RCDO ──────────────────────────────

	@Test
	void encode_rcdoNormalisesToFullTerm() {
		Map<Integer, Float> result = encoder.encode("RCDO alignment missing");
		int rcdoIdx = SparseEncoder.tokenIndex("rally_cry_defining_objective_outcome");
		assertThat(result).containsKey(rcdoIdx);
	}

	// ── Sparse vector properties ─────────────────────────────────────────

	@Test
	void encode_indicesAreNonNegative() {
		Map<Integer, Float> result = encoder.encode("all commits linked to RCDO carry-forward king queen pawn");
		result.keySet().forEach(idx -> assertThat(idx).isGreaterThanOrEqualTo(0));
	}

	@Test
	void encode_returnedMapIsUnmodifiable() {
		Map<Integer, Float> result = encoder.encode("test text for planning");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.put(99, 0.5f))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	// ── tokenIndex static helper ─────────────────────────────────────────

	@Test
	void tokenIndex_neverNegative() {
		// Verify the hash masking works for edge-case strings
		assertThat(SparseEncoder.tokenIndex("")).isGreaterThanOrEqualTo(0);
		assertThat(SparseEncoder.tokenIndex("a")).isGreaterThanOrEqualTo(0);
		assertThat(SparseEncoder.tokenIndex("z".repeat(100))).isGreaterThanOrEqualTo(0);
	}

	@Test
	void tokenIndex_sameTokenSameIndex() {
		assertThat(SparseEncoder.tokenIndex("carry_forward")).isEqualTo(SparseEncoder.tokenIndex("carry_forward"));
	}
}
