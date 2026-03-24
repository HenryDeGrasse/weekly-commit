package com.weeklycommit.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.CarryForwardFact;
import com.weeklycommit.domain.entity.ComplianceFact;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.RcdoWeekRollup;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.TeamWeekRollup;
import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.CarryForwardFactRepository;
import com.weeklycommit.domain.repository.ComplianceFactRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.RcdoWeekRollupRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamWeekRollupRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes derived read-model tables from transactional data.
 *
 * <p>
 * Refresh is triggered by lifecycle events (lock, reconcile, scope change,
 * carry-forward) and also runs on a 5-minute scheduled cadence for the current
 * week (fallback if any event is missed).
 *
 * <p>
 * Every public method is idempotent: re-running with the same input produces
 * the same result. Each plan refresh runs in its own transaction
 * ({@code REQUIRES_NEW}) so that a refresh failure never rolls back the main
 * business transaction.
 */
@Service
public class ReadModelRefreshService {

	private static final Logger log = LoggerFactory.getLogger(ReadModelRefreshService.class);

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final TeamMembershipRepository membershipRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final ManagerReviewExceptionRepository exceptionRepo;
	private final LockSnapshotHeaderRepository lockSnapshotRepo;
	private final UserWeekFactRepository userWeekFactRepo;
	private final TeamWeekRollupRepository teamWeekRollupRepo;
	private final RcdoWeekRollupRepository rcdoWeekRollupRepo;
	private final ComplianceFactRepository complianceFactRepo;
	private final CarryForwardFactRepository carryForwardFactRepo;
	private final ObjectMapper objectMapper;

	public ReadModelRefreshService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			TeamMembershipRepository membershipRepo, ScopeChangeEventRepository scopeChangeRepo,
			ManagerReviewExceptionRepository exceptionRepo, LockSnapshotHeaderRepository lockSnapshotRepo,
			UserWeekFactRepository userWeekFactRepo, TeamWeekRollupRepository teamWeekRollupRepo,
			RcdoWeekRollupRepository rcdoWeekRollupRepo, ComplianceFactRepository complianceFactRepo,
			CarryForwardFactRepository carryForwardFactRepo, ObjectMapper objectMapper) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.membershipRepo = membershipRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.exceptionRepo = exceptionRepo;
		this.lockSnapshotRepo = lockSnapshotRepo;
		this.userWeekFactRepo = userWeekFactRepo;
		this.teamWeekRollupRepo = teamWeekRollupRepo;
		this.rcdoWeekRollupRepo = rcdoWeekRollupRepo;
		this.complianceFactRepo = complianceFactRepo;
		this.carryForwardFactRepo = carryForwardFactRepo;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Scheduled refresh (fallback)
	// -------------------------------------------------------------------------

	/**
	 * Refreshes read models for all plans in the current ISO week. Runs every 5
	 * minutes as a fallback in case any lifecycle event was missed.
	 */
	@Scheduled(fixedRate = 300_000)
	public void scheduledRefreshCurrentWeek() {
		LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
		log.debug("Scheduled read-model refresh for week {}", weekStart);

		List<WeeklyPlan> currentPlans = planRepo.findByWeekStartDate(weekStart);
		int refreshed = 0;
		for (WeeklyPlan plan : currentPlans) {
			try {
				refreshForPlan(plan.getId());
				refreshed++;
			} catch (Exception ex) {
				log.warn("Scheduled refresh failed for plan {}: {}", plan.getId(), ex.getMessage());
			}
		}
		log.debug("Scheduled refresh complete: {} plan(s) refreshed for week {}", refreshed, weekStart);
	}

	// -------------------------------------------------------------------------
	// Event-driven refresh entry point
	// -------------------------------------------------------------------------

	/**
	 * Refreshes all derived read-model rows affected by the given plan.
	 *
	 * <p>
	 * Runs in a <em>separate</em> transaction ({@code REQUIRES_NEW}) so that a
	 * refresh failure cannot roll back the calling business transaction.
	 *
	 * @param planId
	 *            the plan whose derived data should be updated
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void refreshForPlan(UUID planId) {
		WeeklyPlan plan = planRepo.findById(planId).orElse(null);
		if (plan == null) {
			log.warn("ReadModelRefreshService: plan {} not found, skipping refresh", planId);
			return;
		}

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);
		List<ScopeChangeEvent> scopeChanges = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId);
		int scopeChangeCount = scopeChanges.size();

		refreshUserWeekFact(plan, commits, scopeChangeCount);
		refreshComplianceFact(plan);
		refreshCarryForwardFacts(plan, commits);
		refreshRcdoWeekRollup(plan, commits, scopeChanges);
		refreshTeamWeekRollup(plan.getTeamId(), plan.getWeekStartDate());
	}

	// -------------------------------------------------------------------------
	// user_week_fact
	// -------------------------------------------------------------------------

	void refreshUserWeekFact(WeeklyPlan plan, List<WeeklyCommit> commits, int scopeChangeCount) {
		int totalPlanned = 0;
		int totalAchieved = 0;
		int carryForwardCount = 0;
		int kingCount = 0;
		int queenCount = 0;

		for (WeeklyCommit c : commits) {
			int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
			totalPlanned += pts;
			if (c.getOutcome() == CommitOutcome.ACHIEVED) {
				totalAchieved += pts;
			}
			if (c.getCarryForwardStreak() > 0) {
				carryForwardCount++;
			}
			if (c.getChessPiece() == ChessPiece.KING)
				kingCount++;
			if (c.getChessPiece() == ChessPiece.QUEEN)
				queenCount++;
		}

		boolean lockCompliance = plan.getState() != PlanState.DRAFT && plan.isCompliant();
		boolean reconcileCompliance = plan.getState() == PlanState.RECONCILED;

		UserWeekFact fact = userWeekFactRepo.findByUserIdAndWeekStart(plan.getOwnerUserId(), plan.getWeekStartDate())
				.orElse(new UserWeekFact());

		fact.setUserId(plan.getOwnerUserId());
		fact.setWeekStart(plan.getWeekStartDate());
		fact.setPlanState(plan.getState().name());
		fact.setLockCompliance(lockCompliance);
		fact.setReconcileCompliance(reconcileCompliance);
		fact.setTotalPlannedPoints(totalPlanned);
		fact.setTotalAchievedPoints(totalAchieved);
		fact.setCommitCount(commits.size());
		fact.setCarryForwardCount(carryForwardCount);
		fact.setScopeChangeCount(scopeChangeCount);
		fact.setKingCount(kingCount);
		fact.setQueenCount(queenCount);
		fact.setRefreshedAt(Instant.now());

		userWeekFactRepo.save(fact);
	}

	// -------------------------------------------------------------------------
	// compliance_fact
	// -------------------------------------------------------------------------

	void refreshComplianceFact(WeeklyPlan plan) {
		boolean notDraft = plan.getState() != PlanState.DRAFT;
		boolean autoLocked = lockSnapshotRepo.findByPlanId(plan.getId()).map(LockSnapshotHeader::isLockedBySystem)
				.orElse(false);
		boolean lockOnTime = notDraft && plan.isCompliant() && !autoLocked;
		boolean lockLate = notDraft && !plan.isCompliant();
		boolean reconcileOnTime = plan.getState() == PlanState.RECONCILED;
		boolean reconcileMissed = plan.getState() != PlanState.RECONCILED
				&& Instant.now().isAfter(plan.getReconcileDeadline());

		ComplianceFact fact = complianceFactRepo
				.findByUserIdAndWeekStart(plan.getOwnerUserId(), plan.getWeekStartDate()).orElse(new ComplianceFact());

		fact.setUserId(plan.getOwnerUserId());
		fact.setWeekStart(plan.getWeekStartDate());
		fact.setLockOnTime(lockOnTime);
		fact.setLockLate(lockLate);
		fact.setAutoLocked(autoLocked);
		fact.setReconcileOnTime(reconcileOnTime);
		fact.setReconcileLate(false); // requires reconciled_at column — not available in v1
		fact.setReconcileMissed(reconcileMissed);
		fact.setRefreshedAt(Instant.now());

		complianceFactRepo.save(fact);
	}

	// -------------------------------------------------------------------------
	// carry_forward_fact
	// -------------------------------------------------------------------------

	void refreshCarryForwardFacts(WeeklyPlan plan, List<WeeklyCommit> commits) {
		for (WeeklyCommit commit : commits) {
			if (commit.getCarryForwardStreak() <= 0) {
				continue; // not a carry-forward commit
			}

			// Approximate source week: plan week minus streak weeks
			LocalDate sourceWeek = plan.getWeekStartDate().minusWeeks(commit.getCarryForwardStreak());

			CarryForwardFact fact = carryForwardFactRepo.findByCommitId(commit.getId()).orElse(new CarryForwardFact());

			fact.setCommitId(commit.getId());
			fact.setSourceWeek(sourceWeek);
			fact.setCurrentWeek(plan.getWeekStartDate());
			fact.setStreakLength(commit.getCarryForwardStreak());
			fact.setRcdoNodeId(commit.getRcdoNodeId());
			fact.setChessPiece(commit.getChessPiece() != null ? commit.getChessPiece().name() : null);
			fact.setRefreshedAt(Instant.now());

			carryForwardFactRepo.save(fact);
		}
	}

	// -------------------------------------------------------------------------
	// rcdo_week_rollup
	// -------------------------------------------------------------------------

	/**
	 * For each RCDO node referenced by commits in this plan, re-computes the full
	 * rollup for that node+week by scanning ALL commits for that node and week.
	 * This guarantees correctness even when multiple teams contribute to the same
	 * RCDO node.
	 */
	void refreshRcdoWeekRollup(WeeklyPlan plan, List<WeeklyCommit> planCommits, List<ScopeChangeEvent> scopeChanges) {
		Set<UUID> nodeIds = collectAffectedRcdoNodeIds(planCommits, scopeChanges);

		for (UUID nodeId : nodeIds) {
			// Re-scan ALL commits for this node+week across all teams
			List<WeeklyCommit> nodeCommits = commitRepo.findByRcdoNodeId(nodeId);
			int plannedPts = 0;
			int achievedPts = 0;
			int commitCount = 0;
			Map<String, Integer> teamBreakdown = new LinkedHashMap<>();

			for (WeeklyCommit c : nodeCommits) {
				// Filter to this specific week via the commit's plan
				WeeklyPlan cp = planRepo.findById(c.getPlanId()).orElse(null);
				if (cp == null || !plan.getWeekStartDate().equals(cp.getWeekStartDate())) {
					continue;
				}
				int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
				plannedPts += pts;
				if (c.getOutcome() == CommitOutcome.ACHIEVED)
					achievedPts += pts;
				commitCount++;
				teamBreakdown.merge(cp.getTeamId().toString(), pts, Integer::sum);
			}

			RcdoWeekRollup rollup = rcdoWeekRollupRepo.findByRcdoNodeIdAndWeekStart(nodeId, plan.getWeekStartDate())
					.orElse(new RcdoWeekRollup());

			rollup.setRcdoNodeId(nodeId);
			rollup.setWeekStart(plan.getWeekStartDate());
			rollup.setPlannedPoints(plannedPts);
			rollup.setAchievedPoints(achievedPts);
			rollup.setCommitCount(commitCount);
			rollup.setTeamContributionBreakdown(toJson(teamBreakdown));
			rollup.setRefreshedAt(Instant.now());

			rcdoWeekRollupRepo.save(rollup);
		}
	}

	// -------------------------------------------------------------------------
	// team_week_rollup
	// -------------------------------------------------------------------------

	/**
	 * Recomputes the full team rollup for the given team + week by reading all
	 * plans and commits. This is completely accurate and idempotent.
	 */
	void refreshTeamWeekRollup(UUID teamId, LocalDate weekStart) {
		List<TeamMembership> memberships = membershipRepo.findByTeamId(teamId);
		List<WeeklyPlan> plans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart);

		int lockedCount = 0;
		int reconciledCount = 0;
		int totalPlanned = 0;
		int totalAchieved = 0;
		int totalCommits = 0;
		int totalCarryForward = 0;
		Map<String, Integer> chessDist = new HashMap<>();

		for (WeeklyPlan p : plans) {
			PlanState state = p.getState();
			if (state == PlanState.LOCKED || state == PlanState.RECONCILING || state == PlanState.RECONCILED) {
				lockedCount++;
			}
			if (state == PlanState.RECONCILED) {
				reconciledCount++;
			}

			List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(p.getId());
			for (WeeklyCommit c : commits) {
				totalCommits++;
				int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
				totalPlanned += pts;
				if (c.getOutcome() == CommitOutcome.ACHIEVED) {
					totalAchieved += pts;
				}
				if (c.getCarryForwardStreak() > 0) {
					totalCarryForward++;
				}
				if (c.getChessPiece() != null) {
					chessDist.merge(c.getChessPiece().name(), 1, Integer::sum);
				}
			}
		}

		double avgCfr = totalCommits == 0 ? 0.0 : (double) totalCarryForward / totalCommits;
		long exceptionCount = exceptionRepo.countByTeamIdAndWeekStartDate(teamId, weekStart);

		TeamWeekRollup rollup = teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, weekStart)
				.orElse(new TeamWeekRollup());

		rollup.setTeamId(teamId);
		rollup.setWeekStart(weekStart);
		rollup.setMemberCount(memberships.size());
		rollup.setLockedCount(lockedCount);
		rollup.setReconciledCount(reconciledCount);
		rollup.setTotalPlannedPoints(totalPlanned);
		rollup.setTotalAchievedPoints(totalAchieved);
		rollup.setExceptionCount((int) exceptionCount);
		rollup.setAvgCarryForwardRate(avgCfr);
		rollup.setChessDistribution(toJson(chessDist));
		rollup.setRefreshedAt(Instant.now());

		teamWeekRollupRepo.save(rollup);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private Set<UUID> collectAffectedRcdoNodeIds(List<WeeklyCommit> planCommits, List<ScopeChangeEvent> scopeChanges) {
		Set<UUID> nodeIds = new LinkedHashSet<>();
		for (WeeklyCommit c : planCommits) {
			if (c.getRcdoNodeId() != null) {
				nodeIds.add(c.getRcdoNodeId());
			}
		}
		for (ScopeChangeEvent event : scopeChanges) {
			if (event.getCategory() != ScopeChangeCategory.RCDO_CHANGED) {
				continue;
			}
			UUID previousNodeId = parseUuid(event.getPreviousValue());
			UUID newNodeId = parseUuid(event.getNewValue());
			if (previousNodeId != null) {
				nodeIds.add(previousNodeId);
			}
			if (newNodeId != null) {
				nodeIds.add(newNodeId);
			}
		}
		return nodeIds;
	}

	private UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
