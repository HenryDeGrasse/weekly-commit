package com.weeklycommit.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.WhatIfRequest;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation.WhatIfAction;
import com.weeklycommit.ai.dto.WhatIfResponse;
import com.weeklycommit.ai.dto.WhatIfResponse.PlanSnapshot;
import com.weeklycommit.ai.dto.WhatIfResponse.RcdoCoverageChange;
import com.weeklycommit.ai.dto.WhatIfResponse.RiskDelta;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What-If simulation service: pure computation + optional LLM narration.
 *
 * <p>
 * Applies hypothetical commit mutations in-memory against a plan's current
 * state and returns a before/after analysis: capacity delta, per-RCDO-node
 * coverage changes, and risk signal diff. No data is persisted.
 *
 * <p>
 * After computing the structured impact the service optionally calls the LLM to
 * produce a 2-3 sentence narrative and a short recommendation. When AI is
 * unavailable the full structured response is still returned with
 * {@code narrative=null} and {@code recommendation=null}.
 *
 * <p>
 * Risk thresholds are duplicated from {@code RiskDetectionService} as
 * package-private constants so that the simulation can run without calling the
 * persistence-backed detection service.
 */
@Service
@Transactional(readOnly = true)
public class WhatIfService {

	private static final Logger log = LoggerFactory.getLogger(WhatIfService.class);

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
	private final AiProviderRegistry aiProviderRegistry;
	private final ObjectMapper objectMapper;

	public WhatIfService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository scopeChangeRepo, WorkItemRepository workItemRepo,
			WorkItemStatusHistoryRepository statusHistoryRepo, AiProviderRegistry aiProviderRegistry,
			ObjectMapper objectMapper) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.workItemRepo = workItemRepo;
		this.statusHistoryRepo = statusHistoryRepo;
		this.aiProviderRegistry = aiProviderRegistry;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Simulates the impact of hypothetical mutations on a plan and optionally adds
	 * an LLM narrative.
	 *
	 * @param request
	 *            the what-if request with mutations to apply
	 * @return structured before/after analysis; narrative/recommendation are null
	 *         when AI is unavailable
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

		// Optional LLM narration — never blocks the structured response
		String[] narration = fetchNarration(request, currentState, projectedState, capacityDelta, riskDelta,
				coverageChanges);

		return new WhatIfResponse(true, currentState, projectedState, capacityDelta, coverageChanges, riskDelta,
				narration[0], narration[1]);
	}

	// -------------------------------------------------------------------------
	// LLM narration
	// -------------------------------------------------------------------------

	/**
	 * Calls the LLM to produce a narrative and recommendation for the impact.
	 * Returns a two-element array: {@code [narrative, recommendation]}, both
	 * possibly null on AI unavailability or parse failure.
	 */
	private String[] fetchNarration(WhatIfRequest request, PlanSnapshot currentState, PlanSnapshot projectedState,
			int capacityDelta, RiskDelta riskDelta, List<RcdoCoverageChange> coverageChanges) {
		if (!aiProviderRegistry.isAiEnabled()) {
			return new String[]{null, null};
		}
		try {
			Map<String, Object> additionalContext = buildNarrationContext(currentState, projectedState, capacityDelta,
					riskDelta, coverageChanges);
			AiContext context = new AiContext(AiContext.TYPE_WHAT_IF, request.userId(), request.planId(), null,
					Map.of(), Map.of(), List.of(), List.of(), additionalContext);
			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context);
			if (!result.available() || result.payload() == null) {
				return new String[]{null, null};
			}
			return parseNarration(result.payload());
		} catch (Exception e) {
			log.debug("LLM narration failed for what-if plan {}: {}", request.planId(), e.getMessage());
			return new String[]{null, null};
		}
	}

	private String[] parseNarration(String payload) {
		try {
			JsonNode node = objectMapper.readTree(payload);
			String narrative = textOrNull(node, "narrative");
			String recommendation = textOrNull(node, "recommendation");
			return new String[]{narrative, recommendation};
		} catch (Exception e) {
			log.debug("Failed to parse what-if narration payload: {}", e.getMessage());
			return new String[]{null, null};
		}
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode child = node.get(field);
		if (child == null || child.isNull()) {
			return null;
		}
		String text = child.asText();
		return (text.isBlank() || "null".equalsIgnoreCase(text)) ? null : text;
	}

	/**
	 * Serialises the computed impact data into the additionalContext map that the
	 * LLM prompt template reads.
	 */
	private Map<String, Object> buildNarrationContext(PlanSnapshot currentState, PlanSnapshot projectedState,
			int capacityDelta, RiskDelta riskDelta, List<RcdoCoverageChange> coverageChanges) {
		Map<String, Object> ctx = new LinkedHashMap<>();
		ctx.put("currentTotalPoints", currentState.totalPoints());
		ctx.put("projectedTotalPoints", projectedState.totalPoints());
		ctx.put("capacityBudget", currentState.capacityBudget());
		ctx.put("capacityDelta", capacityDelta);
		ctx.put("currentRiskSignals", currentState.riskSignals());
		ctx.put("projectedRiskSignals", projectedState.riskSignals());
		ctx.put("newRisks", riskDelta.newRisks());
		ctx.put("resolvedRisks", riskDelta.resolvedRisks());

		List<Map<String, Object>> coverageList = new ArrayList<>();
		for (RcdoCoverageChange change : coverageChanges) {
			Map<String, Object> cc = new LinkedHashMap<>();
			cc.put("rcdoNodeId", change.rcdoNodeId() != null ? change.rcdoNodeId().toString() : null);
			cc.put("beforePoints", change.beforePoints());
			cc.put("afterPoints", change.afterPoints());
			coverageList.add(cc);
		}
		ctx.put("rcdoCoverageChanges", coverageList);
		return ctx;
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
