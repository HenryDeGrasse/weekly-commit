package com.weeklycommit.team.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.CapacityOverride;
import com.weeklycommit.domain.entity.LockSnapshotCommit;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ManagerComment;
import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.CapacityOverrideRepository;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ManagerCommentRepository;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.dto.CapacityOverrideResponse;
import com.weeklycommit.team.dto.CommentResponse;
import com.weeklycommit.team.dto.ExceptionResponse;
import com.weeklycommit.team.exception.AccessDeniedException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the manager-review features: exception queue, plan/commit comments,
 * capacity overrides, and exception resolution.
 *
 * <p>
 * Exception detection is idempotent — calling {@code getExceptionQueue}
 * multiple times for the same team + week will not create duplicate records.
 */
@Service
@Transactional
public class ManagerReviewService {

	/** Carry-forward streak threshold for the REPEATED_CARRY_FORWARD exception. */
	static final int REPEATED_CARRY_FORWARD_THRESHOLD = 2;

	/**
	 * Minimum number of scope-change events to trigger HIGH_SCOPE_VOLATILITY.
	 */
	static final int HIGH_VOLATILITY_THRESHOLD = 3;

	/** Percentage increase in points that triggers POST_LOCK_SCOPE_INCREASE. */
	static final double POINTS_INCREASE_THRESHOLD = 0.20;

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final LockSnapshotHeaderRepository lockHeaderRepo;
	private final LockSnapshotCommitRepository lockCommitRepo;
	private final ManagerReviewExceptionRepository exceptionRepo;
	private final ManagerCommentRepository commentRepo;
	private final CapacityOverrideRepository capacityOverrideRepo;
	private final UserAccountRepository userRepo;
	private final TeamMembershipRepository membershipRepo;
	private final AuthorizationService authService;
	private final ObjectMapper objectMapper;

	/** Optional — injected when the RAG module is active; null-safe throughout. */
	@Autowired(required = false)
	private com.weeklycommit.ai.rag.SemanticIndexService semanticIndexService;

	public ManagerReviewService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository scopeChangeRepo, LockSnapshotHeaderRepository lockHeaderRepo,
			LockSnapshotCommitRepository lockCommitRepo, ManagerReviewExceptionRepository exceptionRepo,
			ManagerCommentRepository commentRepo, CapacityOverrideRepository capacityOverrideRepo,
			UserAccountRepository userRepo, TeamMembershipRepository membershipRepo, AuthorizationService authService,
			ObjectMapper objectMapper) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.lockHeaderRepo = lockHeaderRepo;
		this.lockCommitRepo = lockCommitRepo;
		this.exceptionRepo = exceptionRepo;
		this.commentRepo = commentRepo;
		this.capacityOverrideRepo = capacityOverrideRepo;
		this.userRepo = userRepo;
		this.membershipRepo = membershipRepo;
		this.authService = authService;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Exception queue
	// -------------------------------------------------------------------------

	/**
	 * Detects and persists exceptions for all team members' plans in the given
	 * week, then returns the unresolved exception list ordered by severity.
	 *
	 * <p>
	 * Detection is idempotent: existing unresolved exceptions are not duplicated.
	 *
	 * <p>
	 * Ordering: HIGH severity first, then MEDIUM, then LOW. Within HIGH,
	 * {@link ExceptionType#KING_CHANGED_POST_LOCK} precedes other HIGH types.
	 *
	 * @param callerId
	 *            must have MANAGER or ADMIN role
	 */
	public List<ExceptionResponse> getExceptionQueue(UUID teamId, LocalDate weekStart, UUID callerId) {
		UserRole role = authService.getCallerRole(callerId);
		if (role == UserRole.IC) {
			throw new AccessDeniedException("Only MANAGER or ADMIN may access the exception queue");
		}

		List<WeeklyPlan> plans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart);
		Instant now = Instant.now();

		for (WeeklyPlan plan : plans) {
			detectAndPersistExceptions(plan, teamId, weekStart, now);
		}

		List<ManagerReviewException> exceptions = exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId,
				weekStart, false);

		// Resolve display names in bulk to avoid N+1 queries
		java.util.Set<UUID> userIds = exceptions.stream().map(ManagerReviewException::getUserId)
				.collect(java.util.stream.Collectors.toSet());
		java.util.Map<UUID, String> displayNames = userRepo.findAllById(userIds).stream()
				.collect(java.util.stream.Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));

		return exceptions.stream().sorted(exceptionComparator())
				.map(e -> ExceptionResponse.from(e, displayNames.get(e.getUserId())))
				.toList();
	}

	// -------------------------------------------------------------------------
	// Add comment
	// -------------------------------------------------------------------------

	/**
	 * Adds a manager comment on a plan or commit.
	 *
	 * @param targetType
	 *            "PLAN" or "COMMIT"
	 * @param targetId
	 *            the plan ID or commit ID
	 * @param managerId
	 *            the commenting manager
	 * @param text
	 *            the comment text
	 * @throws AccessDeniedException
	 *             if the caller is not the direct manager of the target's owner
	 * @throws PlanValidationException
	 *             if {@code targetType} is unknown or the target does not exist
	 */
	public CommentResponse addComment(String targetType, UUID targetId, UUID managerId, String text) {
		if (text == null || text.isBlank()) {
			throw new PlanValidationException("Comment text must not be blank");
		}

		UUID targetOwnerUserId;
		UUID planId = null;
		UUID commitId = null;

		if ("PLAN".equalsIgnoreCase(targetType)) {
			WeeklyPlan plan = planRepo.findById(targetId)
					.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + targetId));
			targetOwnerUserId = plan.getOwnerUserId();
			planId = targetId;
		} else if ("COMMIT".equalsIgnoreCase(targetType)) {
			WeeklyCommit commit = commitRepo.findById(targetId)
					.orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + targetId));
			targetOwnerUserId = commit.getOwnerUserId();
			commitId = targetId;
		} else {
			throw new PlanValidationException("Unknown target type: " + targetType + "; expected PLAN or COMMIT");
		}

		// Only direct managers may comment on direct reports' data
		authService.checkIsDirectManager(managerId, targetOwnerUserId);

		ManagerComment comment = new ManagerComment();
		comment.setPlanId(planId);
		comment.setCommitId(commitId);
		comment.setAuthorUserId(managerId);
		comment.setContent(text);
		ManagerComment saved = commentRepo.save(comment);
		if (semanticIndexService != null) {
			semanticIndexService.indexEntity(com.weeklycommit.ai.rag.SemanticIndexService.TYPE_MANAGER_COMMENT,
					saved.getId());
		}

		return CommentResponse.from(saved);
	}

	// -------------------------------------------------------------------------
	// Set capacity override
	// -------------------------------------------------------------------------

	/**
	 * Sets (or updates) the capacity budget override for a user for a given week.
	 *
	 * @param managerId
	 *            the manager applying the override
	 * @param userId
	 *            the IC whose capacity is being overridden
	 * @param weekStart
	 *            the target week
	 * @param overridePoints
	 *            the new capacity budget
	 * @param reason
	 *            required explanation for the override
	 * @throws AccessDeniedException
	 *             if the caller is not the direct manager of the target user
	 */
	public CapacityOverrideResponse setCapacityOverride(UUID managerId, UUID userId, LocalDate weekStart,
			int overridePoints, String reason) {
		if (overridePoints <= 0) {
			throw new PlanValidationException("Override points must be positive");
		}

		// Verify managerId is a direct manager of userId
		authService.checkIsDirectManager(managerId, userId);

		// Upsert: update existing override or create a new one
		CapacityOverride override = capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, weekStart)
				.orElse(new CapacityOverride());

		override.setUserId(userId);
		override.setWeekStartDate(weekStart);
		override.setBudgetPoints(overridePoints);
		override.setReason(reason);
		override.setSetByManagerId(managerId);

		CapacityOverride saved = capacityOverrideRepo.save(override);

		// Also update the weekly plan's capacity budget if a plan exists
		planRepo.findByOwnerUserIdAndWeekStartDate(userId, weekStart).ifPresent(plan -> {
			plan.setCapacityBudgetPoints(overridePoints);
			planRepo.save(plan);
		});

		return CapacityOverrideResponse.from(saved);
	}

	// -------------------------------------------------------------------------
	// Resolve exception
	// -------------------------------------------------------------------------

	/**
	 * Marks a manager-review exception as resolved.
	 *
	 * @param exceptionId
	 *            the exception to resolve
	 * @param resolution
	 *            the resolution note
	 * @param resolverId
	 *            the manager/admin resolving the exception
	 * @throws AccessDeniedException
	 *             if the caller is not MANAGER or ADMIN
	 */
	public ExceptionResponse resolveException(UUID exceptionId, String resolution, UUID resolverId) {
		UserRole role = authService.getCallerRole(resolverId);
		if (role == UserRole.IC) {
			throw new AccessDeniedException("Only MANAGER or ADMIN may resolve exceptions");
		}

		ManagerReviewException exception = exceptionRepo.findById(exceptionId)
				.orElseThrow(() -> new ResourceNotFoundException("Exception not found: " + exceptionId));

		if (exception.isResolved()) {
			throw new PlanValidationException("Exception " + exceptionId + " is already resolved");
		}

		if (resolution == null || resolution.isBlank()) {
			throw new PlanValidationException("Resolution text is required to resolve an exception");
		}

		exception.setResolved(true);
		exception.setResolution(resolution);
		exception.setResolvedAt(Instant.now());
		exception.setResolvedById(resolverId);
		ManagerReviewException saved = exceptionRepo.save(exception);

		String displayName = userRepo.findById(saved.getUserId())
				.map(UserAccount::getDisplayName).orElse(null);
		return ExceptionResponse.from(saved, displayName);
	}

	// -------------------------------------------------------------------------
	// Exception detection
	// -------------------------------------------------------------------------

	/**
	 * Detects all applicable exception types for the given plan and persists any
	 * that have not already been recorded (idempotent).
	 */
	void detectAndPersistExceptions(WeeklyPlan plan, UUID teamId, LocalDate weekStart, Instant now) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		int totalPoints = commits.stream().filter(c -> c.getOutcome() != CommitOutcome.CANCELED)
				.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();

		// --- MISSED_LOCK: still DRAFT past lock deadline ---
		if (plan.getState() == PlanState.DRAFT && now.isAfter(plan.getLockDeadline())) {
			ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.MISSED_LOCK,
					ExceptionSeverity.MEDIUM, weekStart, "Plan was not locked by the deadline");
		}

		// --- AUTO_LOCKED: system locked with or without errors ---
		if (plan.isSystemLockedWithErrors()) {
			ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.AUTO_LOCKED,
					ExceptionSeverity.HIGH, weekStart, "Plan was auto-locked by the system (manual lock was missed)");
		}

		// --- MISSED_RECONCILE: past reconcile deadline and not RECONCILED ---
		if (plan.getState() != PlanState.RECONCILED && now.isAfter(plan.getReconcileDeadline())) {
			ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.MISSED_RECONCILE,
					ExceptionSeverity.MEDIUM, weekStart, "Plan reconciliation was not completed by the deadline");
		}

		// --- OVER_BUDGET: total committed points exceed capacity budget ---
		if (totalPoints > plan.getCapacityBudgetPoints()) {
			ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.OVER_BUDGET,
					ExceptionSeverity.MEDIUM, weekStart, "Total committed points (" + totalPoints
							+ ") exceed capacity budget (" + plan.getCapacityBudgetPoints() + ")");
		}

		// --- REPEATED_CARRY_FORWARD: any commit has streak >= 2 ---
		boolean hasRepeatedCarryForward = commits.stream()
				.anyMatch(c -> c.getCarryForwardStreak() >= REPEATED_CARRY_FORWARD_THRESHOLD);
		if (hasRepeatedCarryForward) {
			int maxStreak = commits.stream().mapToInt(WeeklyCommit::getCarryForwardStreak).max().orElse(0);
			ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.REPEATED_CARRY_FORWARD,
					ExceptionSeverity.LOW, weekStart,
					"One or more commits have been carried forward " + maxStreak + " weeks in a row");
		}

		// --- Scope-change based exceptions (only for non-DRAFT plans) ---
		if (plan.getState() != PlanState.DRAFT) {
			List<ScopeChangeEvent> events = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId());

			// HIGH_SCOPE_VOLATILITY: more than 3 scope-change events
			if (events.size() > HIGH_VOLATILITY_THRESHOLD) {
				ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.HIGH_SCOPE_VOLATILITY,
						ExceptionSeverity.MEDIUM, weekStart, "Plan has " + events.size()
								+ " scope-change events post-lock (threshold: " + HIGH_VOLATILITY_THRESHOLD + ")");
			}

			// KING_CHANGED_POST_LOCK: King commit added or chess piece changed to/from KING
			if (detectKingChangePostLock(commits, events)) {
				ensureException(teamId, plan.getId(), plan.getOwnerUserId(), ExceptionType.KING_CHANGED_POST_LOCK,
						ExceptionSeverity.HIGH, weekStart,
						"A King-level commit was added or changed after the plan was locked");
			}

			// POST_LOCK_SCOPE_INCREASE: >20% points increase from baseline
			detectPostLockScopeIncrease(plan, commits)
					.ifPresent(description -> ensureException(teamId, plan.getId(), plan.getOwnerUserId(),
							ExceptionType.POST_LOCK_SCOPE_INCREASE, ExceptionSeverity.HIGH, weekStart, description));
		}
	}

	/**
	 * Returns {@code true} if any scope-change event indicates a King commit was
	 * added or a commit was changed to/from KING after lock.
	 */
	boolean detectKingChangePostLock(List<WeeklyCommit> currentCommits, List<ScopeChangeEvent> events) {
		for (ScopeChangeEvent e : events) {
			if (e.getCategory() == ScopeChangeCategory.COMMIT_ADDED) {
				// Check if the new commit (still present) is a KING
				String newVal = e.getNewValue();
				if (newVal != null) {
					try {
						JsonNode node = objectMapper.readTree(newVal);
						if (ChessPiece.KING.name().equals(node.path("chessPiece").asText(null))) {
							return true;
						}
					} catch (JsonProcessingException ignored) {
						// Malformed JSON — fall back to checking current commits by commit ID
					}
				}
				// Also check the current state of the associated commit
				if (e.getCommitId() != null) {
					boolean commitIsKing = currentCommits.stream().filter(c -> c.getId().equals(e.getCommitId()))
							.anyMatch(c -> c.getChessPiece() == ChessPiece.KING);
					if (commitIsKing)
						return true;
				}
			} else if (e.getCategory() == ScopeChangeCategory.CHESS_PIECE_CHANGED) {
				String prev = e.getPreviousValue();
				String next = e.getNewValue();
				if (ChessPiece.KING.name().equals(prev) || ChessPiece.KING.name().equals(next)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns an optional description if post-lock points increased by more than
	 * 20% from the lock baseline.
	 */
	Optional<String> detectPostLockScopeIncrease(WeeklyPlan plan, List<WeeklyCommit> currentCommits) {
		Optional<LockSnapshotHeader> headerOpt = lockHeaderRepo.findByPlanId(plan.getId());
		if (headerOpt.isEmpty())
			return Optional.empty();

		List<LockSnapshotCommit> snapshots = lockCommitRepo.findBySnapshotId(headerOpt.get().getId());
		int baselinePoints = snapshots.stream().mapToInt(sc -> parseEstimatePoints(sc.getSnapshotData())).sum();

		if (baselinePoints <= 0)
			return Optional.empty();

		int currentPoints = currentCommits.stream().filter(c -> c.getOutcome() != CommitOutcome.CANCELED)
				.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();

		double increase = (double) (currentPoints - baselinePoints) / baselinePoints;
		if (increase > POINTS_INCREASE_THRESHOLD) {
			return Optional
					.of(String.format("Total estimate points increased %.0f%% from the lock baseline (%d → %d points)",
							increase * 100, baselinePoints, currentPoints));
		}
		return Optional.empty();
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/**
	 * Persists a new exception if one with the same plan+type combination doesn't
	 * already exist as unresolved.
	 */
	private void ensureException(UUID teamId, UUID planId, UUID userId, ExceptionType type, ExceptionSeverity severity,
			LocalDate weekStart, String description) {
		Optional<ManagerReviewException> existing = exceptionRepo.findByPlanIdAndExceptionTypeAndResolved(planId, type,
				false);
		if (existing.isPresent())
			return;

		ManagerReviewException exc = new ManagerReviewException();
		exc.setTeamId(teamId);
		exc.setPlanId(planId);
		exc.setUserId(userId);
		exc.setExceptionType(type);
		exc.setSeverity(severity);
		exc.setDescription(description);
		exc.setWeekStartDate(weekStart);
		exceptionRepo.save(exc);
	}

	/**
	 * Comparator for exception ordering: HIGH before MEDIUM before LOW; within
	 * HIGH, KING_CHANGED_POST_LOCK first.
	 */
	private Comparator<ManagerReviewException> exceptionComparator() {
		return Comparator.<ManagerReviewException, Integer>comparing(e -> e.getSeverity().ordinal())
				.thenComparing(e -> e.getExceptionType() == ExceptionType.KING_CHANGED_POST_LOCK ? 0 : 1)
				.thenComparing(e -> e.getExceptionType().ordinal());
	}

	/**
	 * Parses the {@code estimatePoints} field from a lock-snapshot-commit JSON
	 * blob.
	 */
	private int parseEstimatePoints(String snapshotData) {
		if (snapshotData == null)
			return 0;
		try {
			JsonNode node = objectMapper.readTree(snapshotData);
			JsonNode pts = node.path("estimatePoints");
			return pts.isNumber() ? pts.intValue() : 0;
		} catch (JsonProcessingException e) {
			return 0;
		}
	}

	private UserAccount requireUser(UUID userId) {
		return userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
	}
}
