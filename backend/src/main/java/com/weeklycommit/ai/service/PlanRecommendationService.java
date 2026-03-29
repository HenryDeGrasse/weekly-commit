package com.weeklycommit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.RiskSignalResponse;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.ai.dto.WhatIfRequest;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation;
import com.weeklycommit.ai.dto.WhatIfRequest.WhatIfMutation.WhatIfAction;
import com.weeklycommit.ai.dto.WhatIfResponse;
import com.weeklycommit.ai.evidence.ConfidenceTierCalculator.ConfidenceTier;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.CalibrationService.CalibrationProfile;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the risk &rarr; what-if &rarr; recommendation pipeline for a
 * plan.
 *
 * <p>
 * Two entry points:
 * <ul>
 * <li>{@link #generateAndPersistRecommendations(UUID, UUID)} &mdash;
 * (re)generates recommendations, persisting each as an {@code AiSuggestion} row
 * so it receives a stable {@code suggestionId} for frontend feedback buttons.
 * Deletes any stale {@code PLAN_RECOMMENDATION} rows first.</li>
 * <li>{@link #getRecommendations(UUID)} &mdash; read-only retrieval of the
 * already- persisted recommendations; returns stable IDs without triggering
 * re-generation.</li>
 * </ul>
 *
 * <p>
 * Graceful degradation: if {@code WhatIfService} or {@code CalibrationService}
 * fails the recommendation is still produced (and persisted) with
 * {@code whatIfResult = null} so it gets a {@code suggestionId}.
 */
@Service
@Transactional
public class PlanRecommendationService {

	private static final Logger log = LoggerFactory.getLogger(PlanRecommendationService.class);

	static final String SUGGESTION_TYPE = "PLAN_RECOMMENDATION";
	private static final String MODEL_VERSION = "plan-recommendation-v1";

	private final RiskDetectionService riskDetectionService;
	private final WhatIfService whatIfService;
	private final CalibrationService calibrationService;
	private final WeeklyCommitRepository commitRepo;
	private final AiSuggestionRepository suggestionRepo;
	private final AiSuggestionService aiSuggestionService;
	private final ObjectMapper objectMapper;

	public PlanRecommendationService(RiskDetectionService riskDetectionService, WhatIfService whatIfService,
			CalibrationService calibrationService, WeeklyCommitRepository commitRepo,
			AiSuggestionRepository suggestionRepo, AiSuggestionService aiSuggestionService, ObjectMapper objectMapper) {
		this.riskDetectionService = riskDetectionService;
		this.whatIfService = whatIfService;
		this.calibrationService = calibrationService;
		this.commitRepo = commitRepo;
		this.suggestionRepo = suggestionRepo;
		this.aiSuggestionService = aiSuggestionService;
		this.objectMapper = objectMapper;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Generates and persists plan recommendations by running the risk &rarr;
	 * what-if &rarr; recommendation pipeline.
	 *
	 * <p>
	 * Delete-then-regenerate: any stale {@code PLAN_RECOMMENDATION} rows are
	 * deleted before new ones are persisted. The returned
	 * {@link PlanRecommendation#suggestionId()} values are the ids of the freshly
	 * persisted rows.
	 *
	 * @param planId
	 *            the plan to analyse
	 * @param userId
	 *            the requesting user (owner or manager)
	 * @return generated recommendations, empty when no risks are detected
	 */
	public List<PlanRecommendation> generateAndPersistRecommendations(UUID planId, UUID userId) {
		// 1. Delete stale recommendations
		List<AiSuggestion> existing = suggestionRepo.findByPlanIdAndSuggestionType(planId, SUGGESTION_TYPE);
		suggestionRepo.deleteAll(existing);

		// 2. Get current risk signals; detect first if none stored
		PlanRiskSignals riskSignals;
		try {
			riskSignals = riskDetectionService.getRiskSignals(planId, userId);
			if (riskSignals.signals().isEmpty()) {
				riskDetectionService.detectAndStoreRiskSignalsById(planId);
				riskSignals = riskDetectionService.getRiskSignals(planId, userId);
			}
		} catch (Exception ex) {
			log.warn("PlanRecommendationService: failed to get risk signals for plan {}: {}", planId, ex.getMessage());
			return List.of();
		}

		if (riskSignals.signals().isEmpty()) {
			log.debug("PlanRecommendationService: no risk signals for plan {}, no recommendations generated", planId);
			return List.of();
		}

		// 3. Load commits once for all mutation lookups
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);

		// 4. Get calibration profile (best-effort — never blocks recommendations)
		CalibrationProfile calibration = null;
		try {
			calibration = calibrationService.getCalibration(userId);
		} catch (Exception ex) {
			log.debug("PlanRecommendationService: calibration lookup failed for user {}: {}", userId, ex.getMessage());
		}

		// 5. Generate one recommendation per risk signal
		List<PlanRecommendation> recommendations = new ArrayList<>();
		for (RiskSignalResponse signal : riskSignals.signals()) {
			try {
				PlanRecommendation rec = generateForSignal(planId, userId, signal, commits, calibration);
				recommendations.add(rec);
			} catch (Exception ex) {
				log.warn("PlanRecommendationService: failed to generate recommendation for signal {}: {}",
						signal.signalType(), ex.getMessage());
			}
		}

		return recommendations;
	}

	/**
	 * Returns persisted {@code PLAN_RECOMMENDATION} rows as stable
	 * {@link PlanRecommendation} objects. Does <em>not</em> regenerate — call
	 * {@link #generateAndPersistRecommendations} for a refresh.
	 *
	 * <p>
	 *
	 * @param planId
	 *            the plan to query
	 * @return persisted recommendations in insertion order
	 */
	@Transactional(readOnly = true)
	public List<PlanRecommendation> getRecommendations(UUID planId) {
		List<AiSuggestion> rows = suggestionRepo.findByPlanIdAndSuggestionType(planId, SUGGESTION_TYPE);
		List<PlanRecommendation> result = new ArrayList<>();
		for (AiSuggestion row : rows) {
			try {
				result.add(deserializeRecommendation(row));
			} catch (Exception ex) {
				log.debug("PlanRecommendationService: skipping unparseable recommendation row {}: {}", row.getId(),
						ex.getMessage());
			}
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Core generation logic
	// -------------------------------------------------------------------------

	private PlanRecommendation generateForSignal(UUID planId, UUID userId, RiskSignalResponse signal,
			List<WeeklyCommit> commits, CalibrationProfile calibration) {

		String signalType = signal.signalType();
		String description = signal.rationale();
		String suggestedAction = buildSuggestedAction(signalType, signal.commitId(), commits);
		ConfidenceTier confidence = computeConfidenceTier(signalType, calibration);

		// What-if simulation — graceful degradation: partial recommendation on failure
		WhatIfResponse whatIfResult = null;
		try {
			whatIfResult = simulateForSignal(planId, userId, signalType, signal.commitId(), commits);
		} catch (Exception ex) {
			log.debug("PlanRecommendationService: what-if simulation failed for signal {}: {}", signalType,
					ex.getMessage());
		}

		String narrative = buildNarrative(description, whatIfResult);
		String contextJson = buildContextJson(signal, calibration);
		String payloadJson = serializePayload(signalType, description, suggestedAction, whatIfResult, narrative,
				confidence);

		// Persist via AiSuggestionService for faithfulness eval and audit trail
		String rationale = description + (suggestedAction.isBlank() ? "" : " \u2192 " + suggestedAction);
		AiSuggestionResult result = new AiSuggestionResult(true, payloadJson, rationale, confidenceScore(confidence),
				MODEL_VERSION, MODEL_VERSION);

		AiSuggestion saved = aiSuggestionService.storeSuggestion(SUGGESTION_TYPE, userId, planId, signal.commitId(),
				contextJson, result);

		return new PlanRecommendation(saved.getId(), signalType, description, suggestedAction, whatIfResult, narrative,
				confidence);
	}

	// -------------------------------------------------------------------------
	// Simulation helpers
	// -------------------------------------------------------------------------

	private WhatIfResponse simulateForSignal(UUID planId, UUID userId, String signalType, UUID signalCommitId,
			List<WeeklyCommit> commits) {
		switch (signalType) {
			case "OVERCOMMIT" :
			case "UNDERCOMMIT" : {
				WeeklyCommit target = findLowestPriorityCommit(commits);
				if (target == null) {
					return null;
				}
				return whatIfService.simulate(new WhatIfRequest(planId, userId, List
						.of(new WhatIfMutation(WhatIfAction.REMOVE_COMMIT, target.getId(), null, null, null, null))));
			}
			case "REPEATED_CARRY_FORWARD" : {
				WeeklyCommit target = findCarryForwardCommit(commits, signalCommitId);
				if (target == null || target.getEstimatePoints() == null) {
					return null;
				}
				int reducedEst = Math.max(1, target.getEstimatePoints() / 2);
				return whatIfService.simulate(new WhatIfRequest(planId, userId, List.of(
						new WhatIfMutation(WhatIfAction.MODIFY_COMMIT, target.getId(), null, null, reducedEst, null))));
			}
			default :
				return null;
		}
	}

	// -------------------------------------------------------------------------
	// Text-building helpers
	// -------------------------------------------------------------------------

	private String buildSuggestedAction(String signalType, UUID commitId, List<WeeklyCommit> commits) {
		switch (signalType) {
			case "OVERCOMMIT" : {
				WeeklyCommit t = findLowestPriorityCommit(commits);
				return t != null
						? "Remove or reschedule '" + t.getTitle() + "' to reduce load."
						: "Remove or reschedule a low-priority commit to reduce load.";
			}
			case "UNDERCOMMIT" :
				return "Add more commits or increase estimates to better utilise capacity.";
			case "REPEATED_CARRY_FORWARD" : {
				WeeklyCommit t = findCarryForwardCommit(commits, commitId);
				return t != null
						? "Break down '" + t.getTitle() + "' into smaller tasks or reduce its estimate."
						: "Break down the carried-forward task into smaller tasks.";
			}
			case "BLOCKED_CRITICAL" :
				return "Unblock the linked ticket to enable progress on this critical commitment.";
			case "SCOPE_VOLATILITY" :
				return "Limit scope changes post-lock to improve plan stability.";
			default :
				return "Review this risk signal and take corrective action.";
		}
	}

	private ConfidenceTier computeConfidenceTier(String signalType, CalibrationProfile calibration) {
		switch (signalType) {
			case "OVERCOMMIT" :
			case "UNDERCOMMIT" :
				// Rules-based arithmetic signal — always high-confidence
				return ConfidenceTier.HIGH;
			case "REPEATED_CARRY_FORWARD" :
				if (calibration != null && !calibration.isInsufficient()) {
					return mapCalibrationTier(calibration.confidenceTier());
				}
				return ConfidenceTier.MEDIUM;
			default :
				return ConfidenceTier.MEDIUM;
		}
	}

	private ConfidenceTier mapCalibrationTier(CalibrationService.CalibrationConfidenceTier tier) {
		switch (tier) {
			case HIGH :
				return ConfidenceTier.HIGH;
			case MEDIUM :
				return ConfidenceTier.MEDIUM;
			case LOW :
			case INSUFFICIENT :
			default :
				return ConfidenceTier.LOW;
		}
	}

	private String buildNarrative(String description, WhatIfResponse whatIfResult) {
		if (whatIfResult == null || !whatIfResult.available()) {
			return description;
		}
		StringBuilder sb = new StringBuilder(description);
		if (whatIfResult.narrative() != null && !whatIfResult.narrative().isBlank()) {
			sb.append(" ").append(whatIfResult.narrative());
		} else {
			List<String> resolved = whatIfResult.riskDelta() != null
					? whatIfResult.riskDelta().resolvedRisks()
					: List.of();
			if (!resolved.isEmpty()) {
				sb.append(" Simulating this change would resolve: ").append(String.join(", ", resolved)).append(".");
			}
			int delta = whatIfResult.capacityDelta();
			if (delta != 0) {
				sb.append(" Capacity delta: ").append(delta > 0 ? "+" : "").append(delta).append(" points.");
			}
		}
		return sb.toString();
	}

	// -------------------------------------------------------------------------
	// Serialisation helpers
	// -------------------------------------------------------------------------

	private String buildContextJson(RiskSignalResponse signal, CalibrationProfile calibration) {
		Map<String, Object> ctx = new LinkedHashMap<>();
		ctx.put("signalType", signal.signalType());
		ctx.put("signalRationale", signal.rationale());
		ctx.put("planId", signal.planId() != null ? signal.planId().toString() : null);
		ctx.put("commitId", signal.commitId() != null ? signal.commitId().toString() : null);
		if (calibration != null && !calibration.isInsufficient()) {
			ctx.put("calibrationOverallRate", calibration.overallAchievementRate());
			ctx.put("calibrationCarryForwardProb", calibration.carryForwardProbability());
			ctx.put("calibrationWeeksOfData", calibration.weeksOfData());
		}
		try {
			return objectMapper.writeValueAsString(ctx);
		} catch (JsonProcessingException ex) {
			return "{}";
		}
	}

	private String serializePayload(String riskType, String description, String suggestedAction,
			WhatIfResponse whatIfResult, String narrative, ConfidenceTier confidence) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("riskType", riskType);
		payload.put("description", description);
		payload.put("suggestedAction", suggestedAction);
		payload.put("whatIfResult", whatIfResult);
		payload.put("narrative", narrative != null ? narrative : description);
		payload.put("confidence", confidence.name());
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException ex) {
			return "{\"riskType\":\"" + riskType + "\",\"confidence\":\"" + confidence.name() + "\"}";
		}
	}

	private PlanRecommendation deserializeRecommendation(AiSuggestion row) throws Exception {
		JsonNode node = objectMapper.readTree(row.getSuggestionPayload());
		String riskType = node.path("riskType").asText("");
		String description = node.path("description").asText("");
		String suggestedAction = node.path("suggestedAction").asText("");
		String narrative = node.path("narrative").asText("");
		WhatIfResponse whatIfResult = node.hasNonNull("whatIfResult")
				? objectMapper.treeToValue(node.get("whatIfResult"), WhatIfResponse.class)
				: null;
		String confidenceName = node.path("confidence").asText(ConfidenceTier.MEDIUM.name());
		ConfidenceTier confidence;
		try {
			confidence = ConfidenceTier.valueOf(confidenceName);
		} catch (IllegalArgumentException ex) {
			confidence = ConfidenceTier.MEDIUM;
		}
		return new PlanRecommendation(row.getId(), riskType, description, suggestedAction, whatIfResult, narrative,
				confidence);
	}

	// -------------------------------------------------------------------------
	// Commit selection helpers
	// -------------------------------------------------------------------------

	/**
	 * Finds the lowest-priority commit from an ascending priority-order list.
	 * Prefers {@link ChessPiece#PAWN} commits (scanning from the end of the list
	 * first), then falls back to the last element.
	 */
	private WeeklyCommit findLowestPriorityCommit(List<WeeklyCommit> commits) {
		if (commits.isEmpty()) {
			return null;
		}
		// Scan from lowest priority (highest index) to find a PAWN first
		for (int i = commits.size() - 1; i >= 0; i--) {
			if (commits.get(i).getChessPiece() == ChessPiece.PAWN) {
				return commits.get(i);
			}
		}
		// No PAWN found — return the commit with the highest priority order number
		return commits.get(commits.size() - 1);
	}

	/**
	 * Finds the specific carry-forward commit identified by the risk signal's
	 * {@code commitId}, or the first carry-forward commit in priority order if the
	 * id is not matched.
	 */
	private WeeklyCommit findCarryForwardCommit(List<WeeklyCommit> commits, UUID signalCommitId) {
		if (signalCommitId != null) {
			for (WeeklyCommit c : commits) {
				if (signalCommitId.equals(c.getId())) {
					return c;
				}
			}
		}
		// Fall back to any commit with a carry-forward streak at or above threshold
		for (WeeklyCommit c : commits) {
			if (c.getCarryForwardStreak() >= RiskDetectionService.CARRY_FORWARD_STREAK_THRESHOLD) {
				return c;
			}
		}
		return null;
	}

	private double confidenceScore(ConfidenceTier tier) {
		switch (tier) {
			case HIGH :
				return 1.0;
			case MEDIUM :
				return 0.7;
			case LOW :
				return 0.4;
			case INSUFFICIENT :
			default :
				return 0.1;
		}
	}

	// -------------------------------------------------------------------------
	// Public nested types
	// -------------------------------------------------------------------------

	/**
	 * A plan recommendation produced by the risk &rarr; what-if &rarr;
	 * recommendation pipeline.
	 *
	 * <p>
	 * The {@link #suggestionId()} is the primary key of the persisted
	 * {@code AiSuggestion} row. It is stable across page reloads and can be used by
	 * the frontend to post feedback via {@code AiFeedbackButtons} and track
	 * per-recommendation dismissal.
	 *
	 */
	public record PlanRecommendation(
			/** AiSuggestion row id — stable primary key for feedback and dismissal. */
			UUID suggestionId,
			/**
			 * Risk signal type that triggered this recommendation (e.g.
			 * {@code "OVERCOMMIT"}).
			 */
			String riskType,
			/** Human-readable description of the detected risk. */
			String description,
			/** Suggested action to address the risk. */
			String suggestedAction,
			/**
			 * What-if simulation result. {@code null} only when the simulation failed
			 * (graceful degradation).
			 */
			WhatIfResponse whatIfResult,
			/** Combined narrative from the risk signal and optional what-if result. */
			String narrative,
			/** Evidence-quality confidence tier for this recommendation. */
			ConfidenceTier confidence) {
	}
}
