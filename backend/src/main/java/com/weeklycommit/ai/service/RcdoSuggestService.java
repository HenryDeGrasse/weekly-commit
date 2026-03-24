package com.weeklycommit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.RcdoSuggestRequest;
import com.weeklycommit.ai.dto.RcdoSuggestResponse;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
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
 * RCDO link suggestion service (PRD §17 capability 3).
 *
 * <p>
 * Suggestions are only surfaced when confidence &ge; 0.7. Never auto-links.
 */
@Service
@Transactional
public class RcdoSuggestService {

	private static final Logger log = LoggerFactory.getLogger(RcdoSuggestService.class);

	/** Minimum confidence to surface an RCDO suggestion. */
	static final double CONFIDENCE_THRESHOLD = 0.7;

	private final AiProviderRegistry registry;
	private final AiSuggestionService suggestionService;
	private final RcdoNodeRepository rcdoNodeRepo;
	private final ObjectMapper objectMapper;

	public RcdoSuggestService(AiProviderRegistry registry, AiSuggestionService suggestionService,
			RcdoNodeRepository rcdoNodeRepo, ObjectMapper objectMapper) {
		this.registry = registry;
		this.suggestionService = suggestionService;
		this.rcdoNodeRepo = rcdoNodeRepo;
		this.objectMapper = objectMapper;
	}

	/**
	 * Suggests a primary RCDO node for the given commit draft.
	 *
	 * @param request
	 *            the suggest request
	 * @return suggestion response; returns below-threshold if no confident match
	 *         found, or unavailable if AI is disabled
	 */
	public RcdoSuggestResponse suggest(RcdoSuggestRequest request) {
		if (!registry.isAiEnabled()) {
			return RcdoSuggestResponse.unavailable();
		}

		List<RcdoNode> activeNodes = rcdoNodeRepo.findByStatus(RcdoNodeStatus.ACTIVE);
		List<Map<String, Object>> rcdoTree = new ArrayList<>();
		for (RcdoNode node : activeNodes) {
			Map<String, Object> entry = new HashMap<>();
			entry.put("id", node.getId().toString());
			entry.put("title", node.getTitle());
			entry.put("nodeType", node.getNodeType().name());
			entry.put("parentId", node.getParentId() != null ? node.getParentId().toString() : null);
			rcdoTree.add(entry);
		}

		Map<String, Object> commitData = new HashMap<>();
		commitData.put("title", request.title());
		commitData.put("description", request.description());
		commitData.put("chessPiece", request.chessPiece() != null ? request.chessPiece().name() : null);

		AiContext context = new AiContext(AiContext.TYPE_RCDO_SUGGEST, request.userId(), request.planId(), null,
				commitData, Map.of(), List.of(), rcdoTree, Map.of());

		AiSuggestionResult result = registry.generateSuggestion(context);
		if (!result.available()) {
			return RcdoSuggestResponse.unavailable();
		}

		// Apply confidence threshold — do not surface low-confidence suggestions
		if (result.confidence() < CONFIDENCE_THRESHOLD) {
			log.debug("RCDO suggestion below threshold: confidence={}", result.confidence());
			return RcdoSuggestResponse.belowThreshold();
		}

		// Parse suggested RCDO id from payload
		UUID suggestedId = parseSuggestedRcdoId(result.payload());
		if (suggestedId == null) {
			return RcdoSuggestResponse.belowThreshold();
		}

		// Resolve display title
		String rcdoTitle = rcdoNodeRepo.findById(suggestedId).map(RcdoNode::getTitle).orElse(null);
		if (rcdoTitle == null) {
			log.debug("Suggested RCDO node {} not found; returning below-threshold", suggestedId);
			return RcdoSuggestResponse.belowThreshold();
		}

		// Use HashMap to allow null values (description may be null)
		java.util.Map<String, String> ctxMap = new java.util.HashMap<>();
		ctxMap.put("title", request.title());
		ctxMap.put("description", request.description());
		String contextJson = toJson(ctxMap);
		AiSuggestion stored = suggestionService.storeSuggestion(AiContext.TYPE_RCDO_SUGGEST, request.userId(),
				request.planId(), null, contextJson, result);

		return new RcdoSuggestResponse(true, true, stored.getId(), suggestedId, rcdoTitle, result.confidence(),
				result.rationale());
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private UUID parseSuggestedRcdoId(String payload) {
		try {
			JsonNode node = objectMapper.readTree(payload);
			JsonNode idNode = node.get("suggestedRcdoNodeId");
			if (idNode == null || idNode.isNull() || idNode.asText().isBlank()) {
				return null;
			}
			String raw = idNode.asText().trim();
			return raw.isEmpty() ? null : UUID.fromString(raw);
		} catch (JsonProcessingException | IllegalArgumentException e) {
			log.debug("Failed to parse RCDO suggestion payload: {}", e.getMessage());
			return null;
		}
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}
}
