package com.weeklycommit.lock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.service.RiskDetectionService;
import com.weeklycommit.domain.entity.LockSnapshotCommit;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.NotificationEvent;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.lock.dto.LockSnapshotHeaderResponse;
import com.weeklycommit.lock.dto.ValidationError;
import com.weeklycommit.notification.service.NotificationService;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.dto.PlanWithCommitsResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.report.service.ReadModelRefreshService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LockService {

	private static final Logger log = LoggerFactory.getLogger(LockService.class);

	private static final int MAX_KING_PER_WEEK = 1;
	private static final int MAX_QUEEN_PER_WEEK = 2;
	private static final java.util.Set<Integer> VALID_ESTIMATE_POINTS = java.util.Set.of(1, 2, 3, 5, 8);

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final LockSnapshotHeaderRepository headerRepo;
	private final LockSnapshotCommitRepository commitSnapshotRepo;
	private final RcdoNodeRepository rcdoNodeRepo;
	private final ObjectMapper objectMapper;
	private final NotificationService notificationService;
	private final RiskDetectionService riskDetectionService;
	private final ReadModelRefreshService readModelRefreshService;

	public LockService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			LockSnapshotHeaderRepository headerRepo, LockSnapshotCommitRepository commitSnapshotRepo,
			RcdoNodeRepository rcdoNodeRepo, ObjectMapper objectMapper, NotificationService notificationService,
			RiskDetectionService riskDetectionService, ReadModelRefreshService readModelRefreshService) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.headerRepo = headerRepo;
		this.commitSnapshotRepo = commitSnapshotRepo;
		this.rcdoNodeRepo = rcdoNodeRepo;
		this.objectMapper = objectMapper;
		this.notificationService = notificationService;
		this.riskDetectionService = riskDetectionService;
		this.readModelRefreshService = readModelRefreshService;
	}

	// -------------------------------------------------------------------------
	// Manual lock
	// -------------------------------------------------------------------------

	/**
	 * Attempts to manually lock a DRAFT plan.
	 *
	 * <ul>
	 * <li>If the plan is already LOCKED the call is idempotent and returns
	 * success.</li>
	 * <li>If the plan is in any other non-DRAFT state a
	 * {@link PlanValidationException} is thrown (400).</li>
	 * <li>If hard validation fails the plan is <em>not</em> locked and the response
	 * contains the error list.</li>
	 * <li>On success the state transitions to LOCKED and a baseline snapshot is
	 * captured in the same transaction.</li>
	 * </ul>
	 */
	public LockResponse lockPlan(UUID planId, UUID actorUserId) {
		WeeklyPlan plan = findPlan(planId);

		if (plan.getState() == PlanState.LOCKED) {
			// Idempotent: already locked
			return LockResponse.success(buildPlanResponse(plan));
		}

		if (plan.getState() != PlanState.DRAFT) {
			throw new PlanValidationException("Plan " + planId + " cannot be locked from state " + plan.getState());
		}

		List<ValidationError> errors = validateForLock(plan);
		if (!errors.isEmpty()) {
			return LockResponse.validationFailed(errors);
		}

		// Compliance: on-time if locked before or at the deadline
		boolean onTime = !Instant.now().isAfter(plan.getLockDeadline());
		plan.setCompliant(onTime);
		plan.setState(PlanState.LOCKED);
		plan.setSystemLockedWithErrors(false);
		planRepo.save(plan);

		captureSnapshot(plan, false, List.of());
		triggerRiskDetection(plan.getId());
		triggerReadModelRefresh(plan.getId());

		return LockResponse.success(buildPlanResponse(plan));
	}

	// -------------------------------------------------------------------------
	// Auto-lock (called by scheduled job, idempotent)
	// -------------------------------------------------------------------------

	/**
	 * Locks an expired DRAFT plan as a system action. If the plan has validation
	 * errors the lock still proceeds but {@code system_locked_with_errors} is set.
	 *
	 * <p>
	 * After locking, an {@code AUTO_LOCK_OCCURRED} notification is sent to the plan
	 * owner and a {@code MANAGER_EXCEPTION_DIGEST} notification is sent to the
	 * team's manager (if one exists).
	 */
	public void autoLockPlan(UUID planId) {
		WeeklyPlan plan = planRepo.findById(planId).orElse(null);
		if (plan == null || plan.getState() != PlanState.DRAFT) {
			return; // idempotent: nothing to do
		}

		List<ValidationError> errors = validateForLock(plan);

		plan.setState(PlanState.LOCKED);
		plan.setCompliant(false); // auto-lock is never manually compliant
		plan.setSystemLockedWithErrors(!errors.isEmpty());
		planRepo.save(plan);

		captureSnapshot(plan, true, errors);
		triggerRiskDetection(plan.getId());
		triggerReadModelRefresh(plan.getId());

		// Notification hooks — fire-and-forget: failures must not roll back the lock
		try {
			notificationService.createNotification(plan.getOwnerUserId(), NotificationEvent.AUTO_LOCK_OCCURRED,
					"Plan auto-locked",
					"Your weekly plan for " + plan.getWeekStartDate() + " was automatically locked by the system.",
					plan.getId(), "PLAN");

			notificationService.findManagerForTeam(plan.getTeamId())
					.ifPresent(
							managerId -> notificationService.createNotification(managerId,
									NotificationEvent.MANAGER_EXCEPTION_DIGEST, "Auto-lock on team plan",
									"A plan for week " + plan.getWeekStartDate() + " in your team was auto-locked"
											+ (errors.isEmpty() ? "." : " with validation errors."),
									plan.getId(), "PLAN"));
		} catch (Exception ex) {
			log.warn("Failed to send auto-lock notifications for plan {}: {}", planId, ex.getMessage());
		}
	}

	// -------------------------------------------------------------------------
	// Snapshot retrieval
	// -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public LockSnapshotHeaderResponse getLockSnapshot(UUID planId) {
		LockSnapshotHeader header = headerRepo.findByPlanId(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Lock snapshot not found for plan: " + planId));
		return LockSnapshotHeaderResponse.from(header);
	}

	// -------------------------------------------------------------------------
	// Hard validation
	// -------------------------------------------------------------------------

	/** Returns the list of hard-validation errors. Empty means valid. */
	List<ValidationError> validateForLock(WeeklyPlan plan) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		List<ValidationError> errors = new ArrayList<>();

		if (commits.isEmpty()) {
			errors.add(ValidationError.of("commits", "At least one commit is required to lock the plan"));
			return errors; // no point validating individual commits
		}

		int kingCount = 0;
		int queenCount = 0;

		for (WeeklyCommit commit : commits) {
			String prefix = "commit[" + commit.getId() + "]";

			if (commit.getTitle() == null || commit.getTitle().isBlank()) {
				errors.add(ValidationError.of(prefix + ".title", "Title is required"));
			}
			if (commit.getChessPiece() == null) {
				errors.add(ValidationError.of(prefix + ".chessPiece", "Chess piece is required"));
				continue; // skip piece-dependent checks
			}
			if (commit.getPriorityOrder() < 1) {
				errors.add(ValidationError.of(prefix + ".priorityOrder", "Priority order must be ≥ 1"));
			}
			if (commit.getRcdoNodeId() == null) {
				errors.add(ValidationError.of(prefix + ".rcdoNodeId", "Primary RCDO link is required at lock time"));
			}
			if (commit.getEstimatePoints() == null) {
				errors.add(ValidationError.of(prefix + ".estimatePoints", "Estimate points are required at lock time"));
			} else if (!VALID_ESTIMATE_POINTS.contains(commit.getEstimatePoints())) {
				errors.add(ValidationError.of(prefix + ".estimatePoints",
						"Estimate points must be one of {1, 2, 3, 5, 8}"));
			}
			if ((commit.getChessPiece() == ChessPiece.KING || commit.getChessPiece() == ChessPiece.QUEEN)
					&& (commit.getSuccessCriteria() == null || commit.getSuccessCriteria().isBlank())) {
				errors.add(ValidationError.of(prefix + ".successCriteria",
						"Success criteria required for " + commit.getChessPiece() + " commits"));
			}
			if (commit.getChessPiece() == ChessPiece.KING)
				kingCount++;
			if (commit.getChessPiece() == ChessPiece.QUEEN)
				queenCount++;
		}

		if (kingCount > MAX_KING_PER_WEEK) {
			errors.add(ValidationError.of("commits.king", "Maximum 1 King commit per week; found " + kingCount));
		}
		if (queenCount > MAX_QUEEN_PER_WEEK) {
			errors.add(ValidationError.of("commits.queen", "Maximum 2 Queen commits per week; found " + queenCount));
		}

		return errors;
	}

	// -------------------------------------------------------------------------
	// Snapshot creation (called within existing transaction)
	// -------------------------------------------------------------------------

	private void triggerRiskDetection(UUID planId) {
		try {
			riskDetectionService.detectAndStoreRiskSignalsById(planId);
		} catch (Exception ex) {
			log.warn("Failed to compute risk signals for plan {}: {}", planId, ex.getMessage());
		}
	}

	private void triggerReadModelRefresh(UUID planId) {
		try {
			readModelRefreshService.refreshForPlan(planId);
		} catch (Exception ex) {
			log.warn("Failed to refresh read models for plan {}: {}", planId, ex.getMessage());
		}
	}

	private void captureSnapshot(WeeklyPlan plan, boolean bySystem, List<ValidationError> validationErrors) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		String payload = buildSnapshotPayload(plan, commits, validationErrors);

		LockSnapshotHeader header = new LockSnapshotHeader();
		header.setPlanId(plan.getId());
		header.setLockedBySystem(bySystem);
		header.setSnapshotPayload(payload);
		LockSnapshotHeader saved = headerRepo.save(header);

		for (WeeklyCommit commit : commits) {
			LockSnapshotCommit sc = new LockSnapshotCommit();
			sc.setSnapshotId(saved.getId());
			sc.setCommitId(commit.getId());
			sc.setSnapshotData(buildCommitSnapshot(commit));
			commitSnapshotRepo.save(sc);
		}

		// Back-reference on plan
		plan.setLockSnapshotId(saved.getId());
		planRepo.save(plan);
	}

	// -------------------------------------------------------------------------
	// JSON snapshot builders
	// -------------------------------------------------------------------------

	private String buildSnapshotPayload(WeeklyPlan plan, List<WeeklyCommit> commits,
			List<ValidationError> validationErrors) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("planId", str(plan.getId()));
		m.put("ownerUserId", str(plan.getOwnerUserId()));
		m.put("teamId", str(plan.getTeamId()));
		m.put("weekStartDate", plan.getWeekStartDate().toString());
		m.put("capacityBudgetPoints", plan.getCapacityBudgetPoints());
		m.put("lockedAt", Instant.now().toString());
		m.put("commits", commits.stream().map(this::buildCommitSnapshotMap).toList());
		if (!validationErrors.isEmpty()) {
			m.put("validationErrors", validationErrors);
		}
		return toJson(m);
	}

	private String buildCommitSnapshot(WeeklyCommit commit) {
		return toJson(buildCommitSnapshotMap(commit));
	}

	private Map<String, Object> buildCommitSnapshotMap(WeeklyCommit commit) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", str(commit.getId()));
		m.put("title", commit.getTitle());
		m.put("chessPiece", commit.getChessPiece() != null ? commit.getChessPiece().name() : null);
		m.put("priorityOrder", commit.getPriorityOrder());
		m.put("estimatePoints", commit.getEstimatePoints());
		m.put("successCriteria", commit.getSuccessCriteria());
		m.put("description", commit.getDescription());
		m.put("rcdoNodeId", str(commit.getRcdoNodeId()));
		m.put("workItemId", str(commit.getWorkItemId()));
		m.put("rcdoPath", buildRcdoPath(commit.getRcdoNodeId()));
		return m;
	}

	private List<Map<String, Object>> buildRcdoPath(UUID nodeId) {
		if (nodeId == null)
			return List.of();
		List<Map<String, Object>> path = new ArrayList<>();
		UUID current = nodeId;
		while (current != null) {
			RcdoNode node = rcdoNodeRepo.findById(current).orElse(null);
			if (node == null)
				break;
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("id", str(node.getId()));
			entry.put("nodeType", node.getNodeType().name());
			entry.put("title", node.getTitle());
			entry.put("status", node.getStatus().name());
			path.add(0, entry); // root first
			current = node.getParentId();
		}
		return path;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan findPlan(UUID planId) {
		return planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + planId));
	}

	private PlanWithCommitsResponse buildPlanResponse(WeeklyPlan plan) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		int total = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();
		return new PlanWithCommitsResponse(PlanResponse.from(plan), commits.stream().map(CommitResponse::from).toList(),
				total);
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize snapshot payload", e);
		}
	}

	private static String str(UUID id) {
		return id != null ? id.toString() : null;
	}
}
