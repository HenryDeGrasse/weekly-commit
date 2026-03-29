package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.repository.CarryForwardFactRepository;
import com.weeklycommit.domain.repository.ComplianceFactRepository;
import com.weeklycommit.domain.repository.RcdoWeekRollupRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamWeekRollupRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SqlQueryRouter}.
 *
 * <p>
 * Focuses on {@link SqlQueryRouter#canHandle} intent/keyword detection and the
 * graceful-degradation paths in {@link SqlQueryRouter#query}. Repository calls
 * are mocked.
 */
@ExtendWith(MockitoExtension.class)
class SqlQueryRouterTest {

	@Mock
	private TeamMembershipRepository teamMembershipRepo;

	@Mock
	private UserWeekFactRepository userWeekFactRepo;

	@Mock
	private TeamWeekRollupRepository teamWeekRollupRepo;

	@Mock
	private RcdoWeekRollupRepository rcdoWeekRollupRepo;

	@Mock
	private CarryForwardFactRepository carryForwardFactRepo;

	@Mock
	private ComplianceFactRepository complianceFactRepo;

	@Mock
	private AiProviderRegistry aiProviderRegistry;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private SqlQueryRouter router;

	@BeforeEach
	void setUp() {
		router = new SqlQueryRouter(teamMembershipRepo, userWeekFactRepo, teamWeekRollupRepo, rcdoWeekRollupRepo,
				carryForwardFactRepo, complianceFactRepo, aiProviderRegistry, objectMapper);
	}

	// ── canHandle: intent-based routing ─────────────────────────────────

	@Test
	void canHandle_analyticalIntent_returnsTrue() {
		assertThat(router.canHandle("analytical", List.of())).isTrue();
	}

	@Test
	void canHandle_complianceQueryIntent_returnsTrue() {
		assertThat(router.canHandle("compliance_query", List.of())).isTrue();
	}

	@Test
	void canHandle_statusQueryNoAggregationKeywords_returnsFalse() {
		assertThat(router.canHandle("status_query", List.of("commit", "team", "week"))).isFalse();
	}

	@Test
	void canHandle_statusQueryWithTotalKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("total", "points", "team"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithAverageKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("average", "carry-forward", "rate"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithHowManyKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("how many", "members", "locked"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithPercentageKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("percentage", "achieved"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithRateKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("rate", "reconciliation"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithTrendKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("trend", "points"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithCompareKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("compare", "teams"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithRankingKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("ranking", "top", "performers"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithMostKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("most", "carry", "forward"))).isTrue();
	}

	@Test
	void canHandle_statusQueryWithLeastKeyword_returnsTrue() {
		assertThat(router.canHandle("status_query", List.of("least", "committed"))).isTrue();
	}

	@Test
	void canHandle_otherIntent_returnsFalse() {
		assertThat(router.canHandle("risk_query", List.of("total"))).isFalse();
	}

	@Test
	void canHandle_nullIntent_returnsFalse() {
		assertThat(router.canHandle(null, List.of("total"))).isFalse();
	}

	@Test
	void canHandle_nullKeywords_analyticalIntentStillTrue() {
		assertThat(router.canHandle("analytical", null)).isTrue();
	}

	@Test
	void canHandle_statusQueryWithNullKeywords_returnsFalse() {
		assertThat(router.canHandle("status_query", null)).isFalse();
	}

	// ── query: graceful degradation ──────────────────────────────────────

	@Test
	void query_providerUnavailable_returnsUnavailable() {
		UUID teamId = UUID.randomUUID();
		// Provide a rollup so data is non-empty and the AI provider is actually called
		com.weeklycommit.domain.entity.TeamWeekRollup rollup = new com.weeklycommit.domain.entity.TeamWeekRollup();
		rollup.setWeekStart(java.time.LocalDate.of(2026, 3, 16));
		rollup.setMemberCount(4);
		rollup.setTotalPlannedPoints(20);
		rollup.setTotalAchievedPoints(16);
		rollup.setAvgCarryForwardRate(0.2);
		rollup.setLockedCount(3);
		rollup.setReconciledCount(3);
		rollup.setExceptionCount(0);
		when(teamMembershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(any(), any(), any())).thenReturn(List.of(rollup));
		when(aiProviderRegistry.generateSuggestion(any(), any())).thenReturn(AiSuggestionResult.unavailable());

		SqlQueryRouter.SqlQueryResult result = router.query("What is the average carry-forward rate?", "analytical",
				List.of("average", "rate"), teamId, "2026-03-16", "2026-03-22", UUID.randomUUID());

		assertThat(result.available()).isFalse();
		assertThat(result.answer()).isNull();
	}

	@Test
	void query_emptyReadModelData_returnsUnavailable() {
		UUID teamId = UUID.randomUUID();
		when(teamMembershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(any(), any(), any())).thenReturn(List.of());

		SqlQueryRouter.SqlQueryResult result = router.query("What is the average rate?", "analytical",
				List.of("average", "rate"), teamId, "2026-03-16", "2026-03-22", UUID.randomUUID());

		assertThat(result.available()).isFalse();
	}

	@Test
	void query_successfulResponse_returnsAnswer() throws Exception {
		UUID teamId = UUID.randomUUID();
		// No members, no data — but mock a rollup so data isn't empty
		com.weeklycommit.domain.entity.TeamWeekRollup rollup = new com.weeklycommit.domain.entity.TeamWeekRollup();
		rollup.setWeekStart(java.time.LocalDate.of(2026, 3, 16));
		rollup.setMemberCount(5);
		rollup.setTotalPlannedPoints(30);
		rollup.setTotalAchievedPoints(25);
		rollup.setAvgCarryForwardRate(0.2);
		rollup.setLockedCount(4);
		rollup.setReconciledCount(4);
		rollup.setExceptionCount(0);

		when(teamMembershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(any(), any(), any())).thenReturn(List.of(rollup));

		String llmPayload = objectMapper
				.writeValueAsString(java.util.Map.of("answer", "The team had an average carry-forward rate of 20%.",
						"dataSource", "teamWeekRollups", "confidence", 0.85));
		when(aiProviderRegistry.generateSuggestion(any(), any()))
				.thenReturn(new AiSuggestionResult(true, llmPayload, "rationale", 0.85, "model-v1"));

		SqlQueryRouter.SqlQueryResult result = router.query("What is the carry-forward rate?", "analytical",
				List.of("total", "rate"), teamId, "2026-03-16", "2026-03-22", UUID.randomUUID());

		assertThat(result.available()).isTrue();
		assertThat(result.answer()).contains("20%");
		assertThat(result.dataSource()).isEqualTo("teamWeekRollups");
		assertThat(result.confidence()).isEqualTo(0.85);
	}

	@Test
	void query_rcdoAnalyticalQuestion_usesRcdoWeekRollups() throws Exception {
		UUID teamId = UUID.randomUUID();
		com.weeklycommit.domain.entity.RcdoWeekRollup rcdoRollup = new com.weeklycommit.domain.entity.RcdoWeekRollup();
		rcdoRollup.setRcdoNodeId(UUID.randomUUID());
		rcdoRollup.setWeekStart(java.time.LocalDate.of(2026, 3, 16));
		rcdoRollup.setPlannedPoints(18);
		rcdoRollup.setAchievedPoints(12);
		rcdoRollup.setCommitCount(4);
		rcdoRollup.setTeamContributionBreakdown("{\"" + teamId + "\":18}");

		when(teamMembershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(any(), any(), any())).thenReturn(List.of());
		when(rcdoWeekRollupRepo.findAll()).thenReturn(List.of(rcdoRollup));

		String llmPayload = objectMapper.writeValueAsString(
				java.util.Map.of("answer", "The top RCDO node had 18 planned points during the selected week.",
						"dataSource", "rcdoWeekRollups", "confidence", 0.9));
		when(aiProviderRegistry.generateSuggestion(any(), any()))
				.thenReturn(new AiSuggestionResult(true, llmPayload, "rationale", 0.9, "model-v1"));

		SqlQueryRouter.SqlQueryResult result = router.query("Which RCDO nodes had the most points committed?",
				"analytical", List.of("RCDO", "most", "points"), teamId, "2026-03-16", "2026-03-22", UUID.randomUUID());

		assertThat(result.available()).isTrue();
		assertThat(result.dataSource()).isEqualTo("rcdoWeekRollups");
	}

	// ── SqlQueryResult record ────────────────────────────────────────────

	@Test
	void sqlQueryResult_unavailableFactory_setsCorrectFields() {
		SqlQueryRouter.SqlQueryResult result = SqlQueryRouter.SqlQueryResult.unavailable();
		assertThat(result.available()).isFalse();
		assertThat(result.answer()).isNull();
		assertThat(result.dataSource()).isNull();
		assertThat(result.confidence()).isEqualTo(0.0);
		assertThat(result.suggestionId()).isNull();
	}
}
