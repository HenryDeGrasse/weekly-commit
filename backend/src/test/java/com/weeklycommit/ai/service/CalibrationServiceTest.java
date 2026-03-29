package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.weeklycommit.ai.service.CalibrationService.CalibrationConfidenceTier;
import com.weeklycommit.ai.service.CalibrationService.CalibrationProfile;
import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalibrationServiceTest {

	@Mock
	private UserWeekFactRepository weekFactRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	private CalibrationService service;
	private UUID userId;

	@BeforeEach
	void setUp() {
		service = new CalibrationService(weekFactRepo, commitRepo);
		userId = UUID.randomUUID();
	}

	// ── Insufficient data ────────────────────────────────────────────────

	@Test
	void getCalibration_lessThan8Weeks_returnsInsufficient() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(7, 80, 60, 10, 2));

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.isInsufficient()).isTrue();
		assertThat(result.confidenceTier()).isEqualTo(CalibrationConfidenceTier.INSUFFICIENT);
	}

	@Test
	void getCalibration_zeroWeeks_returnsInsufficient() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.isInsufficient()).isTrue();
	}

	// ── Valid rates from 12 weeks ────────────────────────────────────────

	@Test
	void getCalibration_12Weeks_producesValidOverallRate() {
		// 12 facts: planned 100pts each, achieved 80pts each → 80%
		List<UserWeekFact> facts = buildFacts(12, 100, 80, 10, 2);
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any())).thenReturn(facts);
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.isInsufficient()).isFalse();
		assertThat(result.overallAchievementRate()).isEqualTo(0.80);
		assertThat(result.weeksOfData()).isEqualTo(12);
	}

	@Test
	void getCalibration_8Weeks_returnsLowConfidenceTier() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(8, 100, 70, 10, 1));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.confidenceTier()).isEqualTo(CalibrationConfidenceTier.LOW);
	}

	@Test
	void getCalibration_15Weeks_returnsMediumConfidenceTier() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(15, 100, 90, 10, 1));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.confidenceTier()).isEqualTo(CalibrationConfidenceTier.MEDIUM);
	}

	@Test
	void getCalibration_30Weeks_returnsHighConfidenceTier() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(30, 100, 95, 10, 1));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.confidenceTier()).isEqualTo(CalibrationConfidenceTier.HIGH);
	}

	// ── Carry-forward probability ────────────────────────────────────────

	@Test
	void getCalibration_carryForwardProbabilityMatchesHistoricalFrequency() {
		// 10 commits per week, 3 carry-forwards per week → 30%
		List<UserWeekFact> facts = buildFacts(10, 100, 80, 10, 3);
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any())).thenReturn(facts);
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		// 30 carry-forward / 100 total commits = 0.30
		assertThat(result.carryForwardProbability()).isEqualTo(0.30);
	}

	@Test
	void getCalibration_noCarryForwards_returnsZeroProbability() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(10, 100, 80, 5, 0));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.carryForwardProbability()).isEqualTo(0.0);
	}

	// ── Per-chess-piece achievement rates ────────────────────────────────

	@Test
	void getCalibration_chessPieceRatesComputedFromCommits() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(10, 100, 80, 10, 2));

		// 3 KING commits: 2 ACHIEVED, 1 NOT_ACHIEVED → 66.7%
		// 2 QUEEN commits: 2 ACHIEVED → 100%
		List<WeeklyCommit> commits = new ArrayList<>();
		commits.add(buildCommit(ChessPiece.KING, CommitOutcome.ACHIEVED, 3));
		commits.add(buildCommit(ChessPiece.KING, CommitOutcome.ACHIEVED, 3));
		commits.add(buildCommit(ChessPiece.KING, CommitOutcome.NOT_ACHIEVED, 3));
		commits.add(buildCommit(ChessPiece.QUEEN, CommitOutcome.ACHIEVED, 5));
		commits.add(buildCommit(ChessPiece.QUEEN, CommitOutcome.ACHIEVED, 5));

		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(commits);

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.chessPieceAchievementRates()).containsKey(ChessPiece.KING);
		assertThat(result.chessPieceAchievementRates().get(ChessPiece.KING)).isEqualTo(2.0 / 3.0);
		assertThat(result.chessPieceAchievementRates().get(ChessPiece.QUEEN)).isEqualTo(1.0);
	}

	@Test
	void getCalibration_canceledCommitsExcludedFromRateCalculation() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(10, 100, 80, 10, 1));

		// 1 ACHIEVED + 1 CANCELED: only ACHIEVED counts → 100% rate (1 of 1
		// non-canceled)
		List<WeeklyCommit> commits = List.of(buildCommit(ChessPiece.PAWN, CommitOutcome.ACHIEVED, 1),
				buildCommit(ChessPiece.PAWN, CommitOutcome.CANCELED, 1));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(commits);

		CalibrationProfile result = service.getCalibration(userId);

		// Only 1 non-canceled PAWN commit: 1 achieved / 1 total = 1.0
		assertThat(result.chessPieceAchievementRates().get(ChessPiece.PAWN)).isEqualTo(1.0);
	}

	@Test
	void getCalibration_avgEstimateByPieceComputedFromCommits() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(10, 100, 80, 10, 1));

		// KING: 3 + 5 = avg 4.0; PAWN: 1 only → avg 1.0
		List<WeeklyCommit> commits = List.of(buildCommit(ChessPiece.KING, CommitOutcome.ACHIEVED, 3),
				buildCommit(ChessPiece.KING, CommitOutcome.ACHIEVED, 5),
				buildCommit(ChessPiece.PAWN, CommitOutcome.NOT_ACHIEVED, 1));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(commits);

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.avgEstimateByPiece().get(ChessPiece.KING)).isEqualTo(4.0);
		assertThat(result.avgEstimateByPiece().get(ChessPiece.PAWN)).isEqualTo(1.0);
	}

	// ── Edge cases ───────────────────────────────────────────────────────

	@Test
	void getCalibration_noPlannedPoints_doesNotDivideByZero() {
		when(weekFactRepo.findByUserIdAndWeekStartBetween(eq(userId), any(), any()))
				.thenReturn(buildFacts(10, 0, 0, 0, 0));
		when(commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(eq(userId), any(), any())).thenReturn(List.of());

		CalibrationProfile result = service.getCalibration(userId);

		assertThat(result.overallAchievementRate()).isEqualTo(0.0);
		assertThat(result.carryForwardProbability()).isEqualTo(0.0);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	/**
	 * Builds {@code count} identical {@link UserWeekFact} rows with the given
	 * aggregate values.
	 */
	private List<UserWeekFact> buildFacts(int count, int plannedPts, int achievedPts, int commitCount,
			int carryForwardCount) {
		List<UserWeekFact> facts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			UserWeekFact f = new UserWeekFact();
			f.setUserId(userId);
			f.setWeekStart(LocalDate.now().minusWeeks(i + 1));
			f.setTotalPlannedPoints(plannedPts);
			f.setTotalAchievedPoints(achievedPts);
			f.setCommitCount(commitCount);
			f.setCarryForwardCount(carryForwardCount);
			facts.add(f);
		}
		return facts;
	}

	private WeeklyCommit buildCommit(ChessPiece piece, CommitOutcome outcome, int estimatePoints) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setOwnerUserId(userId);
		c.setPlanId(UUID.randomUUID());
		c.setTitle("Test commit");
		c.setChessPiece(piece);
		c.setOutcome(outcome);
		c.setEstimatePoints(estimatePoints);
		c.setPriorityOrder(1);
		return c;
	}
}
