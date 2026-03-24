package com.weeklycommit.reconcile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.reconcile.dto.AddCommitData;
import com.weeklycommit.reconcile.dto.EditCommitChanges;
import com.weeklycommit.reconcile.dto.ManagerException;
import com.weeklycommit.reconcile.dto.ScopeChangeEventResponse;
import com.weeklycommit.reconcile.dto.ScopeChangeTimelineResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles post-lock scope changes on a LOCKED plan. All commit mutations after
 * lock are routed through this service so every change is recorded as an
 * immutable {@link ScopeChangeEvent} with before/after values.
 */
@Service
@Transactional
public class ScopeChangeService {

	/** King-related change threshold that triggers a manager exception. */
	private static final ChessPiece KING = ChessPiece.KING;

	/**
	 * Percent increase in total estimate points that triggers a manager exception.
	 */
	private static final double POINTS_INCREASE_THRESHOLD_PCT = 0.20;

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ScopeChangeEventRepository eventRepo;
	private final LockSnapshotHeaderRepository lockSnapshotRepo;
	private final ObjectMapper objectMapper;

	public ScopeChangeService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository eventRepo, LockSnapshotHeaderRepository lockSnapshotRepo,
			ObjectMapper objectMapper) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.eventRepo = eventRepo;
		this.lockSnapshotRepo = lockSnapshotRepo;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Add post-lock commit
	// -------------------------------------------------------------------------

	/**
	 * Adds a new commit to a LOCKED plan and records a COMMIT_ADDED scope-change
	 * event.
	 *
	 * @return updated change timeline including the new event
	 */
	public ScopeChangeTimelineResponse addPostLockCommit(UUID planId, AddCommitData data, String reason,
			UUID actorUserId) {
		validateReason(reason);
		if (data == null) {
			throw new PlanValidationException("Commit data is required for ADD scope changes");
		}
		WeeklyPlan plan = requireLockedPlan(planId);

		WeeklyCommit commit = new WeeklyCommit();
		commit.setPlanId(planId);
		commit.setOwnerUserId(plan.getOwnerUserId());
		commit.setTitle(data.title());
		commit.setChessPiece(data.chessPiece());
		commit.setDescription(data.description());
		commit.setRcdoNodeId(data.rcdoNodeId());
		commit.setWorkItemId(data.workItemId());
		commit.setEstimatePoints(data.estimatePoints());
		commit.setSuccessCriteria(data.successCriteria());
		commit.setPriorityOrder(nextPriorityOrder(planId));
		WeeklyCommit saved = commitRepo.save(commit);

		ScopeChangeEvent event = newEvent(planId, saved.getId(), ScopeChangeCategory.COMMIT_ADDED, actorUserId, reason);
		event.setPreviousValue(null);
		event.setNewValue(toJson(commitToMap(saved)));
		eventRepo.save(event);

		return getChangeTimeline(planId);
	}

	// -------------------------------------------------------------------------
	// Remove post-lock commit
	// -------------------------------------------------------------------------

	/**
	 * Marks an existing commit on a LOCKED plan as canceled and records a
	 * COMMIT_REMOVED scope-change event.
	 *
	 * @return updated change timeline
	 */
	public ScopeChangeTimelineResponse removePostLockCommit(UUID planId, UUID commitId, String reason,
			UUID actorUserId) {
		validateReason(reason);
		WeeklyCommit commit = requireCommit(commitId);
		validateCommitBelongsToPlan(planId, commit);
		requireLockedPlan(commit.getPlanId());

		commit.setOutcome(CommitOutcome.CANCELED);
		commit.setOutcomeNotes(reason);
		commitRepo.save(commit);

		ScopeChangeEvent event = newEvent(commit.getPlanId(), commitId, ScopeChangeCategory.COMMIT_REMOVED, actorUserId,
				reason);
		event.setPreviousValue(toJson(java.util.Map.of("outcome", "null")));
		event.setNewValue(toJson(java.util.Map.of("outcome", CommitOutcome.CANCELED.name())));
		eventRepo.save(event);

		return getChangeTimeline(commit.getPlanId());
	}

	// -------------------------------------------------------------------------
	// Edit post-lock commit
	// -------------------------------------------------------------------------

	/**
	 * Applies mutable-field changes to a commit on a LOCKED plan. A separate
	 * SCOPE_CHANGE event is created for each changed field.
	 *
	 * @return updated change timeline
	 */
	public ScopeChangeTimelineResponse editPostLockCommit(UUID planId, UUID commitId, EditCommitChanges changes,
			String reason, UUID actorUserId) {
		validateReason(reason);
		if (changes == null) {
			throw new PlanValidationException("Changes payload is required for EDIT scope changes");
		}
		WeeklyCommit commit = requireCommit(commitId);
		validateCommitBelongsToPlan(planId, commit);
		requireLockedPlan(commit.getPlanId());

		if (changes.estimatePoints() != null && !changes.estimatePoints().equals(commit.getEstimatePoints())) {
			recordFieldChange(commit, ScopeChangeCategory.ESTIMATE_CHANGED, actorUserId, reason,
					str(commit.getEstimatePoints()), str(changes.estimatePoints()));
			commit.setEstimatePoints(changes.estimatePoints());
		}

		if (changes.chessPiece() != null && changes.chessPiece() != commit.getChessPiece()) {
			recordFieldChange(commit, ScopeChangeCategory.CHESS_PIECE_CHANGED, actorUserId, reason,
					str(commit.getChessPiece()), str(changes.chessPiece()));
			commit.setChessPiece(changes.chessPiece());
		}

		if (changes.rcdoNodeId() != null && !changes.rcdoNodeId().equals(commit.getRcdoNodeId())) {
			recordFieldChange(commit, ScopeChangeCategory.RCDO_CHANGED, actorUserId, reason,
					str(commit.getRcdoNodeId()), str(changes.rcdoNodeId()));
			commit.setRcdoNodeId(changes.rcdoNodeId());
		}

		if (changes.priorityOrder() != null && changes.priorityOrder() != commit.getPriorityOrder()) {
			recordFieldChange(commit, ScopeChangeCategory.PRIORITY_CHANGED, actorUserId, reason,
					str(commit.getPriorityOrder()), str(changes.priorityOrder()));
			commit.setPriorityOrder(changes.priorityOrder());
		}

		commitRepo.save(commit);

		return getChangeTimeline(commit.getPlanId());
	}

	// -------------------------------------------------------------------------
	// Change timeline
	// -------------------------------------------------------------------------

	/**
	 * Returns the full chronological scope-change timeline for a plan, together
	 * with any manager-exception flags.
	 */
	@Transactional(readOnly = true)
	public ScopeChangeTimelineResponse getChangeTimeline(UUID planId) {
		List<ScopeChangeEvent> events = eventRepo.findByPlanIdOrderByCreatedAtAsc(planId);
		List<ScopeChangeEventResponse> responses = events.stream().map(ScopeChangeEventResponse::from).toList();
		List<ManagerException> exceptions = detectManagerExceptions(planId, events);
		return new ScopeChangeTimelineResponse(responses, exceptions);
	}

	// -------------------------------------------------------------------------
	// Manager exception detection
	// -------------------------------------------------------------------------

	/**
	 * Evaluates the current scope-change events against manager-exception
	 * thresholds. Flags are re-derived from events each call (no persistent state).
	 */
	List<ManagerException> detectManagerExceptions(UUID planId, List<ScopeChangeEvent> events) {
		List<ManagerException> exceptions = new ArrayList<>();

		// Rule 1: King added or changed to/from KING
		boolean kingException = false;
		for (ScopeChangeEvent e : events) {
			if (e.getCategory() == ScopeChangeCategory.COMMIT_ADDED) {
				try {
					JsonNode node = objectMapper.readTree(e.getNewValue());
					if (KING.name().equals(node.path("chessPiece").asText(null))) {
						kingException = true;
					}
				} catch (Exception ignored) {
					// malformed JSON — skip
				}
			} else if (e.getCategory() == ScopeChangeCategory.CHESS_PIECE_CHANGED) {
				String prev = e.getPreviousValue();
				String next = e.getNewValue();
				if (KING.name().equals(prev) || KING.name().equals(next)) {
					kingException = true;
				}
			}
		}
		if (kingException) {
			exceptions.add(new ManagerException(ManagerException.TYPE_KING_CHANGE,
					"A King-level commit was added or changed post-lock"));
		}

		// Rule 2: Total estimate points increased >20% from baseline
		lockSnapshotRepo.findByPlanId(planId).ifPresent(header -> {
			int baselinePoints = parseBaselinePoints(header);
			if (baselinePoints > 0) {
				int currentPoints = commitRepo.findByPlanIdOrderByPriorityOrder(planId).stream()
						.filter(c -> c.getOutcome() != CommitOutcome.CANCELED)
						.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();
				double increase = (double) (currentPoints - baselinePoints) / baselinePoints;
				if (increase > POINTS_INCREASE_THRESHOLD_PCT) {
					exceptions.add(new ManagerException(ManagerException.TYPE_POINTS_INCREASE_20PCT,
							String.format("Total estimate points increased %.0f%% from baseline (%d → %d points)",
									increase * 100, baselinePoints, currentPoints)));
				}
			}
		});

		return exceptions;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan requireLockedPlan(UUID planId) {
		WeeklyPlan plan = planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + planId));
		if (plan.getState() != PlanState.LOCKED) {
			throw new PlanValidationException(
					"Scope changes are only allowed on LOCKED plans; current state: " + plan.getState());
		}
		return plan;
	}

	private WeeklyCommit requireCommit(UUID commitId) {
		return commitRepo.findById(commitId)
				.orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + commitId));
	}

	private void validateCommitBelongsToPlan(UUID planId, WeeklyCommit commit) {
		if (!commit.getPlanId().equals(planId)) {
			throw new PlanValidationException("Commit " + commit.getId() + " does not belong to plan " + planId);
		}
	}

	private static void validateReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new PlanValidationException("A non-empty reason is required for all post-lock scope changes");
		}
	}

	private ScopeChangeEvent newEvent(UUID planId, UUID commitId, ScopeChangeCategory category, UUID actorUserId,
			String reason) {
		ScopeChangeEvent e = new ScopeChangeEvent();
		e.setPlanId(planId);
		e.setCommitId(commitId);
		e.setCategory(category);
		e.setChangedByUserId(
				actorUserId != null ? actorUserId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
		e.setReason(reason);
		return e;
	}

	private void recordFieldChange(WeeklyCommit commit, ScopeChangeCategory category, UUID actorUserId, String reason,
			String previousValue, String newValue) {
		ScopeChangeEvent e = newEvent(commit.getPlanId(), commit.getId(), category, actorUserId, reason);
		e.setPreviousValue(previousValue);
		e.setNewValue(newValue);
		eventRepo.save(e);
	}

	private int parseBaselinePoints(LockSnapshotHeader header) {
		try {
			JsonNode root = objectMapper.readTree(header.getSnapshotPayload());
			JsonNode commits = root.path("commits");
			if (!commits.isArray())
				return 0;
			int total = 0;
			for (JsonNode c : commits) {
				JsonNode ep = c.path("estimatePoints");
				if (!ep.isMissingNode() && !ep.isNull()) {
					total += ep.asInt(0);
				}
			}
			return total;
		} catch (JsonProcessingException e) {
			return 0;
		}
	}

	private int nextPriorityOrder(UUID planId) {
		return (int) (commitRepo.countByPlanId(planId) + 1);
	}

	private java.util.Map<String, Object> commitToMap(WeeklyCommit c) {
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("id", str(c.getId()));
		m.put("title", c.getTitle());
		m.put("chessPiece", c.getChessPiece() != null ? c.getChessPiece().name() : null);
		m.put("estimatePoints", c.getEstimatePoints());
		m.put("rcdoNodeId", str(c.getRcdoNodeId()));
		m.put("priorityOrder", c.getPriorityOrder());
		return m;
	}

	private String toJson(Object o) {
		try {
			return objectMapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("JSON serialisation failed", e);
		}
	}

	private static String str(Object o) {
		return o != null ? o.toString() : null;
	}
}
