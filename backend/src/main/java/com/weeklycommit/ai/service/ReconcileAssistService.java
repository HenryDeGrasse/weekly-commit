package com.weeklycommit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.ReconcileAssistRequest;
import com.weeklycommit.ai.dto.ReconcileAssistResponse;
import com.weeklycommit.ai.dto.ReconcileAssistResponse.CarryForwardRecommendation;
import com.weeklycommit.ai.dto.ReconcileAssistResponse.CommitOutcomeSuggestion;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciliation assistance service (PRD §17 capability 5).
 *
 * <p>
 * Generates:
 * <ul>
 * <li>Likely outcome suggestions for commits with no outcome set.</li>
 * <li>A draft summary of the week's changes.</li>
 * <li>Carry-forward recommendations for unfinished commits.</li>
 * </ul>
 */
@Service
@Transactional
public class ReconcileAssistService {

	private static final Logger log = LoggerFactory.getLogger(ReconcileAssistService.class);

	private final AiProviderRegistry registry;
	private final AiSuggestionService suggestionService;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final ObjectMapper objectMapper;

	public ReconcileAssistService(AiProviderRegistry registry, AiSuggestionService suggestionService,
			WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository scopeChangeRepo, ObjectMapper objectMapper) {
		this.registry = registry;
		this.suggestionService = suggestionService;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.objectMapper = objectMapper;
	}

	/**
	 * Generates reconciliation assistance for the given plan.
	 *
	 * @param request
	 *            the reconcile assist request
	 * @return reconcile assist response or unavailable
	 */
	public ReconcileAssistResponse assist(ReconcileAssistRequest request) {
		if (!registry.isAiEnabled()) {
			return ReconcileAssistResponse.unavailable();
		}

		WeeklyPlan plan = planRepo.findById(request.planId())
				.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.planId()));

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		long scopeChangeCount = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId()).size();

		Map<String, Object> planData = new HashMap<>();
		planData.put("weekStartDate", plan.getWeekStartDate().toString());
		planData.put("state", plan.getState().name());
		planData.put("scopeChanges", scopeChangeCount);
		planData.put("commitCount", commits.size());

		List<Map<String, Object>> commitList = new ArrayList<>();
		for (WeeklyCommit c : commits) {
			Map<String, Object> entry = new HashMap<>();
			entry.put("id", c.getId().toString());
			entry.put("title", c.getTitle());
			entry.put("chessPiece", c.getChessPiece() != null ? c.getChessPiece().name() : null);
			entry.put("outcome", c.getOutcome() != null ? c.getOutcome().name() : null);
			entry.put("carryForwardStreak", c.getCarryForwardStreak());
			commitList.add(entry);
		}

		AiContext context = new AiContext(AiContext.TYPE_RECONCILE_ASSIST, request.userId(), request.planId(), null,
				Map.of(), planData, commitList, List.of(), Map.of());

		AiSuggestionResult result = registry.generateSuggestion(context);
		if (!result.available()) {
			return ReconcileAssistResponse.unavailable();
		}

		String contextJson = toJson(planData);
		AiSuggestion stored = suggestionService.storeSuggestion(AiContext.TYPE_RECONCILE_ASSIST, request.userId(),
				request.planId(), null, contextJson, result);

		return parseResponse(stored.getId(), result, commits);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private ReconcileAssistResponse parseResponse(UUID suggestionId, AiSuggestionResult result,
			List<WeeklyCommit> commits) {
		List<CommitOutcomeSuggestion> outcomes = new ArrayList<>();
		List<CarryForwardRecommendation> carryForwards = new ArrayList<>();
		String draftSummary = null;

		try {
			JsonNode node = objectMapper.readTree(result.payload());

			draftSummary = node.has("draftSummary") ? node.get("draftSummary").asText(null) : null;

			// Likely outcomes from AI (stub returns empty list)
			if (node.has("likelyOutcomes") && node.get("likelyOutcomes").isArray()) {
				for (JsonNode entry : node.get("likelyOutcomes")) {
					String commitIdStr = entry.has("commitId") ? entry.get("commitId").asText(null) : null;
					if (commitIdStr == null) {
						continue;
					}
					try {
						outcomes.add(new CommitOutcomeSuggestion(UUID.fromString(commitIdStr),
								entry.has("commitTitle") ? entry.get("commitTitle").asText(null) : null,
								entry.has("suggestedOutcome") ? entry.get("suggestedOutcome").asText(null) : null,
								entry.has("rationale") ? entry.get("rationale").asText(null) : null));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}

			// Carry-forward recommendations from AI (stub returns empty list)
			if (node.has("carryForwardRecommendations") && node.get("carryForwardRecommendations").isArray()) {
				for (JsonNode entry : node.get("carryForwardRecommendations")) {
					String commitIdStr = entry.has("commitId") ? entry.get("commitId").asText(null) : null;
					if (commitIdStr == null) {
						continue;
					}
					try {
						carryForwards.add(new CarryForwardRecommendation(UUID.fromString(commitIdStr),
								entry.has("commitTitle") ? entry.get("commitTitle").asText(null) : null,
								entry.has("rationale") ? entry.get("rationale").asText(null) : null));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}

		} catch (JsonProcessingException e) {
			log.warn("Failed to parse reconcile assist payload: {}", e.getMessage());
		}

		return new ReconcileAssistResponse(true, suggestionId, outcomes, draftSummary, carryForwards);
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
