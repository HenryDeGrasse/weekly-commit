package com.weeklycommit.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.ComplianceFact;
import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.entity.RcdoWeekRollup;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.TeamWeekRollup;
import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import com.weeklycommit.domain.repository.AiFeedbackRepository;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.ComplianceFactRepository;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.RcdoWeekRollupRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamWeekRollupRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.report.dto.AiAcceptanceReportEntry;
import com.weeklycommit.report.dto.CarryForwardReportEntry;
import com.weeklycommit.report.dto.ChessDistributionReportEntry;
import com.weeklycommit.report.dto.ComplianceReportEntry;
import com.weeklycommit.report.dto.ExceptionAgingEntry;
import com.weeklycommit.report.dto.PlannedVsAchievedEntry;
import com.weeklycommit.report.dto.RcdoCoverageReportEntry;
import com.weeklycommit.report.dto.ScopeChangeReportEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReportingService}.
 *
 * <p>
 * Verifies that report endpoints return correctly filtered data from derived
 * read-model tables.
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

	@Mock
	private ComplianceFactRepository complianceFactRepo;
	@Mock
	private TeamWeekRollupRepository teamWeekRollupRepo;
	@Mock
	private UserWeekFactRepository userWeekFactRepo;
	@Mock
	private RcdoWeekRollupRepository rcdoWeekRollupRepo;
	@Mock
	private TeamMembershipRepository membershipRepo;
	@Mock
	private ManagerReviewExceptionRepository exceptionRepo;
	@Mock
	private AiFeedbackRepository feedbackRepo;
	@Mock
	private AiSuggestionRepository suggestionRepo;

	private ReportingService service;

	private UUID teamId;
	private UUID userId;
	private LocalDate weekStart;
	private LocalDate weekEnd;

	@BeforeEach
	void setUp() {
		service = new ReportingService(complianceFactRepo, teamWeekRollupRepo, userWeekFactRepo, rcdoWeekRollupRepo,
				membershipRepo, exceptionRepo, feedbackRepo, suggestionRepo, new ObjectMapper());

		teamId = UUID.randomUUID();
		userId = UUID.randomUUID();
		weekStart = LocalDate.of(2026, 3, 10);
		weekEnd = LocalDate.of(2026, 3, 24);
	}

	// -------------------------------------------------------------------------
	// Compliance report
	// -------------------------------------------------------------------------

	@Test
	void getComplianceReport_returnsFilteredDataFromDerivedTable() {
		TeamMembership membership = new TeamMembership();
		membership.setTeamId(teamId);
		membership.setUserId(userId);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership));

		ComplianceFact fact = new ComplianceFact();
		fact.setUserId(userId);
		fact.setWeekStart(weekStart);
		fact.setLockOnTime(true);
		when(complianceFactRepo.findByUserIdInAndWeekStartBetween(List.of(userId), weekStart, weekEnd))
				.thenReturn(List.of(fact));

		List<ComplianceReportEntry> result = service.getComplianceReport(teamId, weekStart, weekEnd);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).userId()).isEqualTo(userId);
		assertThat(result.get(0).weekStart()).isEqualTo(weekStart);
		assertThat(result.get(0).lockOnTime()).isTrue();
	}

	@Test
	void getComplianceReport_returnsEmptyWhenNoTeamMembers() {
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of());

		List<ComplianceReportEntry> result = service.getComplianceReport(teamId, weekStart, weekEnd);

		assertThat(result).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Planned vs achieved
	// -------------------------------------------------------------------------

	@Test
	void getPlannedVsAchievedReport_queriesTeamWeekRollupTable() {
		TeamWeekRollup rollup = new TeamWeekRollup();
		rollup.setTeamId(teamId);
		rollup.setWeekStart(weekStart);
		rollup.setTotalPlannedPoints(20);
		rollup.setTotalAchievedPoints(15);
		rollup.setMemberCount(5);
		rollup.setReconciledCount(4);
		when(teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(teamId, weekStart, weekEnd))
				.thenReturn(List.of(rollup));

		List<PlannedVsAchievedEntry> result = service.getPlannedVsAchievedReport(teamId, weekStart, weekEnd);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).totalPlannedPoints()).isEqualTo(20);
		assertThat(result.get(0).totalAchievedPoints()).isEqualTo(15);
		assertThat(result.get(0).memberCount()).isEqualTo(5);
	}

	// -------------------------------------------------------------------------
	// Carry-forward report
	// -------------------------------------------------------------------------

	@Test
	void getCarryForwardReport_computesRateFromUserWeekFact() {
		TeamMembership membership = new TeamMembership();
		membership.setTeamId(teamId);
		membership.setUserId(userId);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership));

		UserWeekFact fact = new UserWeekFact();
		fact.setUserId(userId);
		fact.setWeekStart(weekStart);
		fact.setCommitCount(4);
		fact.setCarryForwardCount(2);
		when(userWeekFactRepo.findByUserIdInAndWeekStartBetween(List.of(userId), weekStart, weekEnd))
				.thenReturn(List.of(fact));

		List<CarryForwardReportEntry> result = service.getCarryForwardReport(teamId, weekStart, weekEnd);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).carryForwardCount()).isEqualTo(2);
		assertThat(result.get(0).commitCount()).isEqualTo(4);
		assertThat(result.get(0).carryForwardRate()).isEqualTo(0.5);
	}

	@Test
	void getCarryForwardReport_zeroRateWhenNoCommits() {
		TeamMembership membership = new TeamMembership();
		membership.setTeamId(teamId);
		membership.setUserId(userId);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership));

		UserWeekFact fact = new UserWeekFact();
		fact.setUserId(userId);
		fact.setWeekStart(weekStart);
		fact.setCommitCount(0);
		fact.setCarryForwardCount(0);
		when(userWeekFactRepo.findByUserIdInAndWeekStartBetween(List.of(userId), weekStart, weekEnd))
				.thenReturn(List.of(fact));

		List<CarryForwardReportEntry> result = service.getCarryForwardReport(teamId, weekStart, weekEnd);

		assertThat(result.get(0).carryForwardRate()).isEqualTo(0.0);
	}

	// -------------------------------------------------------------------------
	// Chess distribution
	// -------------------------------------------------------------------------

	@Test
	void getChessDistributionReport_parsesJsonFromTeamWeekRollup() {
		TeamWeekRollup rollup = new TeamWeekRollup();
		rollup.setTeamId(teamId);
		rollup.setWeekStart(weekStart);
		rollup.setChessDistribution("{\"KING\":1,\"ROOK\":3}");
		when(teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, weekStart)).thenReturn(Optional.of(rollup));

		ChessDistributionReportEntry result = service.getChessDistributionReport(teamId, weekStart);

		assertThat(result.teamId()).isEqualTo(teamId);
		assertThat(result.distribution()).containsEntry("KING", 1);
		assertThat(result.distribution()).containsEntry("ROOK", 3);
	}

	@Test
	void getChessDistributionReport_returnsEmptyMapWhenNoRollup() {
		when(teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, weekStart)).thenReturn(Optional.empty());

		ChessDistributionReportEntry result = service.getChessDistributionReport(teamId, weekStart);

		assertThat(result.distribution()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Scope changes
	// -------------------------------------------------------------------------

	@Test
	void getScopeChangeReport_returnsScopeChangeCountFromUserWeekFact() {
		TeamMembership membership = new TeamMembership();
		membership.setTeamId(teamId);
		membership.setUserId(userId);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership));

		UserWeekFact fact = new UserWeekFact();
		fact.setUserId(userId);
		fact.setWeekStart(weekStart);
		fact.setScopeChangeCount(5);
		when(userWeekFactRepo.findByUserIdInAndWeekStartBetween(List.of(userId), weekStart, weekEnd))
				.thenReturn(List.of(fact));

		List<ScopeChangeReportEntry> result = service.getScopeChangeReport(teamId, weekStart, weekEnd);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).scopeChangeCount()).isEqualTo(5);
	}

	// -------------------------------------------------------------------------
	// RCDO coverage
	// -------------------------------------------------------------------------

	@Test
	void getRcdoCoverageReport_returnsRcdoWeekRollupData() {
		UUID rcdoNodeId = UUID.randomUUID();
		UUID teamContribId = UUID.randomUUID();

		RcdoWeekRollup rollup = new RcdoWeekRollup();
		rollup.setRcdoNodeId(rcdoNodeId);
		rollup.setWeekStart(weekStart);
		rollup.setPlannedPoints(15);
		rollup.setAchievedPoints(10);
		rollup.setCommitCount(3);
		rollup.setTeamContributionBreakdown("{\"" + teamContribId + "\":15}");
		when(rcdoWeekRollupRepo.findByRcdoNodeIdAndWeekStartBetween(rcdoNodeId, weekStart, weekEnd))
				.thenReturn(List.of(rollup));

		List<RcdoCoverageReportEntry> result = service.getRcdoCoverageReport(rcdoNodeId, weekStart, weekEnd);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).plannedPoints()).isEqualTo(15);
		assertThat(result.get(0).achievedPoints()).isEqualTo(10);
		assertThat(result.get(0).commitCount()).isEqualTo(3);
		assertThat(result.get(0).teamContributionBreakdown()).containsEntry(teamContribId.toString(), 15);
	}

	// -------------------------------------------------------------------------
	// AI acceptance
	// -------------------------------------------------------------------------

	@Test
	void getAiAcceptanceReport_computesAcceptanceRateCorrectly() {
		when(suggestionRepo.count()).thenReturn(100L);
		when(feedbackRepo.count()).thenReturn(40L);
		when(feedbackRepo.countByAccepted(true)).thenReturn(30L);
		when(feedbackRepo.countByAccepted(false)).thenReturn(10L);

		AiAcceptanceReportEntry result = service.getAiAcceptanceReport();

		assertThat(result.totalSuggestions()).isEqualTo(100);
		assertThat(result.totalFeedbackGiven()).isEqualTo(40);
		assertThat(result.acceptedCount()).isEqualTo(30);
		assertThat(result.dismissedCount()).isEqualTo(10);
		assertThat(result.acceptanceRate()).isEqualTo(0.75); // 30/40
	}

	@Test
	void getAiAcceptanceReport_returnsZeroRateWhenNoFeedback() {
		when(suggestionRepo.count()).thenReturn(50L);
		when(feedbackRepo.count()).thenReturn(0L);
		when(feedbackRepo.countByAccepted(true)).thenReturn(0L);
		when(feedbackRepo.countByAccepted(false)).thenReturn(0L);

		AiAcceptanceReportEntry result = service.getAiAcceptanceReport();

		assertThat(result.acceptanceRate()).isEqualTo(0.0);
	}

	// -------------------------------------------------------------------------
	// Exception aging
	// -------------------------------------------------------------------------

	@Test
	void getExceptionAgingReport_returnsUnresolvedExceptionsWithAge() {
		ManagerReviewException ex = new ManagerReviewException();
		ex.setId(UUID.randomUUID());
		ex.setTeamId(teamId);
		ex.setUserId(userId);
		ex.setExceptionType(ExceptionType.MISSED_LOCK);
		ex.setSeverity(ExceptionSeverity.MEDIUM);
		ex.setWeekStartDate(weekStart);
		ex.setResolved(false);
		ex.setDescription("Late lock exception");

		when(exceptionRepo.findByTeamIdAndResolved(teamId, false)).thenReturn(List.of(ex));

		List<ExceptionAgingEntry> result = service.getExceptionAgingReport(teamId);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).teamId()).isEqualTo(teamId);
		assertThat(result.get(0).userId()).isEqualTo(userId);
		assertThat(result.get(0).exceptionType()).isEqualTo("MISSED_LOCK");
		assertThat(result.get(0).severity()).isEqualTo("MEDIUM");
		// Age should be 0 since createdAt is null in test
		assertThat(result.get(0).ageInHours()).isEqualTo(0L);
	}
}
