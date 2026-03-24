package com.weeklycommit.reconcile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.LockSnapshotCommit;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ReconcileSnapshotCommit;
import com.weeklycommit.domain.entity.ReconcileSnapshotHeader;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ReconcileSnapshotCommitRepository;
import com.weeklycommit.domain.repository.ReconcileSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.reconcile.dto.ReconcileCommitView;
import com.weeklycommit.reconcile.dto.ReconciliationViewResponse;
import com.weeklycommit.reconcile.dto.ScopeChangeEventResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the RECONCILING phase of a weekly plan's lifecycle.
 *
 * <p>
 * Responsibilities:
 * <ol>
 * <li>Opening reconciliation — idempotent LOCKED → RECONCILING transition with
 * auto-achieve for linked DONE tickets.</li>
 * <li>Providing the reconciliation view — baseline vs current commit data.</li>
 * <li>Setting individual commit outcomes.</li>
 * <li>Submitting the final reconciliation — validates completeness, writes
 * immutable snapshot, and transitions to RECONCILED.</li>
 * </ol>
 */
@Service
@Transactional
public class ReconciliationService {

	/** Outcomes that require accompanying notes. */
	private static final Set<CommitOutcome> NOTES_REQUIRED_OUTCOMES = Set.of(CommitOutcome.PARTIALLY_ACHIEVED,
			CommitOutcome.NOT_ACHIEVED, CommitOutcome.CANCELED);

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final WorkItemRepository workItemRepo;
	private final ScopeChangeEventRepository eventRepo;
	private final LockSnapshotHeaderRepository lockHeaderRepo;
	private final LockSnapshotCommitRepository lockCommitRepo;
	private final ReconcileSnapshotHeaderRepository reconcileHeaderRepo;
	private final ReconcileSnapshotCommitRepository reconcileCommitRepo;
	private final ObjectMapper objectMapper;

	public ReconciliationService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			WorkItemRepository workItemRepo, ScopeChangeEventRepository eventRepo,
			LockSnapshotHeaderRepository lockHeaderRepo, LockSnapshotCommitRepository lockCommitRepo,
			ReconcileSnapshotHeaderRepository reconcileHeaderRepo,
			ReconcileSnapshotCommitRepository reconcileCommitRepo, ObjectMapper objectMapper) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.workItemRepo = workItemRepo;
		this.eventRepo = eventRepo;
		this.lockHeaderRepo = lockHeaderRepo;
		this.lockCommitRepo = lockCommitRepo;
		this.reconcileHeaderRepo = reconcileHeaderRepo;
		this.reconcileCommitRepo = reconcileCommitRepo;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Open reconciliation (LOCKED → RECONCILING)
	// -------------------------------------------------------------------------

	/**
	 * Transitions a LOCKED plan to RECONCILING. Idempotent: if already RECONCILING
	 * the call is a no-op.
	 *
	 * <p>
	 * Side-effect: any commit whose linked work-item has status DONE is
	 * automatically given an ACHIEVED outcome.
	 *
	 * @throws PlanValidationException
	 *             if the plan is not LOCKED or RECONCILING.
	 */
	public void openReconciliation(UUID planId) {
		WeeklyPlan plan = requirePlan(planId);

		if (plan.getState() == PlanState.RECONCILING) {
			return; // idempotent
		}

		if (plan.getState() != PlanState.LOCKED) {
			throw new PlanValidationException(
					"Reconciliation can only be opened for LOCKED plans; current state: " + plan.getState());
		}

		plan.setState(PlanState.RECONCILING);
		planRepo.save(plan);

		autoAchieveDoneTickets(plan.getId());
	}

	// -------------------------------------------------------------------------
	// Reconciliation view
	// -------------------------------------------------------------------------

	/**
	 * Builds the full reconciliation dashboard for a RECONCILING (or RECONCILED)
	 * plan.
	 */
	@Transactional(readOnly = true)
	public ReconciliationViewResponse getReconciliationView(UUID planId) {
		WeeklyPlan plan = requirePlan(planId);
		if (plan.getState() != PlanState.RECONCILING && plan.getState() != PlanState.RECONCILED) {
			throw new PlanValidationException(
					"Reconciliation view is only available for plans in RECONCILING or RECONCILED state; "
							+ "current state: " + plan.getState());
		}

		// Baseline data
		LockSnapshotHeader lockHeader = lockHeaderRepo.findByPlanId(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Lock snapshot not found for plan: " + planId));
		List<LockSnapshotCommit> lockCommits = lockCommitRepo.findBySnapshotId(lockHeader.getId());
		Map<UUID, Map<String, Object>> baselineByCommitId = buildBaselineMap(lockCommits);
		int baselineTotalPoints = baselineByCommitId.values().stream()
				.mapToInt(m -> m.get("estimatePoints") instanceof Number n ? n.intValue() : 0).sum();

		// Current commits
		List<WeeklyCommit> currentCommits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);

		// Scope-change events, grouped by commit
		List<ScopeChangeEvent> allEvents = eventRepo.findByPlanIdOrderByCreatedAtAsc(planId);
		Map<UUID, List<ScopeChangeEvent>> eventsByCommit = allEvents.stream().filter(e -> e.getCommitId() != null)
				.collect(Collectors.groupingBy(ScopeChangeEvent::getCommitId));

		// Identify commits added or removed post-lock
		Set<UUID> addedPostLock = allEvents.stream()
				.filter(e -> e.getCategory() == ScopeChangeCategory.COMMIT_ADDED && e.getCommitId() != null)
				.map(ScopeChangeEvent::getCommitId).collect(Collectors.toSet());
		Set<UUID> removedPostLock = allEvents.stream()
				.filter(e -> e.getCategory() == ScopeChangeCategory.COMMIT_REMOVED && e.getCommitId() != null)
				.map(ScopeChangeEvent::getCommitId).collect(Collectors.toSet());

		List<ReconcileCommitView> views = new ArrayList<>();
		int currentTotalPoints = 0;
		int outcomesSetCount = 0;

		for (WeeklyCommit commit : currentCommits) {
			int pts = commit.getEstimatePoints() != null ? commit.getEstimatePoints() : 0;
			if (commit.getOutcome() != CommitOutcome.CANCELED) {
				currentTotalPoints += pts;
			}
			if (commit.getOutcome() != null) {
				outcomesSetCount++;
			}

			List<ScopeChangeEventResponse> commitEvents = eventsByCommit.getOrDefault(commit.getId(), List.of())
					.stream().map(ScopeChangeEventResponse::from).toList();

			String ticketStatus = null;
			if (commit.getWorkItemId() != null) {
				ticketStatus = workItemRepo.findById(commit.getWorkItemId()).map(wi -> wi.getStatus().name())
						.orElse(null);
			}

			Map<String, Object> baseline = baselineByCommitId.get(commit.getId());

			views.add(new ReconcileCommitView(commit.getId(), commit.getTitle(), commit.getChessPiece(),
					commit.getEstimatePoints(), commit.getOutcome(), commit.getOutcomeNotes(), baseline, commitEvents,
					ticketStatus, addedPostLock.contains(commit.getId()), removedPostLock.contains(commit.getId())));
		}

		return new ReconciliationViewResponse(PlanResponse.from(plan), views, baselineTotalPoints, currentTotalPoints,
				currentCommits.size(), outcomesSetCount);
	}

	// -------------------------------------------------------------------------
	// Set commit outcome
	// -------------------------------------------------------------------------

	/**
	 * Sets (or updates) the outcome on a commit during RECONCILING.
	 *
	 * @throws PlanValidationException
	 *             if notes are required but absent.
	 */
	public CommitResponse setCommitOutcome(UUID planId, UUID commitId, CommitOutcome outcome, String notes) {
		WeeklyPlan plan = requirePlan(planId);
		if (plan.getState() != PlanState.RECONCILING) {
			throw new PlanValidationException(
					"Outcomes can only be set while the plan is in RECONCILING state; current state: "
							+ plan.getState());
		}

		WeeklyCommit commit = requireCommit(commitId);
		if (!commit.getPlanId().equals(planId)) {
			throw new PlanValidationException("Commit " + commitId + " does not belong to plan " + planId);
		}

		// Notes required for PARTIALLY_ACHIEVED, NOT_ACHIEVED, CANCELED
		if (NOTES_REQUIRED_OUTCOMES.contains(outcome) && (notes == null || notes.isBlank())) {
			throw new PlanValidationException("Outcome notes are required for outcome " + outcome);
		}

		commit.setOutcome(outcome);
		commit.setOutcomeNotes(notes);
		commitRepo.save(commit);

		return CommitResponse.from(commit);
	}

	// -------------------------------------------------------------------------
	// Submit reconciliation
	// -------------------------------------------------------------------------

	/**
	 * Validates that all commits have outcomes, then writes the immutable
	 * reconciliation snapshot and transitions the plan to RECONCILED.
	 *
	 * @throws PlanValidationException
	 *             if any commit still has no outcome.
	 */
	public ReconciliationViewResponse submitReconciliation(UUID planId) {
		WeeklyPlan plan = requirePlan(planId);
		if (plan.getState() != PlanState.RECONCILING) {
			throw new PlanValidationException(
					"Reconciliation can only be submitted from RECONCILING state; current state: " + plan.getState());
		}

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);

		// Validate every commit has an outcome
		List<UUID> missing = commits.stream().filter(c -> c.getOutcome() == null).map(WeeklyCommit::getId).toList();
		if (!missing.isEmpty()) {
			throw new PlanValidationException(
					"All commits must have an outcome before reconciliation can be submitted. "
							+ "Commits without outcomes: " + missing);
		}

		// Compute compliance: plan was already marked compliant/non-compliant at lock
		// time; we keep that flag and add an additional "all achieved" flag via the
		// snapshot payload.
		boolean allAchieved = commits.stream()
				.allMatch(c -> c.getOutcome() == CommitOutcome.ACHIEVED || c.getOutcome() == CommitOutcome.CANCELED);

		// Write immutable reconcile snapshot
		ReconcileSnapshotHeader header = new ReconcileSnapshotHeader();
		header.setPlanId(planId);
		header.setSnapshotPayload(buildReconcilePayload(plan, commits, allAchieved));
		ReconcileSnapshotHeader savedHeader = reconcileHeaderRepo.save(header);

		for (WeeklyCommit commit : commits) {
			ReconcileSnapshotCommit sc = new ReconcileSnapshotCommit();
			sc.setSnapshotId(savedHeader.getId());
			sc.setCommitId(commit.getId());
			sc.setOutcome(commit.getOutcome());
			sc.setSnapshotData(buildCommitSnapshot(commit));
			reconcileCommitRepo.save(sc);
		}

		// Transition to RECONCILED
		plan.setReconcileSnapshotId(savedHeader.getId());
		plan.setState(PlanState.RECONCILED);
		plan.setCompliant(plan.isCompliant() && allAchieved);
		planRepo.save(plan);

		return getReconciliationView(planId);
	}

	// -------------------------------------------------------------------------
	// Auto-achieve helper (called by openReconciliation)
	// -------------------------------------------------------------------------

	private void autoAchieveDoneTickets(UUID planId) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);
		for (WeeklyCommit commit : commits) {
			if (commit.getWorkItemId() != null && commit.getOutcome() == null) {
				Optional<WorkItem> wiOpt = workItemRepo.findById(commit.getWorkItemId());
				if (wiOpt.isPresent() && wiOpt.get().getStatus() == TicketStatus.DONE) {
					commit.setOutcome(CommitOutcome.ACHIEVED);
					commitRepo.save(commit);
				}
			}
		}
	}

	// -------------------------------------------------------------------------
	// Snapshot builders
	// -------------------------------------------------------------------------

	private String buildReconcilePayload(WeeklyPlan plan, List<WeeklyCommit> commits, boolean allAchieved) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("planId", plan.getId().toString());
		m.put("ownerUserId", plan.getOwnerUserId().toString());
		m.put("teamId", plan.getTeamId().toString());
		m.put("weekStartDate", plan.getWeekStartDate().toString());
		m.put("reconciledAt", Instant.now().toString());
		m.put("allAchieved", allAchieved);
		m.put("commits", commits.stream().map(this::commitToMapForSnapshot).toList());
		return toJson(m);
	}

	private String buildCommitSnapshot(WeeklyCommit commit) {
		return toJson(commitToMapForSnapshot(commit));
	}

	private Map<String, Object> commitToMapForSnapshot(WeeklyCommit commit) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", commit.getId().toString());
		m.put("title", commit.getTitle());
		m.put("chessPiece", commit.getChessPiece() != null ? commit.getChessPiece().name() : null);
		m.put("priorityOrder", commit.getPriorityOrder());
		m.put("estimatePoints", commit.getEstimatePoints());
		m.put("successCriteria", commit.getSuccessCriteria());
		m.put("outcome", commit.getOutcome() != null ? commit.getOutcome().name() : null);
		m.put("outcomeNotes", commit.getOutcomeNotes());
		m.put("rcdoNodeId", commit.getRcdoNodeId() != null ? commit.getRcdoNodeId().toString() : null);
		m.put("workItemId", commit.getWorkItemId() != null ? commit.getWorkItemId().toString() : null);
		return m;
	}

	// -------------------------------------------------------------------------
	// Baseline map builder
	// -------------------------------------------------------------------------

	private Map<UUID, Map<String, Object>> buildBaselineMap(List<LockSnapshotCommit> lockCommits) {
		Map<UUID, Map<String, Object>> result = new HashMap<>();
		for (LockSnapshotCommit lsc : lockCommits) {
			try {
				JsonNode node = objectMapper.readTree(lsc.getSnapshotData());
				Map<String, Object> m = new LinkedHashMap<>();
				node.fields().forEachRemaining(entry -> m.put(entry.getKey(), nodeToValue(entry.getValue())));
				result.put(lsc.getCommitId(), m);
			} catch (JsonProcessingException e) {
				// return empty map for this commit if JSON is malformed
				result.put(lsc.getCommitId(), Map.of());
			}
		}
		return result;
	}

	private Object nodeToValue(JsonNode node) {
		if (node.isNull())
			return null;
		if (node.isInt())
			return node.intValue();
		if (node.isLong())
			return node.longValue();
		if (node.isBoolean())
			return node.booleanValue();
		if (node.isTextual())
			return node.textValue();
		return node.toString();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan requirePlan(UUID planId) {
		return planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + planId));
	}

	private WeeklyCommit requireCommit(UUID commitId) {
		return commitRepo.findById(commitId)
				.orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + commitId));
	}

	private String toJson(Object o) {
		try {
			return objectMapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("JSON serialisation failed", e);
		}
	}
}
