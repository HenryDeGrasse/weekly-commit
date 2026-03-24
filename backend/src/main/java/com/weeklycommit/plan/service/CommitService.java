package com.weeklycommit.plan.service;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.dto.CreateCommitRequest;
import com.weeklycommit.plan.dto.UpdateCommitRequest;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.rcdo.service.RcdoLinkageValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommitService {

	/** Valid Fibonacci-scale estimate values. */
	private static final Set<Integer> VALID_ESTIMATE_POINTS = Set.of(1, 2, 3, 5, 8);

	private static final int MAX_KING_PER_WEEK = 1;
	private static final int MAX_QUEEN_PER_WEEK = 2;

	private final WeeklyCommitRepository commitRepo;
	private final WeeklyPlanRepository planRepo;
	private final WorkItemRepository workItemRepo;
	private final RcdoLinkageValidator rcdoLinkageValidator;

	public CommitService(WeeklyCommitRepository commitRepo, WeeklyPlanRepository planRepo,
			WorkItemRepository workItemRepo, RcdoLinkageValidator rcdoLinkageValidator) {
		this.commitRepo = commitRepo;
		this.planRepo = planRepo;
		this.workItemRepo = workItemRepo;
		this.rcdoLinkageValidator = rcdoLinkageValidator;
	}

	// -------------------------------------------------------------------------
	// Create
	// -------------------------------------------------------------------------

	public WeeklyCommit createCommit(UUID planId, CreateCommitRequest req, UUID actorUserId) {
		WeeklyPlan plan = findPlan(planId);
		validateDraftState(plan);

		// Validate chess piece limits
		validateChessPieceLimits(planId, req.chessPiece(), null);

		// Validate estimate points if provided
		validateEstimatePoints(req.estimatePoints());

		// Success criteria required for King / Queen
		validateSuccessCriteria(req.chessPiece(), req.successCriteria());

		// Resolve RCDO: use explicit value, else default from ticket
		UUID rcdoNodeId = resolveRcdoNodeId(req.rcdoNodeId(), req.workItemId());

		// Validate RCDO linkage if provided
		if (rcdoNodeId != null) {
			rcdoLinkageValidator.validateCommitLinkage(rcdoNodeId);
		}

		WeeklyCommit commit = new WeeklyCommit();
		commit.setPlanId(planId);
		commit.setOwnerUserId(plan.getOwnerUserId());
		commit.setTitle(req.title());
		commit.setChessPiece(req.chessPiece());
		commit.setDescription(req.description());
		commit.setRcdoNodeId(rcdoNodeId);
		commit.setWorkItemId(req.workItemId());
		commit.setEstimatePoints(req.estimatePoints());
		commit.setSuccessCriteria(req.successCriteria());
		commit.setPriorityOrder(nextPriorityOrder(planId));

		return commitRepo.save(commit);
	}

	// -------------------------------------------------------------------------
	// Update
	// -------------------------------------------------------------------------

	public WeeklyCommit updateCommit(UUID planId, UUID commitId, UpdateCommitRequest req, UUID actorUserId) {
		WeeklyCommit commit = findCommit(commitId);
		validateCommitBelongsToPlan(planId, commit);
		WeeklyPlan plan = findPlan(commit.getPlanId());
		validateDraftState(plan);

		// Effective values after applying patch
		ChessPiece effectivePiece = req.chessPiece() != null ? req.chessPiece() : commit.getChessPiece();
		String effectiveCriteria = req.successCriteria() != null ? req.successCriteria() : commit.getSuccessCriteria();
		Integer effectivePoints = req.estimatePoints() != null ? req.estimatePoints() : commit.getEstimatePoints();
		UUID effectiveRcdo = req.rcdoNodeId() != null ? req.rcdoNodeId() : commit.getRcdoNodeId();

		// If chess piece is changing, re-validate limits excluding this commit
		if (req.chessPiece() != null && req.chessPiece() != commit.getChessPiece()) {
			validateChessPieceLimits(commit.getPlanId(), req.chessPiece(), commitId);
		}

		validateEstimatePoints(effectivePoints);
		validateSuccessCriteria(effectivePiece, effectiveCriteria);

		if (effectiveRcdo != null && !effectiveRcdo.equals(commit.getRcdoNodeId())) {
			rcdoLinkageValidator.validateCommitLinkage(effectiveRcdo);
		}

		// Apply non-null fields
		if (req.title() != null && !req.title().isBlank()) {
			commit.setTitle(req.title());
		}
		if (req.chessPiece() != null) {
			commit.setChessPiece(req.chessPiece());
		}
		if (req.description() != null) {
			commit.setDescription(req.description());
		}
		if (req.rcdoNodeId() != null) {
			commit.setRcdoNodeId(req.rcdoNodeId());
		}
		if (req.workItemId() != null) {
			commit.setWorkItemId(req.workItemId());
		}
		if (req.estimatePoints() != null) {
			commit.setEstimatePoints(req.estimatePoints());
		}
		if (req.successCriteria() != null) {
			commit.setSuccessCriteria(req.successCriteria());
		}

		return commitRepo.save(commit);
	}

	// -------------------------------------------------------------------------
	// Delete
	// -------------------------------------------------------------------------

	public void deleteCommit(UUID planId, UUID commitId, UUID actorUserId) {
		WeeklyCommit commit = findCommit(commitId);
		validateCommitBelongsToPlan(planId, commit);
		WeeklyPlan plan = findPlan(commit.getPlanId());
		validateDraftState(plan);

		commitRepo.delete(commit);

		// Re-number remaining commits sequentially
		List<WeeklyCommit> remaining = commitRepo.findByPlanIdOrderByPriorityOrder(planId);
		for (int i = 0; i < remaining.size(); i++) {
			remaining.get(i).setPriorityOrder(i + 1);
			commitRepo.save(remaining.get(i));
		}
	}

	// -------------------------------------------------------------------------
	// Reorder
	// -------------------------------------------------------------------------

	public List<WeeklyCommit> reorderCommits(UUID planId, List<UUID> orderedCommitIds, UUID actorUserId) {
		WeeklyPlan plan = findPlan(planId);
		validateDraftState(plan);

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);
		Set<UUID> planIds = new HashSet<>();
		for (WeeklyCommit c : commits) {
			planIds.add(c.getId());
		}

		if (!planIds.equals(new HashSet<>(orderedCommitIds))) {
			throw new PlanValidationException("Reorder list must contain exactly all commit IDs for this plan");
		}

		for (int i = 0; i < orderedCommitIds.size(); i++) {
			final int newOrder = i + 1;
			UUID cId = orderedCommitIds.get(i);
			commits.stream().filter(c -> c.getId().equals(cId)).findFirst().ifPresent(c -> {
				c.setPriorityOrder(newOrder);
				commitRepo.save(c);
			});
		}

		return commitRepo.findByPlanIdOrderByPriorityOrder(planId);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	WeeklyCommit findCommit(UUID commitId) {
		return commitRepo.findById(commitId)
				.orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + commitId));
	}

	private WeeklyPlan findPlan(UUID planId) {
		return planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + planId));
	}

	private void validateDraftState(WeeklyPlan plan) {
		if (plan.getState() != PlanState.DRAFT) {
			throw new PlanValidationException(
					"Plan " + plan.getId() + " is not in DRAFT state (current: " + plan.getState() + ")");
		}
	}

	private void validateCommitBelongsToPlan(UUID planId, WeeklyCommit commit) {
		if (!commit.getPlanId().equals(planId)) {
			throw new PlanValidationException("Commit " + commit.getId() + " does not belong to plan " + planId);
		}
	}

	void validateChessPieceLimits(UUID planId, ChessPiece piece, UUID excludeCommitId) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);
		long count = commits.stream().filter(c -> excludeCommitId == null || !c.getId().equals(excludeCommitId))
				.filter(c -> c.getChessPiece() == piece).count();

		if (piece == ChessPiece.KING && count >= MAX_KING_PER_WEEK) {
			throw new PlanValidationException("Maximum " + MAX_KING_PER_WEEK + " King commit(s) per week exceeded");
		}
		if (piece == ChessPiece.QUEEN && count >= MAX_QUEEN_PER_WEEK) {
			throw new PlanValidationException("Maximum " + MAX_QUEEN_PER_WEEK + " Queen commit(s) per week exceeded");
		}
	}

	void validateEstimatePoints(Integer points) {
		if (points != null && !VALID_ESTIMATE_POINTS.contains(points)) {
			throw new PlanValidationException("Estimate points must be one of {1, 2, 3, 5, 8}, got: " + points);
		}
	}

	void validateSuccessCriteria(ChessPiece piece, String successCriteria) {
		if ((piece == ChessPiece.KING || piece == ChessPiece.QUEEN)
				&& (successCriteria == null || successCriteria.isBlank())) {
			throw new PlanValidationException("Success criteria is required for " + piece + " commits");
		}
	}

	private int nextPriorityOrder(UUID planId) {
		return (int) (commitRepo.countByPlanId(planId) + 1);
	}

	private UUID resolveRcdoNodeId(UUID requestedRcdo, UUID workItemId) {
		if (requestedRcdo != null) {
			return requestedRcdo;
		}
		if (workItemId != null) {
			return workItemRepo.findById(workItemId).map(WorkItem::getRcdoNodeId).orElse(null);
		}
		return null;
	}
}
