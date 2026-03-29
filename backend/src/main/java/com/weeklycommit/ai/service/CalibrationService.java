package com.weeklycommit.ai.service;

import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes rolling completion-rate statistics from historical data for
 * predictive planning intelligence (PRD §17 capability — calibration).
 *
 * <p>
 * Aggregate fields (overall achievement, carry-forward probability) come from
 * the {@code user_week_fact} read-model. Per-chess-piece rates and estimates
 * are derived directly from {@code WeeklyCommit} rows because
 * {@code user_week_fact} only stores {@code kingCount} / {@code queenCount}
 * aggregates — not per-piece achievement or estimate data.
 */
@Service
@Transactional(readOnly = true)
public class CalibrationService {

	private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

	/** Minimum weeks required before a calibration profile is considered usable. */
	static final int WEEKS_INSUFFICIENT = 8;

	/** Rolling window: last 12 weeks of history (84 days). */
	static final int WINDOW_WEEKS = 12;

	// ── Tier boundaries ──────────────────────────────────────────────────

	/** {@link CalibrationConfidenceTier#INSUFFICIENT} below this week count. */
	static final int TIER_LOW_MIN = 8;
	/** {@link CalibrationConfidenceTier#LOW} from 8 (inclusive) to 15. */
	static final int TIER_MEDIUM_MIN = 15;
	/** {@link CalibrationConfidenceTier#MEDIUM} from 15 (inclusive) to 30. */
	static final int TIER_HIGH_MIN = 30;

	// ── Dependencies ──────────────────────────────────────────────────────

	private final UserWeekFactRepository weekFactRepo;
	private final WeeklyCommitRepository commitRepo;

	public CalibrationService(UserWeekFactRepository weekFactRepo, WeeklyCommitRepository commitRepo) {
		this.weekFactRepo = weekFactRepo;
		this.commitRepo = commitRepo;
	}

	// ── Public API ────────────────────────────────────────────────────────

	/**
	 * Returns the rolling {@link CalibrationProfile} for the given user over the
	 * last {@value #WINDOW_WEEKS} weeks.
	 *
	 * <p>
	 * Returns {@link CalibrationProfile#insufficient()} when there are fewer than
	 * {@value #WEEKS_INSUFFICIENT} weeks of data — callers should treat the profile
	 * as unavailable and not display it.
	 *
	 * @param userId
	 *            the user to calibrate
	 * @return calibration profile, never {@code null}
	 */
	public CalibrationProfile getCalibration(UUID userId) {
		LocalDate to = LocalDate.now();
		LocalDate from = to.minusWeeks(WINDOW_WEEKS);

		// ── Step 1: aggregate stats from user_week_fact ──────────────────
		List<UserWeekFact> facts = weekFactRepo.findByUserIdAndWeekStartBetween(userId, from, to);
		int weeksOfData = facts.size();

		if (weeksOfData < WEEKS_INSUFFICIENT) {
			log.debug("CalibrationService: insufficient data for user={} ({} weeks)", userId, weeksOfData);
			return CalibrationProfile.insufficient();
		}

		long totalPlanned = 0;
		long totalAchieved = 0;
		long totalCommits = 0;
		long totalCarryForward = 0;

		for (UserWeekFact f : facts) {
			totalPlanned += f.getTotalPlannedPoints();
			totalAchieved += f.getTotalAchievedPoints();
			totalCommits += f.getCommitCount();
			totalCarryForward += f.getCarryForwardCount();
		}

		double overallAchievementRate = totalPlanned > 0 ? (double) totalAchieved / totalPlanned : 0.0;
		double carryForwardProbability = totalCommits > 0 ? (double) totalCarryForward / totalCommits : 0.0;

		// ── Step 2: per-piece stats from WeeklyCommit rows ───────────────
		List<WeeklyCommit> commits = commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(userId, from, to);

		Map<ChessPiece, int[]> pieceStats = new EnumMap<>(ChessPiece.class);
		Map<ChessPiece, long[]> pieceEstimates = new EnumMap<>(ChessPiece.class);

		for (WeeklyCommit c : commits) {
			if (c.getChessPiece() == null) {
				continue;
			}
			ChessPiece piece = c.getChessPiece();

			// [0] = total outcomes (excluding null/CANCELED), [1] = achieved count
			pieceStats.computeIfAbsent(piece, k -> new int[2]);
			int[] stats = pieceStats.get(piece);
			if (c.getOutcome() != null && c.getOutcome() != CommitOutcome.CANCELED) {
				stats[0]++;
				if (c.getOutcome() == CommitOutcome.ACHIEVED) {
					stats[1]++;
				}
			}

			// [0] = sum of estimate points, [1] = count with non-null estimates
			pieceEstimates.computeIfAbsent(piece, k -> new long[2]);
			if (c.getEstimatePoints() != null && c.getEstimatePoints() > 0) {
				pieceEstimates.get(piece)[0] += c.getEstimatePoints();
				pieceEstimates.get(piece)[1]++;
			}
		}

		Map<ChessPiece, Double> chessPieceAchievementRates = new EnumMap<>(ChessPiece.class);
		for (Map.Entry<ChessPiece, int[]> entry : pieceStats.entrySet()) {
			int total = entry.getValue()[0];
			int achieved = entry.getValue()[1];
			chessPieceAchievementRates.put(entry.getKey(), total > 0 ? (double) achieved / total : 0.0);
		}

		Map<ChessPiece, Double> avgEstimateByPiece = new EnumMap<>(ChessPiece.class);
		for (Map.Entry<ChessPiece, long[]> entry : pieceEstimates.entrySet()) {
			long count = entry.getValue()[1];
			long sum = entry.getValue()[0];
			avgEstimateByPiece.put(entry.getKey(), count > 0 ? (double) sum / count : 0.0);
		}

		CalibrationConfidenceTier tier = tierFromWeeks(weeksOfData);

		return new CalibrationProfile(overallAchievementRate, chessPieceAchievementRates, carryForwardProbability,
				weeksOfData, avgEstimateByPiece, tier);
	}

	// ── Private helpers ───────────────────────────────────────────────────

	private static CalibrationConfidenceTier tierFromWeeks(int weeks) {
		if (weeks < TIER_LOW_MIN) {
			return CalibrationConfidenceTier.INSUFFICIENT;
		}
		if (weeks < TIER_MEDIUM_MIN) {
			return CalibrationConfidenceTier.LOW;
		}
		if (weeks < TIER_HIGH_MIN) {
			return CalibrationConfidenceTier.MEDIUM;
		}
		return CalibrationConfidenceTier.HIGH;
	}

	// ── Inner types ───────────────────────────────────────────────────────

	/**
	 * Data-sufficiency tier for calibration profiles. Named
	 * {@code CalibrationConfidenceTier} to avoid collision with the evidence-based
	 * {@code ConfidenceTier} introduced in Step 9.
	 */
	public enum CalibrationConfidenceTier {
		/** 30+ weeks of data. */
		HIGH,
		/** 15–29 weeks of data. */
		MEDIUM,
		/** 8–14 weeks of data. */
		LOW,
		/** Fewer than 8 weeks — profile should not be displayed. */
		INSUFFICIENT
	}

	/**
	 * Immutable calibration profile for a single user.
	 *
	 * <p>
	 * Use {@link #insufficient()} as a safe sentinel when there is not enough
	 * historical data to produce meaningful statistics.
	 */
	public record CalibrationProfile(
			/** Fraction of planned points achieved (0.0–1.0). */
			double overallAchievementRate,
			/** Per-chess-piece achievement rates, keyed by {@link ChessPiece}. */
			Map<ChessPiece, Double> chessPieceAchievementRates,
			/** Fraction of commits that were carried forward (0.0–1.0). */
			double carryForwardProbability,
			/** Number of UserWeekFact rows found in the rolling window. */
			int weeksOfData,
			/** Average estimate points per chess piece from WeeklyCommit rows. */
			Map<ChessPiece, Double> avgEstimateByPiece,
			/** Data-sufficiency confidence tier. */
			CalibrationConfidenceTier confidenceTier) {

		/**
		 * Sentinel profile returned when there is insufficient historical data (&lt;
		 * {@value CalibrationService#WEEKS_INSUFFICIENT} weeks).
		 */
		public static CalibrationProfile insufficient() {
			return new CalibrationProfile(0.0, Map.of(), 0.0, 0, Map.of(), CalibrationConfidenceTier.INSUFFICIENT);
		}

		/** {@code true} when the profile has too little data to be useful. */
		public boolean isInsufficient() {
			return confidenceTier == CalibrationConfidenceTier.INSUFFICIENT;
		}
	}
}
