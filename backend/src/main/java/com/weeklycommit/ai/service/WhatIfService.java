package com.weeklycommit.ai.service;

import com.weeklycommit.ai.dto.WhatIfRequest;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation.WhatIfAction;
import com.weeklycommit.ai.dto.WhatIfResponse;
import com.weeklycommit.ai.dto.WhatIfResponse.PlanSnapshot;
import com.weeklycommit.ai.dto.WhatIfResponse.RcdoCoverageChange;
import com.weeklycommit.ai.dto.WhatIfResponse.RiskDelta;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure-computation What-If simulation service (no LLM calls in this step).
 *
 * <p>
 * Applies hypothetical commit mutations in-memory against a plan's current
 * state and returns a before/after analysis: capacity delta, per-RCDO-node
 * coverage changes, and risk signal diff. No data is persisted.
 *
 * <p>
 * Risk thresholds are duplicated from {@code RiskDetectionService} as
 * package-private constants so that the simulation can run without calling the
 * persistence-backed detection service.
 */
@Service
@Transactional(readOnly = true)
public class WhatIfService {

	/** Blocked-for threshold in hours for the BLOCKED_CRITICAL signal. */
	static final long BLOCKED_CRITICAL_HOURS = 48;

	/** Carry-forward streak threshold for the REPEATED_CARRY_FORWARD signal. */
	static final int CARRY_FORWARD_STREAK_THRESHOLD = 2;

	/** Scope-change count threshold for SCOPE_VOLATILITY. */
	static final int SCOPE_VOLATILITY_THRESHOLD = 3;

	/** Minimum fill rate (fraction of capacity) to avoid UNDERCOMMIT. */
	static final double UNDERCOMMIT_THRESHOLD = 0.60;

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final WorkItemRepository workItemRepo;
	private final WorkItemStatusHistoryRepository statusHistoryRepo;

	public WhatIfService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository scopeChangeRepo, WorkItemRepository workItemRepo,
			WorkItemStatusHistoryRepository statusHistoryRepo) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.workItemRepo = workItemRepo;
		this.statusHistoryRepo = statusHistoryRepo;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Simulates the impact of hypothetical mutations on a plan.
	 *
	 * @param request
	 *            the what-if request with mutations to apply
	 * @return structured before/after analysis, with narrative=null (added in step
	 *         2)
	 * @throws ResourceNotFoundException
	 *             if the plan does not exist
	 */
	public WhatIfResponse simulate(WhatIfRequest request) {
		WeeklyPlan plan = planRepo.findById(request.planId())
				.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.planId()));

		List<WeeklyCommit> originalCommits = commitRepo.findByPlanIdOrderByPriorityOrder(request.planId());

		// Scope change count is the same for both snapshots (mutations don't add
		// events)
		long scopeChangeCount = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(request.planId()).size();

		PlanSnapshot currentState = buildSnapshot(plan, originalCommits, scopeChangeCount);

		List<WeeklyCommit> mutatedCommits = applyMutations(originalCommits, request.hypotheticalChanges(), plan);

		PlanSnapshot projectedState = buildSnapshot(plan, mutatedCommits, scopeChangeCount);

		int capacityDelta = projectedState.totalPoints() - currentState.totalPoints();

		List<RcdoCoverageChange> coverageChanges = computeCoverageChanges(currentState.rcdoCoverage(),
				projectedState.rcdoCoverage());

		RiskDelta riskDelta = computeRiskDelta(currentState.riskSignals(), projectedState.riskSignals());

		return new WhatIfResponse(true, currentState, projectedState, capacityDelta, coverageChanges, riskDelta, null,
				null);
	}

	// -------------------------------------------------------------------------
	// Snapshot building
	// -------------------------------------------------------------------------

	private PlanSnapshot buildSnapshot(WeeklyPlan plan, List<WeeklyCommit> commits, long scopeChangeCount) {
		int totalPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
				.sum();
		int budget = plan.getCapacityBudgetPoints();
		List<String> signals = detectSignals(commits, scopeChangeCount, totalPoints, budget);
		Map<UUID, Integer> rcdoCoverage = computeRcdoCoverage(commits);
		return new PlanSnapshot(totalPoints, budget, signals, rcdoCoverage);
	}

	/**
	 * Replicates the 5 risk rules from {@code RiskDetectionService} inline, without
	 * persisting any signals.
	 */
	private List<String> detectSignals(List<WeeklyCommit> commits, long scopeChangeCount, int totalPoints, int budget) {
		List<String> signals = new ArrayList<>();

		// 1. OVERCOMMIT
		if (totalPoints > budget) {
			signals.add("OVERCOMMIT");
		}

		// 2. UNDERCOMMIT
		if (budget > 0 && (double) totalPoints / budget < UNDERCOMMIT_THRESHOLD) {
			signals.add("UNDERCOMMIT");
		}

		// 3. REPEATED_CARRY_FORWARD
		for (WeeklyCommit commit : commits) {
			if (commit.getCarryForwardStreak() >= CARRY_FORWARD_STREAK_THRESHOLD) {
				signals.add("REPEATED_CARRY_FORWARD");
				break;
			}
		}

		// 4. BLOCKED_CRITICAL
		Instant now = Instant.now();
		outer : for (WeeklyCommit commit : commits) {
			if (commit.getChessPiece() != ChessPiece.KING && commit.getChessPiece() != ChessPiece.QUEEN) {
				continue;
			}
			if (commit.getWorkItemId() == null) {
				continue;
			}
			WorkItem ticket = workItemRepo.findById(commit.getWorkItemId()).orElse(null);
			if (ticket == null || ticket.getStatus() != TicketStatus.BLOCKED) {
				continue;
			}
			Instant blockedSince = findBlockedSince(ticket.getId());
			if (blockedSince != null && Duration.between(blockedSince, now).toHours() >= BLOCKED_CRITICAL_HOURS) {
				signals.add("BLOCKED_CRITICAL");
				break outer;
			}
		}

		// 5. SCOPE_VOLATILITY
		if (scopeChangeCount > SCOPE_VOLATILITY_THRESHOLD) {
			signals.add("SCOPE_VOLATILITY");
		}

		return signals;
	}

	private Map<UUID, Integer> computeRcdoCoverage(List<WeeklyCommit> commits) {
		Map<UUID, Integer> coverage = new LinkedHashMap<>();
		for (WeeklyCommit commit : commits) {
			if (commit.getRcdoNodeId() == null) {
				continue;
			}
			int points = commit.getEstimatePoints() != null ? commit.getEstimatePoints() : 0;
			coverage.merge(commit.getRcdoNodeId(), points, Integer::sum);
		}
		return coverage;
	}

	private Instant findBlockedSince(UUID workItemId) {
		List<WorkItemStatusHistory> history = statusHistoryRepo.findByWorkItemIdOrderByCreatedAtAsc(workItemId);
		for (int i = history.size() - 1; i >= 0; i--) {
			if ("BLOCKED".equals(history.get(i).getToStatus())) {
				return history.get(i).getCreatedAt();
			}
		}
		return null;
	}

	// -------------------------------------------------------------------------
	// Mutation application
	// -------------------------------------------------------------------------

	private List<WeeklyCommit> applyMutations(List<WeeklyCommit> originalCommits, List<WhatIfMutation> mutations,
			WeeklyPlan plan) {
		List<WeeklyCommit> commits = new ArrayList<>(originalCommits.stream().map(this::copyCommit).toList());

		if (mutations == null || mutations.isEmpty()) {
			return commits;
		}

		for (WhatIfMutation mutation : mutations) {
			if (mutation.action() == WhatIfAction.ADD_COMMIT) {
				WeeklyCommit synthetic = new WeeklyCommit();
				synthetic.setId(UUID.randomUUID());
				synthetic.setPlanId(plan.getId());
				synthetic.setOwnerUserId(plan.getOwnerUserId());
				synthetic.setTitle(mutation.title() != null ? mutation.title() : "(hypothetical)");
				synthetic.setChessPiece(parseChessPiece(mutation.chessPiece()));
				synthetic.setEstimatePoints(mutation.estimatePoints());
				synthetic.setRcdoNodeId(mutation.rcdoNodeId());
				synthetic.setPriorityOrder(commits.size() + 1);
				commits.add(synthetic);

			} else if (mutation.action() == WhatIfAction.REMOVE_COMMIT) {
				if (mutation.commitId() != null) {
					commits.removeIf(c -> mutation.commitId().equals(c.getId()));
				}

			} else if (mutation.action() == WhatIfAction.MODIFY_COMMIT) {
				if (mutation.commitId() != null) {
					for (WeeklyCommit c : commits) {
						if (mutation.commitId().equals(c.getId())) {
							if (mutation.title() != null) {
								c.setTitle(mutation.title());
							}
							if (mutation.chessPiece() != null) {
								c.setChessPiece(parseChessPiece(mutation.chessPiece()));
							}
							if (mutation.estimatePoints() != null) {
								c.setEstimatePoints(mutation.estimatePoints());
							}
							if (mutation.rcdoNodeId() != null) {
								c.setRcdoNodeId(mutation.rcdoNodeId());
							}
							break;
						}
					}
				}
			}
		}

		return commits;
	}

	private WeeklyCommit copyCommit(WeeklyCommit original) {
		WeeklyCommit copy = new WeeklyCommit();
		copy.setId(original.getId());
		copy.setPlanId(original.getPlanId());
		copy.setOwnerUserId(original.getOwnerUserId());
		copy.setTitle(original.getTitle());
		copy.setDescription(original.getDescription());
		copy.setChessPiece(original.getChessPiece());
		copy.setPriorityOrder(original.getPriorityOrder());
		copy.setRcdoNodeId(original.getRcdoNodeId());
		copy.setWorkItemId(original.getWorkItemId());
		copy.setEstimatePoints(original.getEstimatePoints());
		copy.setSuccessCriteria(original.getSuccessCriteria());
		copy.setOutcome(original.getOutcome());
		copy.setCarryForwardStreak(original.getCarryForwardStreak());
		copy.setCarryForwardSourceId(original.getCarryForwardSourceId());
		return copy;
	}

	private ChessPiece parseChessPiece(String chessPiece) {
		if (chessPiece == null || chessPiece.isBlank()) {
			return null;
		}
		try {
			return ChessPiece.valueOf(chessPiece.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// -------------------------------------------------------------------------
	// Delta computation
	// -------------------------------------------------------------------------

	private List<RcdoCoverageChange> computeCoverageChanges(Map<UUID, Integer> before, Map<UUID, Integer> after) {
		List<RcdoCoverageChange> changes = new ArrayList<>();
		Set<UUID> allNodes = new HashSet<>();
		allNodes.addAll(before.keySet());
		allNodes.addAll(after.keySet());

		for (UUID nodeId : allNodes) {
			int beforePts = before.getOrDefault(nodeId, 0);
			int afterPts = after.getOrDefault(nodeId, 0);
			if (beforePts != afterPts) {
				changes.add(new RcdoCoverageChange(nodeId, null, beforePts, afterPts));
			}
		}
		return changes;
	}

	private RiskDelta computeRiskDelta(List<String> current, List<String> projected) {
		Set<String> currentSet = new HashSet<>(current);
		Set<String> projectedSet = new HashSet<>(projected);

		List<String> newRisks = projected.stream().filter(s -> !currentSet.contains(s)).toList();
		List<String> resolvedRisks = current.stream().filter(s -> !projectedSet.contains(s)).toList();

		return new RiskDelta(newRisks, resolvedRisks);
	}
}
