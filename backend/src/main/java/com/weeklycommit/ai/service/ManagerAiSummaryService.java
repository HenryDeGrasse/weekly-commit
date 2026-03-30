package com.weeklycommit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.ManagerAiSummaryResponse;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.exception.AccessDeniedException;
import com.weeklycommit.team.service.AuthorizationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager team AI summary service (PRD §17 capability 6).
 *
 * <p>
 * Generates a weekly summary citing:
 * <ul>
 * <li>Top RCDO branches by planned commitment.</li>
 * <li>Unresolved manager-review exceptions (cited by id).</li>
 * <li>Carry-forward patterns across team members.</li>
 * <li>Critical blocked items (King/Queen with BLOCKED tickets).</li>
 * </ul>
 *
 * <p>
 * Only visible to managers — access denied for non-managers.
 */
@Service
@Transactional
public class ManagerAiSummaryService {

	private static final Logger log = LoggerFactory.getLogger(ManagerAiSummaryService.class);

	private final AiProviderRegistry registry;
	private final AiSuggestionService suggestionService;
	private final TeamRepository teamRepo;
	private final TeamMembershipRepository membershipRepo;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final WorkItemRepository workItemRepo;
	private final RcdoNodeRepository rcdoNodeRepo;
	private final ManagerReviewExceptionRepository exceptionRepo;
	private final AuthorizationService authService;
	private final ObjectMapper objectMapper;

	public ManagerAiSummaryService(AiProviderRegistry registry, AiSuggestionService suggestionService,
			TeamRepository teamRepo, TeamMembershipRepository membershipRepo, WeeklyPlanRepository planRepo,
			WeeklyCommitRepository commitRepo, WorkItemRepository workItemRepo, RcdoNodeRepository rcdoNodeRepo,
			ManagerReviewExceptionRepository exceptionRepo, AuthorizationService authService,
			ObjectMapper objectMapper) {
		this.registry = registry;
		this.suggestionService = suggestionService;
		this.teamRepo = teamRepo;
		this.membershipRepo = membershipRepo;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.workItemRepo = workItemRepo;
		this.rcdoNodeRepo = rcdoNodeRepo;
		this.exceptionRepo = exceptionRepo;
		this.authService = authService;
		this.objectMapper = objectMapper;
	}

	/**
	 * Returns the AI summary for a team week. Only accessible to managers.
	 *
	 * @param teamId
	 *            the team
	 * @param weekStart
	 *            the week start date
	 * @param callerId
	 *            the requesting user
	 * @return summary response
	 * @throws AccessDeniedException
	 *             if the caller is not a manager/admin
	 * @throws ResourceNotFoundException
	 *             if the team is not found
	 */
	public ManagerAiSummaryResponse getSummary(UUID teamId, LocalDate weekStart, UUID callerId) {
		teamRepo.findById(teamId).orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

		// Privacy: only managers may access AI summaries
		UserRole callerRole = authService.getCallerRole(callerId);
		if (callerRole != UserRole.ADMIN && callerRole != UserRole.MANAGER) {
			throw new AccessDeniedException("Only managers may view AI team summaries");
		}

		if (!registry.isAiEnabled()) {
			return ManagerAiSummaryResponse.unavailable(teamId, weekStart);
		}

		// Gather team data
		List<TeamMembership> memberships = membershipRepo.findByTeamId(teamId);
		List<WeeklyPlan> plans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart);

		// Collect all commits for this team-week
		List<WeeklyCommit> allCommits = new ArrayList<>();
		for (WeeklyPlan plan : plans) {
			allCommits.addAll(commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()));
		}

		// Derive cited objects: unresolved exceptions
		List<ManagerReviewException> unresolvedExceptions = exceptionRepo
				.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false);
		List<UUID> unresolvedExceptionIds = unresolvedExceptions.stream().map(ManagerReviewException::getId).toList();

		// Carry-forward patterns
		List<String> carryForwardPatterns = deriveCarryForwardPatterns(allCommits);

		// Critical blocked items: King/Queen commits with BLOCKED tickets
		List<UUID> criticalBlockedItemIds = deriveCriticalBlockedItems(allCommits);

		// Top RCDO branches by planned points
		List<String> topRcdoBranches = deriveTopRcdoBranches(allCommits);

		// ── Plan-level aggregates for the prompt's planData field ──────────────────
		long lockedPlanCount = plans.stream().filter(p -> p.getState() != null && !p.getState().name().equals("DRAFT"))
				.count();
		long reconciledPlanCount = plans.stream()
				.filter(p -> p.getState() != null && p.getState().name().equals("RECONCILED")).count();
		int totalPlannedPoints = allCommits.stream()
				.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();
		int totalAchievedPoints = allCommits.stream()
				.filter(c -> c.getOutcome() != null && c.getOutcome().name().equals("ACHIEVED"))
				.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();

		Map<String, Object> planData = new HashMap<>();
		planData.put("teamMemberCount", memberships.size());
		planData.put("lockedPlanCount", lockedPlanCount);
		planData.put("reconciledPlanCount", reconciledPlanCount);
		planData.put("totalPlannedPoints", totalPlannedPoints);
		planData.put("totalAchievedPoints", totalAchievedPoints);
		planData.put("weekStart", weekStart.toString());

		// ── Commits serialised for the prompt's historicalCommits field ────────────
		Map<UUID, UUID> ownerByPlanId = new HashMap<>();
		for (WeeklyPlan plan : plans) {
			ownerByPlanId.put(plan.getId(), plan.getOwnerUserId());
		}
		List<Map<String, Object>> historicalCommits = allCommits.stream().map(c -> {
			Map<String, Object> m = new HashMap<>();
			m.put("ownerUserId", ownerByPlanId.getOrDefault(c.getPlanId(), c.getPlanId()).toString());
			m.put("title", c.getTitle());
			m.put("chessPiece", c.getChessPiece() != null ? c.getChessPiece().name() : null);
			m.put("estimatePoints", c.getEstimatePoints());
			m.put("outcome", c.getOutcome() != null ? c.getOutcome().name() : null);
			m.put("carryForwardStreak", c.getCarryForwardStreak());
			return m;
		}).toList();

		// ── Additional context — manager dashboard aggregates ──────────────────────
		long missedLocks = (memberships.size() - plans.size())
				+ plans.stream().filter(p -> p.getState() != null && p.getState().name().equals("DRAFT")).count();
		long missedReconciles = plans.stream().filter(p -> p.getState() != null
				&& (p.getState().name().equals("LOCKED") || p.getState().name().equals("RECONCILING"))).count();

		Map<String, Object> additionalCtx = new HashMap<>();
		additionalCtx.put("exceptionCount", unresolvedExceptionIds.size());
		additionalCtx.put("missedLocks", missedLocks);
		additionalCtx.put("missedReconciles", missedReconciles);
		additionalCtx.put("topRcdoBranches", topRcdoBranches);
		additionalCtx.put("carryForwardPatterns", carryForwardPatterns);
		additionalCtx.put("criticalBlockedItems", criticalBlockedItemIds.size());

		AiContext context = new AiContext(AiContext.TYPE_TEAM_SUMMARY, callerId, null, null, Map.of(), planData,
				historicalCommits, List.of(), additionalCtx);

		AiSuggestionResult result = registry.generateSuggestion(context);
		if (!result.available()) {
			return ManagerAiSummaryResponse.unavailable(teamId, weekStart);
		}

		String contextJson = toJson(additionalCtx);
		AiSuggestion stored = suggestionService.storeSuggestion(AiContext.TYPE_TEAM_SUMMARY, callerId, null, null,
				contextJson, result);

		String summaryText = parseSummaryText(result.payload(), memberships.size(), plans.size(), allCommits.size());

		return new ManagerAiSummaryResponse(true, stored.getId(), teamId, weekStart, summaryText, topRcdoBranches,
				unresolvedExceptionIds, carryForwardPatterns, criticalBlockedItemIds, result.modelVersion());
	}

	// -------------------------------------------------------------------------
	// Derived data helpers
	// -------------------------------------------------------------------------

	private List<String> deriveTopRcdoBranches(List<WeeklyCommit> commits) {
		Map<UUID, Integer> pointsByRcdo = new LinkedHashMap<>();
		for (WeeklyCommit c : commits) {
			if (c.getRcdoNodeId() == null) {
				continue;
			}
			int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
			pointsByRcdo.merge(c.getRcdoNodeId(), pts, Integer::sum);
		}

		return pointsByRcdo.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(3)
				.map(e -> {
					RcdoNode node = rcdoNodeRepo.findById(e.getKey()).orElse(null);
					if (node == null) {
						return e.getKey().toString();
					}
					return node.getTitle() + " (" + e.getValue() + " pts)";
				}).toList();
	}

	private List<String> deriveCarryForwardPatterns(List<WeeklyCommit> commits) {
		long carryForwardCount = commits.stream().filter(c -> c.getCarryForwardStreak() > 0).count();
		if (carryForwardCount == 0) {
			return List.of();
		}
		List<String> patterns = new ArrayList<>();
		patterns.add(carryForwardCount + " commit(s) carried forward this week.");

		long repeatedCount = commits.stream().filter(c -> c.getCarryForwardStreak() >= 2).count();
		if (repeatedCount > 0) {
			patterns.add(repeatedCount + " commit(s) carried forward 2+ consecutive weeks.");
		}
		return patterns;
	}

	private List<UUID> deriveCriticalBlockedItems(List<WeeklyCommit> commits) {
		List<UUID> blocked = new ArrayList<>();
		for (WeeklyCommit c : commits) {
			if (c.getChessPiece() != ChessPiece.KING && c.getChessPiece() != ChessPiece.QUEEN) {
				continue;
			}
			if (c.getWorkItemId() == null) {
				continue;
			}
			workItemRepo.findById(c.getWorkItemId()).filter(wi -> wi.getStatus() == TicketStatus.BLOCKED)
					.ifPresent(wi -> blocked.add(wi.getId()));
		}
		return blocked;
	}

	private String parseSummaryText(String payload, int memberCount, int planCount, int commitCount) {
		try {
			JsonNode node = objectMapper.readTree(payload);
			JsonNode summaryNode = node.get("summaryText");
			if (summaryNode != null && !summaryNode.isNull() && !summaryNode.asText().isBlank()) {
				return summaryNode.asText();
			}
		} catch (JsonProcessingException e) {
			log.debug("Could not parse AI summary text: {}", e.getMessage());
		}
		// Fallback generated summary
		return "Team has " + memberCount + " member(s) with " + planCount + " plan(s) and " + commitCount
				+ " total commit(s) this week.";
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
