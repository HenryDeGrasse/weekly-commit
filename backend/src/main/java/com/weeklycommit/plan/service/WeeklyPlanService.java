package com.weeklycommit.plan.service;

import com.weeklycommit.audit.service.AuditLogService;
import com.weeklycommit.config.service.ConfigurationService;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.dto.PlanWithCommitsResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WeeklyPlanService {

	private final WeeklyPlanRepository planRepo;
	private final UserAccountRepository userRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ConfigurationService configurationService;
	private final AuditLogService auditLogService;

	/**
	 * Self-reference through the Spring proxy so that calls to
	 * {@link #createPlanInNewTransaction} acquire their own transaction even when
	 * invoked from within this bean. {@code @Lazy} breaks the circular-dependency
	 * cycle that would otherwise prevent startup.
	 */
	@Autowired
	@Lazy
	private WeeklyPlanService self;

	public WeeklyPlanService(WeeklyPlanRepository planRepo, UserAccountRepository userRepo,
			WeeklyCommitRepository commitRepo, ConfigurationService configurationService,
			AuditLogService auditLogService) {
		this.planRepo = planRepo;
		this.userRepo = userRepo;
		this.commitRepo = commitRepo;
		this.configurationService = configurationService;
		this.auditLogService = auditLogService;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Returns the existing plan for the given user+week, creating one if it does
	 * not yet exist. Safe under concurrent requests: if two requests race to create
	 * the same plan simultaneously (e.g. React 18 Strict Mode double-invocation),
	 * only one INSERT will succeed; the other catches the unique-constraint
	 * violation and falls back to a fresh SELECT.
	 *
	 * <p>
	 * Runs with {@code NOT_SUPPORTED} propagation so that the find and the create
	 * each use their own short-lived transactions (via {@link #self}) rather than
	 * sharing one transaction that would be rolled back on conflict.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public WeeklyPlan getOrCreatePlan(UUID userId, LocalDate weekStartDate) {
		// Fast-path: plan already exists — no write transaction needed.
		var existing = planRepo.findByOwnerUserIdAndWeekStartDate(userId, weekStartDate);
		if (existing.isPresent()) {
			return existing.get();
		}

		try {
			// Delegate to a REQUIRES_NEW method (via the Spring proxy) so the
			// INSERT commits independently before we continue.
			return self.createPlanInNewTransaction(userId, weekStartDate);
		} catch (DataIntegrityViolationException ex) {
			// A concurrent request won the race and already committed the plan.
			// Do a fresh SELECT — it is now visible in a new read.
			return planRepo.findByOwnerUserIdAndWeekStartDate(userId, weekStartDate).orElseThrow(
					() -> new IllegalStateException("Plan not found after duplicate-key violation for user=" + userId
							+ " week=" + weekStartDate, ex));
		}
	}

	/**
	 * Creates the draft plan in its own brand-new transaction so that the INSERT is
	 * committed (or rolled back on conflict) before the caller in
	 * {@link #getOrCreatePlan} sees the result.
	 *
	 * <p>
	 * Must be called through the Spring proxy (i.e. via {@link #self}) for the
	 * {@code REQUIRES_NEW} propagation to take effect.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public WeeklyPlan createPlanInNewTransaction(UUID userId, LocalDate weekStartDate) {
		return createDraftPlan(userId, weekStartDate);
	}

	/**
	 * Convenience: resolve the current week's Monday and delegate to getOrCreate.
	 */
	public WeeklyPlan getMyCurrentWeekPlan(UUID userId) {
		return getOrCreatePlan(userId, currentWeekStart());
	}

	/** Returns the plan with its commits ordered by priority. */
	@Transactional(readOnly = true)
	public PlanWithCommitsResponse getPlanWithCommits(UUID planId) {
		WeeklyPlan plan = findById(planId);
		return toResponse(plan);
	}

	/** Lists all plans for a user in descending week order. */
	@Transactional(readOnly = true)
	public List<PlanResponse> listPlansForUser(UUID userId) {
		return planRepo.findByOwnerUserIdOrderByWeekStartDateDesc(userId).stream().map(PlanResponse::from).toList();
	}

	/** Calculates the Monday of the current ISO week (UTC). */
	public static LocalDate currentWeekStart() {
		return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan createDraftPlan(UUID userId, LocalDate weekStartDate) {
		UserAccount user = userRepo.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

		if (user.getHomeTeamId() == null) {
			throw new PlanValidationException("User " + userId + " has no home team; cannot create a weekly plan");
		}

		UUID teamId = user.getHomeTeamId();
		int budgetPoints = configurationService.getEffectiveCapacity(userId, weekStartDate).budgetPoints();

		WeeklyPlan plan = new WeeklyPlan();
		plan.setOwnerUserId(userId);
		plan.setTeamId(teamId);
		plan.setWeekStartDate(weekStartDate);
		plan.setState(PlanState.DRAFT);
		plan.setCapacityBudgetPoints(budgetPoints);

		// Derive per-team cadence deadlines from effective configuration
		plan.setLockDeadline(configurationService.computeLockDeadline(teamId, weekStartDate));
		plan.setReconcileDeadline(configurationService.computeReconcileDeadline(teamId, weekStartDate));

		WeeklyPlan saved = planRepo.save(plan);

		auditLogService.record(AuditLogService.PLAN_CREATED, userId, "IC", AuditLogService.TARGET_PLAN, saved.getId(),
				null, Map.of("ownerUserId", userId.toString(), "weekStartDate", weekStartDate.toString(), "teamId",
						teamId.toString()));

		return saved;
	}

	WeeklyPlan findById(UUID planId) {
		return planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + planId));
	}

	PlanWithCommitsResponse toResponse(WeeklyPlan plan) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		int totalPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
				.sum();
		return new PlanWithCommitsResponse(PlanResponse.from(plan), commits.stream().map(CommitResponse::from).toList(),
				totalPoints);
	}
}
