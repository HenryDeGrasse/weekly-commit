package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QueryRewriter}.
 *
 * <p>
 * All tests are pure in-process — no mocks or Spring context required.
 */
class QueryRewriterTest {

	private QueryRewriter rewriter;

	@BeforeEach
	void setUp() {
		rewriter = new QueryRewriter();
	}

	// ── rewrite: acronym expansion ────────────────────────────────────────

	@Test
	void rewrite_expandsAcronym_CF() {
		String result = rewriter.rewrite("What are the CF items for this week?");
		assertThat(result).contains("carry-forward");
		assertThat(result).doesNotContain(" CF ");
	}

	@Test
	void rewrite_expandsAcronym_CF_caseExact() {
		// Only exact "CF" (uppercase) should be expanded; "cf" (lowercase) should not
		String resultUpper = rewriter.rewrite("List CF tasks");
		assertThat(resultUpper).contains("carry-forward");

		// Lowercase "cf" should NOT be expanded (pattern is case-sensitive for CF)
		String resultLower = rewriter.rewrite("What cf tasks remain?");
		assertThat(resultLower).contains("cf");
		assertThat(resultLower).doesNotContain("carry-forward");
	}

	@Test
	void rewrite_expandsAcronym_RCDO() {
		String result = rewriter.rewrite("Explain the RCDO hierarchy for our team");
		assertThat(result).contains("Rally Cry Defining Objective Outcome hierarchy");
		assertThat(result).doesNotContain(" RCDO ");
	}

	// ── rewrite: chess-term normalisation ────────────────────────────────

	@Test
	void rewrite_normalizesChessTerm_kingCommit() {
		String result = rewriter.rewrite("Show me the king commit for last week");
		assertThat(result).containsIgnoringCase("KING chess piece commit");
		assertThat(result).doesNotContain("king commit");
	}

	@Test
	void rewrite_normalizesChessTerm_kingCommit_caseInsensitive() {
		String result = rewriter.rewrite("What is the KING COMMIT status?");
		assertThat(result).containsIgnoringCase("KING chess piece commit");
	}

	@Test
	void rewrite_normalizesChessTerm_queens() {
		String result = rewriter.rewrite("How many queens did the team complete?");
		assertThat(result).contains("QUEEN chess piece");
		assertThat(result).doesNotContain("queens");
	}

	// ── rewrite: time expression normalisation ────────────────────────────

	@Test
	void rewrite_normalizesTimeExpression_prevWeek() {
		String result = rewriter.rewrite("What happened prev week?");
		assertThat(result).contains("last week");
		assertThat(result).doesNotContain("prev week");
	}

	@Test
	void rewrite_normalizesTimeExpression_previousWeek() {
		String result = rewriter.rewrite("Show me previous week results");
		assertThat(result).contains("last week");
		assertThat(result).doesNotContain("previous week");
	}

	@Test
	void rewrite_normalizesTimeExpression_priorWeek() {
		String result = rewriter.rewrite("What were the prior week commitments?");
		assertThat(result).contains("last week");
		assertThat(result).doesNotContain("prior week");
	}

	@Test
	void rewrite_leavesLastWeek_unchanged() {
		// "last week" is already normalised — should not be altered
		String result = rewriter.rewrite("What did the team commit to last week?");
		assertThat(result).contains("last week");
	}

	// ── rewrite: shorthand expansion ─────────────────────────────────────

	@Test
	void rewrite_expandsShorthand_pts() {
		String result = rewriter.rewrite("How many pts did the team score?");
		assertThat(result).contains("points");
		assertThat(result).doesNotContain(" pts ");
	}

	@Test
	void rewrite_expandsShorthand_est() {
		String result = rewriter.rewrite("What is the est for this task?");
		assertThat(result).contains("estimate");
		assertThat(result).doesNotContain(" est ");
	}

	// ── rewrite: filler-word removal ──────────────────────────────────────

	@Test
	void rewrite_stripsFiller_canYouTellMe() {
		String result = rewriter.rewrite("Can you tell me what the team committed to last week?");
		assertThat(result).doesNotContainIgnoringCase("can you tell me");
		assertThat(result).contains("what the team committed to last week");
	}

	@Test
	void rewrite_stripsFiller_iWantToKnow() {
		String result = rewriter.rewrite("I want to know the commit history for Alice");
		assertThat(result).doesNotContainIgnoringCase("I want to know");
		assertThat(result).contains("the commit history for Alice");
	}

	@Test
	void rewrite_stripsFiller_pleaseShowMe() {
		String result = rewriter.rewrite("Please show me the weekly results");
		assertThat(result).doesNotContainIgnoringCase("please show me");
		assertThat(result).contains("the weekly results");
	}

	// ── rewrite: simple query passthrough ────────────────────────────────

	@Test
	void rewrite_simpleQuery_noUnnecessaryChange() {
		String input = "What did the team commit to last week?";
		String result = rewriter.rewrite(input);
		// Should be unchanged (no acronyms, chess terms, fillers, or shorthand)
		assertThat(result).isEqualTo(input);
	}

	@Test
	void rewrite_nullInput_returnsNull() {
		assertThat(rewriter.rewrite(null)).isNull();
	}

	@Test
	void rewrite_blankInput_returnsBlank() {
		String input = "   ";
		assertThat(rewriter.rewrite(input)).isEqualTo(input);
	}

	// ── decompose: simple query passthrough ──────────────────────────────

	@Test
	void decompose_simpleQuery_returnsSingleElement() {
		List<String> result = rewriter.decompose("What did the team commit to last week?");
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo("What did the team commit to last week?");
	}

	@Test
	void decompose_nullQuery_returnsEmptyList() {
		assertThat(rewriter.decompose(null)).isEmpty();
	}

	@Test
	void decompose_blankQuery_returnsEmptyList() {
		assertThat(rewriter.decompose("   ")).isEmpty();
	}

	// ── decompose: multi-hop detection and splitting ──────────────────────

	@Test
	void decompose_andAlso_splitsTwoSubQueries() {
		String query = "What did Alice commit last week and also what are Bob's carry-forward patterns?";
		List<String> result = rewriter.decompose(query);
		assertThat(result).hasSize(2);
		assertThat(result.get(0)).contains("Alice");
		assertThat(result.get(1)).contains("Bob");
	}

	@Test
	void decompose_asWellAs_splitsTwoSubQueries() {
		String query = "Show me the king commits as well as the carry-forward items";
		List<String> result = rewriter.decompose(query);
		assertThat(result).hasSize(2);
	}

	@Test
	void decompose_plusConjunction_splitsTwoSubQueries() {
		String query = "What are the team's commitments plus the carry-forward risks?";
		List<String> result = rewriter.decompose(query);
		assertThat(result).hasSize(2);
	}

	@Test
	void decompose_multipleQuestionMarks_splitsTwoSubQueries() {
		String query = "What did Alice commit last week? What are Bob's carry-forward patterns?";
		List<String> result = rewriter.decompose(query);
		assertThat(result).hasSize(2);
		assertThat(result.get(0)).contains("Alice");
		assertThat(result.get(1)).contains("Bob");
	}

	@Test
	void decompose_subQueriesAreNonBlank() {
		String query = "What did Alice commit last week and also what are Bob's patterns?";
		List<String> result = rewriter.decompose(query);
		assertThat(result).allSatisfy(q -> assertThat(q).isNotBlank());
	}

	// ── decompose: MAX_SUB_QUERIES cap ───────────────────────────────────

	@Test
	void decompose_manySubQueries_capsAtMaxSubQueries() {
		// Construct a query that splits into 4 parts when split on "and also"
		String query = "What did Alice commit and also what did Bob commit and also what did Carol commit "
				+ "and also what did Dave commit?";
		List<String> result = rewriter.decompose(query);
		assertThat(result).hasSizeLessThanOrEqualTo(QueryRewriter.MAX_SUB_QUERIES);
		assertThat(result).hasSizeLessThanOrEqualTo(3);
	}

	@Test
	void decompose_maxSubQueriesConstant_isThree() {
		assertThat(QueryRewriter.MAX_SUB_QUERIES).isEqualTo(3);
	}

	// ── decompose: result is immutable ───────────────────────────────────

	@Test
	void decompose_returnedList_isImmutable() {
		List<String> result = rewriter.decompose("What did the team commit to last week?");
		assertThat(result).isUnmodifiable();
	}

	@Test
	void decompose_multiHop_returnedList_isImmutable() {
		List<String> result = rewriter.decompose("Alice's commits and also Bob's patterns?");
		assertThat(result).isUnmodifiable();
	}
}
