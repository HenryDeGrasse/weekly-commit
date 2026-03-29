package com.weeklycommit.ai.rag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Generates sparse vector representations from text using a TF (term-frequency)
 * approach suitable for Pinecone's native sparse-dense hybrid search.
 *
 * <p>
 * The encoder maps each token to a non-negative integer index (derived from the
 * token's hash) and weights it by its normalized term frequency within the
 * document. Domain-specific normalisations are applied before hashing to
 * improve retrieval precision for planning terminology.
 *
 * <p>
 * This is a local computation — no external API calls are made.
 */
@Component
public class SparseEncoder {

	/**
	 * Domain-specific token normalisation map. Keys are lowercase tokens; values
	 * are their canonical forms. Applied after basic tokenisation so that
	 * abbreviations and multi-word synonyms collapse to the same index.
	 */
	private static final Map<String, String> DOMAIN_NORMALIZATIONS;

	static {
		Map<String, String> m = new LinkedHashMap<>();
		// Carry-forward abbreviation
		m.put("cf", "carry_forward");
		// Chess piece names — suffixed to reduce false positives with common words
		m.put("king", "king_chess_piece");
		m.put("queen", "queen_chess_piece");
		m.put("bishop", "bishop_chess_piece");
		m.put("rook", "rook_chess_piece");
		m.put("knight", "knight_chess_piece");
		m.put("pawn", "pawn_chess_piece");
		// RCDO acronym
		m.put("rcdo", "rally_cry_defining_objective_outcome");
		m.put("rc", "rally_cry");
		DOMAIN_NORMALIZATIONS = Collections.unmodifiableMap(m);
	}

	/**
	 * Encodes {@code text} into a sparse vector mapping token-index → TF weight.
	 *
	 * @param text
	 *            the text to encode; may be {@code null} or blank
	 * @return a sparse vector (never {@code null}; empty when no meaningful tokens
	 *         found)
	 */
	public Map<Integer, Float> encode(String text) {
		if (text == null || text.isBlank()) {
			return Collections.emptyMap();
		}

		// Collapse multi-word domain terms before tokenising
		String preprocessed = preprocess(text);

		// Tokenise: lowercase, split on non-alphanumeric/underscore characters
		String[] rawTokens = preprocessed.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+");

		// Count per-token occurrences (skip single-char tokens — typically noise)
		Map<String, Integer> counts = new LinkedHashMap<>();
		int total = 0;
		for (String raw : rawTokens) {
			if (raw.length() > 1) {
				String normalized = DOMAIN_NORMALIZATIONS.getOrDefault(raw, raw);
				counts.merge(normalized, 1, Integer::sum);
				total++;
			}
		}

		if (total == 0) {
			return Collections.emptyMap();
		}

		// Build sparse vector: non-negative token index → TF weight
		Map<Integer, Float> result = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			int idx = tokenIndex(entry.getKey());
			float tf = (float) entry.getValue() / total;
			// Sum weights on hash collision (rare in practice for typical vocab sizes)
			result.merge(idx, tf, Float::sum);
		}

		return Collections.unmodifiableMap(result);
	}

	// ── Private helpers ──────────────────────────────────────────────────

	/**
	 * Collapses multi-word domain phrases to underscore-joined tokens so they
	 * survive whitespace-based tokenisation as a single term.
	 */
	private String preprocess(String text) {
		return text.replace("carry-forward", "carry_forward").replace("Carry-Forward", "carry_forward")
				.replace("carry forward", "carry_forward").replace("Carry Forward", "carry_forward")
				.replace("Rally Cry", "rally_cry").replace("rally cry", "rally_cry")
				.replace("Defining Objective", "defining_objective")
				.replace("defining objective", "defining_objective");
	}

	/**
	 * Maps a token string to a non-negative sparse-vector index.
	 *
	 * <p>
	 * Uses the token hash code with the sign bit masked off ({@code & 0x7FFFFFFF})
	 * to guarantee a non-negative value and avoid {@link Integer#MIN_VALUE}
	 * wrapping.
	 *
	 * <p>
	 * Package-private for testing.
	 */
	static int tokenIndex(String token) {
		return token.hashCode() & 0x7FFFFFFF;
	}
}
