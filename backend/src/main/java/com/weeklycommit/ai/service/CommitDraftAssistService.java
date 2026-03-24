package com.weeklycommit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.CommitDraftAssistRequest;
import com.weeklycommit.ai.dto.CommitDraftAssistResponse;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
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
 * Provides commit drafting assistance (PRD §17 capability 1).
 *
 * <p>
 * Suggestions are editable proposals — they are never auto-applied.
 */
@Service
@Transactional
public class CommitDraftAssistService {

	private static final Logger log = LoggerFactory.getLogger(CommitDraftAssistService.class);

	private final AiProviderRegistry registry;
	private final AiSuggestionService suggestionService;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ObjectMapper objectMapper;

	public CommitDraftAssistService(AiProviderRegistry registry, AiSuggestionService suggestionService,
			WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo, ObjectMapper objectMapper) {
		this.registry = registry;
		this.suggestionService = suggestionService;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.objectMapper = objectMapper;
	}

	/**
	 * Generates commit drafting suggestions for a commit being authored.
	 *
	 * @param request
	 *            the drafting assist request
	 * @return suggestions, or a response with {@code aiAvailable=false} if AI is
	 *         unavailable
	 */
	public CommitDraftAssistResponse assist(CommitDraftAssistRequest request) {
		if (!registry.isAiEnabled()) {
			return CommitDraftAssistResponse.unavailable();
		}

		WeeklyPlan plan = planRepo.findById(request.planId())
				.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + request.planId()));

		List<WeeklyCommit> pastCommits = commitRepo.findByOwnerUserId(plan.getOwnerUserId());

		Map<String, Object> commitData = new HashMap<>();
		commitData.put("title", request.currentTitle());
		commitData.put("description", request.currentDescription());
		commitData.put("successCriteria", request.currentSuccessCriteria());
		commitData.put("estimatePoints", request.currentEstimatePoints());
		commitData.put("chessPiece", request.chessPiece() != null ? request.chessPiece().name() : null);

		Map<String, Object> planData = new HashMap<>();
		planData.put("weekStartDate", plan.getWeekStartDate().toString());
		planData.put("capacityPoints", plan.getCapacityBudgetPoints());

		List<Map<String, Object>> historical = new ArrayList<>();
		for (WeeklyCommit c : pastCommits) {
			Map<String, Object> entry = new HashMap<>();
			entry.put("title", c.getTitle());
			entry.put("estimatePoints", c.getEstimatePoints());
			entry.put("chessPiece", c.getChessPiece() != null ? c.getChessPiece().name() : null);
			historical.add(entry);
		}

		String contextJson = toJson(Map.of("commit", commitData, "plan", planData));

		AiContext context = new AiContext(AiContext.TYPE_COMMIT_DRAFT, request.userId(), request.planId(),
				request.commitId(), commitData, planData, historical, List.of(), Map.of());

		AiSuggestionResult result = registry.generateSuggestion(context);
		if (!result.available()) {
			return CommitDraftAssistResponse.unavailable();
		}

		AiSuggestion stored = suggestionService.storeSuggestion(AiContext.TYPE_COMMIT_DRAFT, request.userId(),
				request.planId(), request.commitId(), contextJson, result);

		return parseResponse(stored.getId(), result);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private CommitDraftAssistResponse parseResponse(UUID suggestionId, AiSuggestionResult result) {
		try {
			JsonNode node = objectMapper.readTree(result.payload());
			return new CommitDraftAssistResponse(true, suggestionId, textOrNull(node, "suggestedTitle"),
					textOrNull(node, "suggestedDescription"), textOrNull(node, "suggestedSuccessCriteria"),
					node.has("suggestedEstimatePoints") && !node.get("suggestedEstimatePoints").isNull()
							? node.get("suggestedEstimatePoints").asInt()
							: null,
					result.rationale());
		} catch (JsonProcessingException e) {
			log.warn("Failed to parse AI draft assist payload: {}", e.getMessage());
			return CommitDraftAssistResponse.unavailable();
		}
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode child = node.get(field);
		if (child == null || child.isNull() || child.asText().isBlank()) {
			return null;
		}
		return child.asText();
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
