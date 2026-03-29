package com.weeklycommit.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Domain-specific query rewriter for the RAG pipeline.
 *
 * <p>
 * Normalises user queries before embedding so that the embedding model sees
 * canonical, domain-consistent phrasing. All transformations are pure string
 * operations — no LLM calls are made.
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>{@link #rewrite} — acronym expansion, chess-term normalisation, time
 * expression normalisation, shorthand expansion, and filler-word removal.</li>
 * <li>{@link #decompose} — splits compound (multi-hop) questions into
 * independently embeddable sub-queries, capped at
 * {@link #MAX_SUB_QUERIES}.</li>
 * </ul>
 */
@Component
public class QueryRewriter {

	/**
	 * Maximum sub-queries returned by {@link #decompose} to prevent pathological
	 * Pinecone call expansion (each sub-query requires a separate embedding +
	 * Pinecone call).
	 */
	static final int MAX_SUB_QUERIES = 3;

	// ── Acronym expansion patterns ────────────────────────────────────────
	private static final Pattern CF_PATTERN = Pattern.compile("\\bCF\\b");
	private static final Pattern RCDO_PATTERN = Pattern.compile("\\bRCDO\\b");

	// ── Chess-term normalisation patterns ─────────────────────────────────
	/** "king commit" → "KING chess piece commit" (case-insensitive). */
	private static final Pattern KING_COMMIT_PATTERN = Pattern.compile("\\bking commit\\b", Pattern.CASE_INSENSITIVE);
	/** "queens" → "QUEEN chess piece" (case-insensitive, whole word). */
	private static final Pattern QUEENS_PATTERN = Pattern.compile("\\bqueens\\b", Pattern.CASE_INSENSITIVE);

	// ── Time expression normalisation patterns ────────────────────────────
	/** "prev week" / "previous week" → "last week". */
	private static final Pattern PREV_WEEK_PATTERN = Pattern.compile("\\bprev(?:ious)?\\s+week\\b",
			Pattern.CASE_INSENSITIVE);
	/** "prior week" → "last week". */
	private static final Pattern PRIOR_WEEK_PATTERN = Pattern.compile("\\bprior\\s+week\\b", Pattern.CASE_INSENSITIVE);

	// ── Shorthand expansion patterns ──────────────────────────────────────
	/** "pts" → "points" (whole word, case-insensitive). */
	private static final Pattern PTS_PATTERN = Pattern.compile("\\bpts\\b", Pattern.CASE_INSENSITIVE);
	/** "est" → "estimate" (whole word, case-insensitive). */
	private static final Pattern EST_PATTERN = Pattern.compile("\\best\\b", Pattern.CASE_INSENSITIVE);

	// ── Filler-word patterns ──────────────────────────────────────────────
	private static final List<Pattern> FILLER_PATTERNS = List.of(
			Pattern.compile("can you tell me[,\\s]*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("I want to know[,\\s]*", Pattern.CASE_INSENSITIVE),
			Pattern.compile("please show me[,\\s]*", Pattern.CASE_INSENSITIVE));

	// ── Compound-question detection / splitting ───────────────────────────
	private static final Pattern AND_ALSO_PATTERN = Pattern.compile("\\band also\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern AS_WELL_AS_PATTERN = Pattern.compile("\\bas well as\\b", Pattern.CASE_INSENSITIVE);
	/**
	 * Matches " plus " (surrounded by whitespace) to avoid false positives on
	 * numeric expressions like "1+1".
	 */
	private static final Pattern PLUS_CONJUNCTION_PATTERN = Pattern.compile("\\s+plus\\s+", Pattern.CASE_INSENSITIVE);

	// ── Public API ────────────────────────────────────────────────────────

	/**
	 * Rewrites {@code originalQuery} with domain-specific normalisations.
	 *
	 * <p>
	 * Transformations applied (in order):
	 * <ol>
	 * <li>Acronym expansion: CF → carry-forward, RCDO → Rally Cry Defining
	 * Objective Outcome hierarchy.</li>
	 * <li>Chess-term normalisation: "king commit" → "KING chess piece commit",
	 * "queens" → "QUEEN chess piece".</li>
	 * <li>Time expression normalisation: "prev/previous/prior week" → "last
	 * week".</li>
	 * <li>Shorthand expansion: "pts" → "points", "est" → "estimate".</li>
	 * <li>Filler-word removal: "can you tell me", "I want to know", "please show
	 * me".</li>
	 * <li>Whitespace normalisation (trim + collapse runs).</li>
	 * </ol>
	 *
	 * @param originalQuery
	 *            the user's raw question; returned unchanged if {@code null} or
	 *            blank
	 * @return the normalised query string, never {@code null} when input is
	 *         non-null
	 */
	public String rewrite(String originalQuery) {
		if (originalQuery == null || originalQuery.isBlank()) {
			return originalQuery;
		}

		String q = originalQuery;

		// 1. Acronym expansion
		q = CF_PATTERN.matcher(q).replaceAll("carry-forward");
		q = RCDO_PATTERN.matcher(q).replaceAll("Rally Cry Defining Objective Outcome hierarchy");

		// 2. Chess-term normalisation
		q = KING_COMMIT_PATTERN.matcher(q).replaceAll("KING chess piece commit");
		q = QUEENS_PATTERN.matcher(q).replaceAll("QUEEN chess piece");

		// 3. Time expression normalisation
		q = PREV_WEEK_PATTERN.matcher(q).replaceAll("last week");
		q = PRIOR_WEEK_PATTERN.matcher(q).replaceAll("last week");

		// 4. Shorthand expansion
		q = PTS_PATTERN.matcher(q).replaceAll("points");
		q = EST_PATTERN.matcher(q).replaceAll("estimate");

		// 5. Filler-word removal
		for (Pattern filler : FILLER_PATTERNS) {
			q = filler.matcher(q).replaceAll("");
		}

		// 6. Whitespace normalisation
		q = q.trim().replaceAll("\\s{2,}", " ");

		return q;
	}

	/**
	 * Decomposes {@code query} into sub-queries for multi-hop questions.
	 *
	 * <p>
	 * A query is considered compound if it contains at least one of:
	 * <ul>
	 * <li>"and also"</li>
	 * <li>"as well as"</li>
	 * <li>" plus " (as a conjunction, surrounded by whitespace)</li>
	 * <li>more than one {@code ?}</li>
	 * </ul>
	 *
	 * <p>
	 * Compound queries are split into at most {@link #MAX_SUB_QUERIES} sub-queries.
	 * If the split would produce more, only the first {@value #MAX_SUB_QUERIES} are
	 * kept.
	 *
	 * <p>
	 * Simple queries return a single-element list containing the original query.
	 *
	 * @param query
	 *            the (possibly rewritten) query; returns an empty list when
	 *            {@code null} or blank
	 * @return immutable list of sub-queries, never {@code null}
	 */
	public List<String> decompose(String query) {
		if (query == null || query.isBlank()) {
			return List.of();
		}

		if (!isCompoundQuery(query)) {
			return List.of(query);
		}

		// Try splitting strategies in priority order; take the first that produces 2+
		// parts
		List<String> parts = trySplit(query, AND_ALSO_PATTERN);
		if (parts.size() < 2) {
			parts = trySplit(query, AS_WELL_AS_PATTERN);
		}
		if (parts.size() < 2) {
			parts = trySplit(query, PLUS_CONJUNCTION_PATTERN);
		}
		if (parts.size() < 2) {
			parts = splitOnQuestionMarks(query);
		}

		// If we still couldn't split meaningfully, treat as a single query
		if (parts.size() < 2) {
			return List.of(query);
		}

		// Cap at MAX_SUB_QUERIES
		if (parts.size() > MAX_SUB_QUERIES) {
			parts = parts.subList(0, MAX_SUB_QUERIES);
		}

		return List.copyOf(parts);
	}

	// ── Private helpers ────────────────────────────────────────────────────

	private boolean isCompoundQuery(String query) {
		long questionMarkCount = query.chars().filter(c -> c == '?').count();
		return questionMarkCount > 1 || AND_ALSO_PATTERN.matcher(query).find()
				|| AS_WELL_AS_PATTERN.matcher(query).find() || PLUS_CONJUNCTION_PATTERN.matcher(query).find();
	}

	/**
	 * Splits the query on the given pattern and returns non-blank trimmed parts.
	 */
	private List<String> trySplit(String query, Pattern splitter) {
		String[] rawParts = splitter.split(query);
		List<String> result = new ArrayList<>(rawParts.length);
		for (String part : rawParts) {
			String trimmed = part.trim();
			if (!trimmed.isBlank()) {
				result.add(trimmed);
			}
		}
		return result;
	}

	/**
	 * Splits the query on {@code ?} boundaries and reconstructs each part as a
	 * question.
	 */
	private List<String> splitOnQuestionMarks(String query) {
		String[] rawParts = query.split("\\?");
		List<String> result = new ArrayList<>(rawParts.length);
		for (String part : rawParts) {
			String trimmed = part.trim();
			if (!trimmed.isBlank()) {
				// Re-append "?" unless the trimmed part already ends with punctuation
				if (!trimmed.endsWith("?") && !trimmed.endsWith(".") && !trimmed.endsWith("!")) {
					trimmed = trimmed + "?";
				}
				result.add(trimmed);
			}
		}
		return result;
	}
}
