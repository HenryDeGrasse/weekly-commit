package com.weeklycommit.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Historical replay benchmark for rules-based risk signal accuracy.
 *
 * <p>
 * Pure unit test — no {@code @SpringBootTest}, no database, no external
 * services. Builds 12 synthetic {@link WeeklyPlan}/{@link WeeklyCommit}
 * scenarios entirely in memory, replays the 5 risk-signal rules against each,
 * and compares predictions to known outcomes using the explicit
 * signal-to-outcome mappings defined by this benchmark.
 *
 * <p>
 * Signal-to-outcome mappings:
 * <ul>
 * <li>OVERCOMMIT — true positive if plan's average commit completion rate &lt;
 * 70% (ACHIEVED=1.0, PARTIALLY_ACHIEVED=0.5, NOT_ACHIEVED/CANCELED=0.0)
 * <li>UNDERCOMMIT — true positive if plan had spare capacity AND all commits
 * ACHIEVED
 * <li>REPEATED_CARRY_FORWARD — true positive if the flagged commit's outcome is
 * NOT_ACHIEVED or CANCELED
 * <li>BLOCKED_CRITICAL — true positive if the flagged commit's outcome is
 * NOT_ACHIEVED
 * <li>SCOPE_VOLATILITY — true positive if any commit outcome is NOT_ACHIEVED
 * </ul>
 *
 * <p>
 * Results are written to {@code build/eval-results/replay-benchmark.json}.
 * Tagged {@code @Tag("eval")} so it runs only via {@code ./gradlew evalTest}.
 */
@Tag("eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricalReplayBenchmark {

	private static final Logger log = LoggerFactory.getLogger(HistoricalReplayBenchmark.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	// ── Risk-rule thresholds (replicated from RiskDetectionService) ──────────

	/** Hours a King/Queen ticket must be BLOCKED to trigger BLOCKED_CRITICAL. */
	private static final long BLOCKED_CRITICAL_HOURS = 48;

	/** Minimum carry-forward streak to trigger REPEATED_CARRY_FORWARD. */
	private static final int CARRY_FORWARD_STREAK_THRESHOLD = 2;

	/** Post-lock scope changes needed to trigger SCOPE_VOLATILITY. */
	private static final int SCOPE_VOLATILITY_THRESHOLD = 3;

	/** Points/budget ratio below which UNDERCOMMIT fires. */
	private static final double UNDERCOMMIT_THRESHOLD = 0.60;

	// ── Outcome-mapping threshold ─────────────────────────────────────────────

	/** Completion rate below which OVERCOMMIT is considered "truly harmful". */
	private static final double OVERCOMMIT_COMPLETION_THRESHOLD = 0.70;

	// ── Deterministic IDs ─────────────────────────────────────────────────────

	private static final UUID OWNER_ID = new UUID(0L, 0L);
	private static final UUID TEAM_ID = new UUID(0L, 1L);

	/**
	 * A well-past timestamp used as the "blocked since" anchor for scenarios that
	 * include a BLOCKED_CRITICAL King/Queen commit. Using a fixed past instant
	 * ensures {@code Duration.between(BLOCKED_SINCE, Instant.now()) >= 48h}
	 * regardless of when the test runs.
	 */
	private static final Instant BLOCKED_SINCE = Instant.parse("2026-03-22T00:00:00Z");

	private static final List<String> SIGNAL_TYPES = List.of("OVERCOMMIT", "UNDERCOMMIT", "REPEATED_CARRY_FORWARD",
			"BLOCKED_CRITICAL", "SCOPE_VOLATILITY");

	// ── Scenario data ─────────────────────────────────────────────────────────

	/**
	 * In-memory plan scenario — no database, no persistence. Uses plain Java
	 * objects created with setters only.
	 *
	 * @param plan
	 *            the weekly plan (capacity budget, state)
	 * @param commits
	 *            commits with outcomes already set (simulates post-reconcile state)
	 * @param scopeChangeCount
	 *            post-lock scope-change event count (simulated)
	 * @param blockedCommitIds
	 *            ids of KING/QUEEN commits whose linked ticket was BLOCKED since
	 *            {@link #BLOCKED_SINCE}
	 */
	private record PlanScenario(WeeklyPlan plan, List<WeeklyCommit> commits, long scopeChangeCount,
			Set<UUID> blockedCommitIds) {
	}

	private List<PlanScenario> scenarios;

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@BeforeAll
	void buildSyntheticData() {
		scenarios = List.of(scenario01_overcommitTp(), scenario02_overcommitFp(), scenario03_overcommitFn(),
				scenario04_undercommitTp(), scenario05_undercommitFp(), scenario06_repeatedCfTp(),
				scenario07_repeatedCfFp(), scenario08_scopeVolatilityTp(), scenario09_scopeVolatilityFp(),
				scenario10_blockedCriticalTp(), scenario11_blockedCriticalFp(), scenario12_balancedTn());
		log.info("Built {} synthetic plan scenarios for historical replay benchmark", scenarios.size());
	}

	// ── Test ──────────────────────────────────────────────────────────────────

	@Test
	void runReplayBenchmark() throws Exception {
		// int[4] = { TP, FP, FN, TN }
		Map<String, int[]> matrix = new LinkedHashMap<>();
		for (String signal : SIGNAL_TYPES) {
			matrix.put(signal, new int[4]);
		}

		for (PlanScenario scenario : scenarios) {
			Set<String> predicted = detectSignals(scenario);
			for (String signal : SIGNAL_TYPES) {
				boolean wasPredicted = predicted.contains(signal);
				boolean wasConfirmed = isSignalConfirmed(signal, scenario);
				int[] cm = matrix.get(signal);
				if (wasPredicted && wasConfirmed) {
					cm[0]++;
				} else if (wasPredicted) {
					cm[1]++;
				} else if (wasConfirmed) {
					cm[2]++;
				} else {
					cm[3]++;
				}
			}
		}

		// Build and write JSON report
		Map<String, Object> report = buildReport(matrix);
		Path reportDir = Path.of("build", "eval-results");
		Files.createDirectories(reportDir);
		File outFile = reportDir.resolve("replay-benchmark.json").toFile();
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(outFile, report);
		log.info("Replay benchmark report written to: {}", outFile.getAbsolutePath());

		logSummary(matrix);

		// Sanity assertions — always pass on deterministic synthetic data
		assertThat(scenarios).hasSize(12);
		assertThat(outFile).exists();
		// Verify we have at least one TP for each signal
		for (String signal : SIGNAL_TYPES) {
			int[] cm = matrix.get(signal);
			assertThat(cm[0]).as("TP count for %s should be >= 1", signal).isGreaterThanOrEqualTo(1);
		}
	}

	// ── In-memory risk detection (no repos, no DB) ────────────────────────────

	/**
	 * Replicates the 5 risk rules from RiskDetectionService as pure in-memory
	 * computation. No repository calls — uses only the data in the scenario.
	 */
	private Set<String> detectSignals(PlanScenario scenario) {
		java.util.HashSet<String> signals = new java.util.HashSet<>();
		List<WeeklyCommit> commits = scenario.commits();
		int budget = scenario.plan().getCapacityBudgetPoints();
		int totalPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
				.sum();

		// 1. OVERCOMMIT
		if (totalPoints > budget) {
			signals.add("OVERCOMMIT");
		}

		// 2. UNDERCOMMIT
		if (budget > 0 && (double) totalPoints / budget < UNDERCOMMIT_THRESHOLD) {
			signals.add("UNDERCOMMIT");
		}

		// 3. REPEATED_CARRY_FORWARD
		for (WeeklyCommit c : commits) {
			if (c.getCarryForwardStreak() >= CARRY_FORWARD_STREAK_THRESHOLD) {
				signals.add("REPEATED_CARRY_FORWARD");
				break;
			}
		}

		// 4. BLOCKED_CRITICAL — uses blockedCommitIds + BLOCKED_SINCE anchor
		Instant now = Instant.now();
		for (WeeklyCommit c : commits) {
			if (c.getChessPiece() != ChessPiece.KING && c.getChessPiece() != ChessPiece.QUEEN) {
				continue;
			}
			if (scenario.blockedCommitIds().contains(c.getId())
					&& Duration.between(BLOCKED_SINCE, now).toHours() >= BLOCKED_CRITICAL_HOURS) {
				signals.add("BLOCKED_CRITICAL");
				break;
			}
		}

		// 5. SCOPE_VOLATILITY
		if (scenario.scopeChangeCount() > SCOPE_VOLATILITY_THRESHOLD) {
			signals.add("SCOPE_VOLATILITY");
		}

		return signals;
	}

	// ── Signal-to-outcome confirmation ────────────────────────────────────────

	/**
	 * Returns {@code true} when the given signal's outcome condition is satisfied
	 * (making a prediction "true positive" if also predicted, or "false negative"
	 * if not).
	 */
	private boolean isSignalConfirmed(String signal, PlanScenario scenario) {
		List<WeeklyCommit> commits = scenario.commits();
		return switch (signal) {
			case "OVERCOMMIT" ->
				// TP if average completion rate < 70%
				completionRate(commits) < OVERCOMMIT_COMPLETION_THRESHOLD;
			case "UNDERCOMMIT" ->
				// TP if plan had spare capacity AND all commits ACHIEVED
				commits.stream().allMatch(c -> c.getOutcome() == CommitOutcome.ACHIEVED)
						&& scenario.plan().getCapacityBudgetPoints() > totalPoints(commits);
			case "REPEATED_CARRY_FORWARD" ->
				// TP if the flagged commit (streak >= 2) has outcome NOT_ACHIEVED or CANCELED
				commits.stream().filter(c -> c.getCarryForwardStreak() >= CARRY_FORWARD_STREAK_THRESHOLD).anyMatch(
						c -> c.getOutcome() == CommitOutcome.NOT_ACHIEVED || c.getOutcome() == CommitOutcome.CANCELED);
			case "BLOCKED_CRITICAL" ->
				// TP if the blocked King/Queen commit's outcome is NOT_ACHIEVED
				commits.stream()
						.filter(c -> (c.getChessPiece() == ChessPiece.KING || c.getChessPiece() == ChessPiece.QUEEN)
								&& scenario.blockedCommitIds().contains(c.getId()))
						.anyMatch(c -> c.getOutcome() == CommitOutcome.NOT_ACHIEVED);
			case "SCOPE_VOLATILITY" ->
				// TP if any commit outcome is NOT_ACHIEVED
				commits.stream().anyMatch(c -> c.getOutcome() == CommitOutcome.NOT_ACHIEVED);
			default -> false;
		};
	}

	private double completionRate(List<WeeklyCommit> commits) {
		if (commits.isEmpty()) {
			return 1.0;
		}
		double sum = commits.stream().mapToDouble(c -> {
			if (c.getOutcome() == null) {
				return 0.0;
			}
			return switch (c.getOutcome()) {
				case ACHIEVED -> 1.0;
				case PARTIALLY_ACHIEVED -> 0.5;
				case NOT_ACHIEVED, CANCELED -> 0.0;
			};
		}).sum();
		return sum / commits.size();
	}

	private int totalPoints(List<WeeklyCommit> commits) {
		return commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();
	}

	// ── Scenario builders ────────────────────────────────────────────────────

	/**
	 * Plan 1 — OVERCOMMIT true positive. Budget=10, Points=14. Completion rate=0.17
	 * &lt; 0.70 → TP(OVERCOMMIT).
	 */
	private PlanScenario scenario01_overcommitTp() {
		WeeklyPlan plan = buildPlan(1, 10);
		List<WeeklyCommit> commits = List.of(commit(1, 1, ChessPiece.KING, 5, 0, CommitOutcome.NOT_ACHIEVED),
				commit(1, 2, ChessPiece.ROOK, 5, 0, CommitOutcome.NOT_ACHIEVED),
				commit(1, 3, ChessPiece.PAWN, 4, 0, CommitOutcome.PARTIALLY_ACHIEVED));
		// rate = (0+0+0.5)/3 = 0.17 < 0.70 → confirmed
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 2 — OVERCOMMIT false positive. Budget=10, Points=12. All ACHIEVED →
	 * rate=1.0 ≥ 0.70 → FP(OVERCOMMIT).
	 */
	private PlanScenario scenario02_overcommitFp() {
		WeeklyPlan plan = buildPlan(2, 10);
		List<WeeklyCommit> commits = List.of(commit(2, 1, ChessPiece.ROOK, 4, 0, CommitOutcome.ACHIEVED),
				commit(2, 2, ChessPiece.ROOK, 4, 0, CommitOutcome.ACHIEVED),
				commit(2, 3, ChessPiece.PAWN, 4, 0, CommitOutcome.ACHIEVED));
		// rate=1.0 ≥ 0.70 → not confirmed → FP
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 3 — OVERCOMMIT false negative. Budget=10, Points=9 (within budget).
	 * Rate=0.33 &lt; 0.70 → FN(OVERCOMMIT). Also contributes FN(SCOPE_VOLATILITY)
	 * since some commits NOT_ACHIEVED.
	 */
	private PlanScenario scenario03_overcommitFn() {
		WeeklyPlan plan = buildPlan(3, 10);
		List<WeeklyCommit> commits = List.of(commit(3, 1, ChessPiece.ROOK, 3, 0, CommitOutcome.NOT_ACHIEVED),
				commit(3, 2, ChessPiece.ROOK, 3, 0, CommitOutcome.NOT_ACHIEVED),
				commit(3, 3, ChessPiece.PAWN, 3, 0, CommitOutcome.ACHIEVED));
		// 9 ≤ 10 → no OVERCOMMIT predicted; rate=0.33 < 0.70 → FN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 4 — UNDERCOMMIT true positive. Budget=10, Points=5 (50% &lt; 60%). All
	 * ACHIEVED → TP(UNDERCOMMIT).
	 */
	private PlanScenario scenario04_undercommitTp() {
		WeeklyPlan plan = buildPlan(4, 10);
		List<WeeklyCommit> commits = List.of(commit(4, 1, ChessPiece.ROOK, 2, 0, CommitOutcome.ACHIEVED),
				commit(4, 2, ChessPiece.PAWN, 3, 0, CommitOutcome.ACHIEVED));
		// 5/10=50% < 60% → UNDERCOMMIT predicted; all ACHIEVED + spare capacity → TP
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 5 — UNDERCOMMIT false positive. Budget=10, Points=4 (40% &lt; 60%). Not
	 * all ACHIEVED → FP(UNDERCOMMIT).
	 */
	private PlanScenario scenario05_undercommitFp() {
		WeeklyPlan plan = buildPlan(5, 10);
		List<WeeklyCommit> commits = List.of(commit(5, 1, ChessPiece.ROOK, 1, 0, CommitOutcome.ACHIEVED),
				commit(5, 2, ChessPiece.ROOK, 1, 0, CommitOutcome.ACHIEVED),
				commit(5, 3, ChessPiece.PAWN, 2, 0, CommitOutcome.PARTIALLY_ACHIEVED));
		// 4/10=40% < 60% → UNDERCOMMIT predicted; not all ACHIEVED → FP
		// rate=(1+1+0.5)/3=0.83 ≥ 0.70 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 6 — REPEATED_CARRY_FORWARD true positive. One commit with streak=2. That
	 * commit is NOT_ACHIEVED → TP(REPEATED_CF).
	 */
	private PlanScenario scenario06_repeatedCfTp() {
		WeeklyPlan plan = buildPlan(6, 10);
		List<WeeklyCommit> commits = List.of(commit(6, 1, ChessPiece.ROOK, 3, 2, CommitOutcome.NOT_ACHIEVED),
				commit(6, 2, ChessPiece.ROOK, 2, 0, CommitOutcome.ACHIEVED),
				commit(6, 3, ChessPiece.ROOK, 2, 0, CommitOutcome.ACHIEVED),
				commit(6, 4, ChessPiece.PAWN, 1, 0, CommitOutcome.ACHIEVED));
		// streak=2 → REPEATED_CF predicted; flagged commit NOT_ACHIEVED → TP
		// rate=(0+1+1+1)/4=0.75 ≥ 0.70 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 7 — REPEATED_CARRY_FORWARD false positive. One commit with streak=3.
	 * That commit is ACHIEVED → FP(REPEATED_CF).
	 */
	private PlanScenario scenario07_repeatedCfFp() {
		WeeklyPlan plan = buildPlan(7, 10);
		List<WeeklyCommit> commits = List.of(commit(7, 1, ChessPiece.ROOK, 4, 3, CommitOutcome.ACHIEVED),
				commit(7, 2, ChessPiece.ROOK, 3, 0, CommitOutcome.ACHIEVED),
				commit(7, 3, ChessPiece.PAWN, 3, 0, CommitOutcome.ACHIEVED));
		// streak=3 → REPEATED_CF predicted; all ACHIEVED → FP
		// 10/10=100% → TN(UNDERCOMMIT); rate=1.0 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	/**
	 * Plan 8 — SCOPE_VOLATILITY true positive. 4 scope changes. One commit
	 * NOT_ACHIEVED → TP(SCOPE_VOL).
	 */
	private PlanScenario scenario08_scopeVolatilityTp() {
		WeeklyPlan plan = buildPlan(8, 10);
		List<WeeklyCommit> commits = List.of(commit(8, 1, ChessPiece.QUEEN, 5, 0, CommitOutcome.ACHIEVED),
				commit(8, 2, ChessPiece.ROOK, 2, 0, CommitOutcome.ACHIEVED),
				commit(8, 3, ChessPiece.ROOK, 1, 0, CommitOutcome.ACHIEVED),
				commit(8, 4, ChessPiece.PAWN, 1, 0, CommitOutcome.NOT_ACHIEVED));
		// scopeChanges=4 > 3 → SCOPE_VOL predicted; any NOT_ACHIEVED → TP
		// rate=(1+1+1+0)/4=0.75 ≥ 0.70 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 4, Set.of());
	}

	/**
	 * Plan 9 — SCOPE_VOLATILITY false positive. 5 scope changes. All ACHIEVED →
	 * FP(SCOPE_VOL).
	 */
	private PlanScenario scenario09_scopeVolatilityFp() {
		WeeklyPlan plan = buildPlan(9, 10);
		List<WeeklyCommit> commits = List.of(commit(9, 1, ChessPiece.QUEEN, 4, 0, CommitOutcome.ACHIEVED),
				commit(9, 2, ChessPiece.ROOK, 3, 0, CommitOutcome.ACHIEVED),
				commit(9, 3, ChessPiece.PAWN, 3, 0, CommitOutcome.ACHIEVED));
		// scopeChanges=5 > 3 → SCOPE_VOL predicted; all ACHIEVED → FP
		// 10/10=100% → TN(UNDERCOMMIT); rate=1.0 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 5, Set.of());
	}

	/**
	 * Plan 10 — BLOCKED_CRITICAL true positive. KING commit has ticket BLOCKED
	 * since BLOCKED_SINCE. Outcome NOT_ACHIEVED → TP(BLOCKED_CRITICAL).
	 */
	private PlanScenario scenario10_blockedCriticalTp() {
		WeeklyPlan plan = buildPlan(10, 10);
		UUID blockedId = commitUuid(10, 1);
		List<WeeklyCommit> commits = List.of(commitWithId(blockedId, ChessPiece.KING, 5, 0, CommitOutcome.NOT_ACHIEVED),
				commit(10, 2, ChessPiece.ROOK, 1, 0, CommitOutcome.ACHIEVED),
				commit(10, 3, ChessPiece.ROOK, 1, 0, CommitOutcome.ACHIEVED),
				commit(10, 4, ChessPiece.PAWN, 2, 0, CommitOutcome.ACHIEVED));
		// KING blocked ≥ 48h → BLOCKED_CRITICAL predicted; outcome NOT_ACHIEVED → TP
		// rate=(0+1+1+1)/4=0.75 ≥ 0.70 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of(blockedId));
	}

	/**
	 * Plan 11 — BLOCKED_CRITICAL false positive. QUEEN commit has ticket BLOCKED
	 * since BLOCKED_SINCE. Outcome ACHIEVED → FP(BLOCKED_CRITICAL).
	 */
	private PlanScenario scenario11_blockedCriticalFp() {
		WeeklyPlan plan = buildPlan(11, 10);
		UUID blockedId = commitUuid(11, 1);
		List<WeeklyCommit> commits = List.of(commitWithId(blockedId, ChessPiece.QUEEN, 4, 0, CommitOutcome.ACHIEVED),
				commit(11, 2, ChessPiece.ROOK, 3, 0, CommitOutcome.ACHIEVED),
				commit(11, 3, ChessPiece.PAWN, 3, 0, CommitOutcome.ACHIEVED));
		// QUEEN blocked ≥ 48h → BLOCKED_CRITICAL predicted; ACHIEVED → FP
		// 10/10=100% → TN(UNDERCOMMIT); rate=1.0 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of(blockedId));
	}

	/**
	 * Plan 12 — balanced plan, all TN. Budget=10, Points=10. All ACHIEVED, no
	 * signals. TN for every signal type.
	 */
	private PlanScenario scenario12_balancedTn() {
		WeeklyPlan plan = buildPlan(12, 10);
		List<WeeklyCommit> commits = List.of(commit(12, 1, ChessPiece.KING, 3, 0, CommitOutcome.ACHIEVED),
				commit(12, 2, ChessPiece.ROOK, 3, 0, CommitOutcome.ACHIEVED),
				commit(12, 3, ChessPiece.PAWN, 2, 0, CommitOutcome.ACHIEVED),
				commit(12, 4, ChessPiece.PAWN, 2, 0, CommitOutcome.ACHIEVED));
		// No signals predicted; all ACHIEVED → TN for all signals
		// 10/10=100% → TN(UNDERCOMMIT); rate=1.0 → TN(OVERCOMMIT)
		return new PlanScenario(plan, commits, 0, Set.of());
	}

	// ── Report building ───────────────────────────────────────────────────────

	private Map<String, Object> buildReport(Map<String, int[]> matrix) {
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("timestamp", Instant.now().toString());
		report.put("description",
				"Historical risk signal replay benchmark — 12 synthetic RECONCILED plans, pure in-memory");
		report.put("totalPlans", scenarios.size());

		Map<String, Object> signalResults = new LinkedHashMap<>();
		for (String signal : SIGNAL_TYPES) {
			int[] cm = matrix.get(signal);
			int tp = cm[0], fp = cm[1], fn = cm[2], tn = cm[3];
			double precision = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
			double recall = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
			double f1 = (precision + recall) == 0 ? 0.0 : 2 * precision * recall / (precision + recall);

			Map<String, Object> signalReport = new LinkedHashMap<>();
			signalReport.put("predicted", tp + fp);
			signalReport.put("actual", tp + fn);
			signalReport.put("truePositives", tp);
			signalReport.put("falsePositives", fp);
			signalReport.put("falseNegatives", fn);
			signalReport.put("trueNegatives", tn);
			signalReport.put("precision", Math.round(precision * 1000.0) / 1000.0);
			signalReport.put("recall", Math.round(recall * 1000.0) / 1000.0);
			signalReport.put("f1", Math.round(f1 * 1000.0) / 1000.0);
			signalResults.put(signal, signalReport);
		}
		report.put("signalResults", signalResults);
		return report;
	}

	private void logSummary(Map<String, int[]> matrix) {
		log.info("══════════════════════════════════════════════════════════════");
		log.info("  HISTORICAL REPLAY BENCHMARK RESULTS ({} plans)", scenarios.size());
		log.info("  {:30s} {:>6s} {:>6s} {:>6s} {:>6s} {:>9s} {:>9s} {:>6s}", "Signal", "TP", "FP", "FN", "TN",
				"Precision", "Recall", "F1");
		for (String signal : SIGNAL_TYPES) {
			int[] cm = matrix.get(signal);
			int tp = cm[0], fp = cm[1], fn = cm[2], tn = cm[3];
			double precision = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
			double recall = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
			double f1 = (precision + recall) == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
			log.info("  {:30s} {:>6d} {:>6d} {:>6d} {:>6d} {:>9.3f} {:>9.3f} {:>6.3f}", signal, tp, fp, fn, tn,
					precision, recall, f1);
		}
		log.info("══════════════════════════════════════════════════════════════");
	}

	// ── Entity helpers ────────────────────────────────────────────────────────

	private static WeeklyPlan buildPlan(int n, int budgetPoints) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(new UUID(0L, n));
		p.setOwnerUserId(OWNER_ID);
		p.setTeamId(TEAM_ID);
		p.setWeekStartDate(LocalDate.of(2026, 1, 6).plusWeeks(n - 1));
		p.setState(PlanState.RECONCILED);
		p.setCapacityBudgetPoints(budgetPoints);
		p.setLockDeadline(Instant.parse("2026-01-09T17:00:00Z").plusSeconds((long) (n - 1) * 7 * 86400));
		p.setReconcileDeadline(Instant.parse("2026-01-12T17:00:00Z").plusSeconds((long) (n - 1) * 7 * 86400));
		return p;
	}

	private static UUID commitUuid(int planN, int commitN) {
		return new UUID((long) planN, (long) commitN);
	}

	private static WeeklyCommit commit(int planN, int commitN, ChessPiece piece, int points, int streak,
			CommitOutcome outcome) {
		return commitWithId(commitUuid(planN, commitN), piece, points, streak, outcome);
	}

	private static WeeklyCommit commitWithId(UUID id, ChessPiece piece, int points, int streak, CommitOutcome outcome) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(id);
		c.setOwnerUserId(OWNER_ID);
		c.setPlanId(OWNER_ID); // planId not material for benchmark logic
		c.setTitle("Synthetic commit " + id.getLeastSignificantBits());
		c.setChessPiece(piece);
		c.setEstimatePoints(points);
		c.setCarryForwardStreak(streak);
		c.setOutcome(outcome);
		c.setPriorityOrder(1);
		return c;
	}
}
