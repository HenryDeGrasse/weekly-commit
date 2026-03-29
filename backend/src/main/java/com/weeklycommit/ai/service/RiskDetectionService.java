package com.weeklycommit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.RiskSignalResponse;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.service.AuthorizationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Risk signal detection service (PRD §17 capability 4).
 *
 * <p>
 * Computes risk signals on plan lock and via a daily scheduled job for all
 * LOCKED plans. Signals are stored in the {@code ai_suggestion} table with
 * {@code suggestion_type = 'RISK_SIGNAL'}.
 *
 * <p>
 * v1 signal types with rationale:
 * <ul>
 * <li>OVERCOMMIT — planned points exceeds capacity budget.</li>
 * <li>UNDERCOMMIT — planned points is less than 60% of capacity budget.</li>
 * <li>REPEATED_CARRY_FORWARD — at least one commit with streak &ge; 2.</li>
 * <li>BLOCKED_CRITICAL — King/Queen commit with linked ticket BLOCKED for more
 * than 2 days.</li>
 * <li>SCOPE_VOLATILITY — more than 3 post-lock scope change events.</li>
 * </ul>
 *
 * <p>
 * Risk signals are hidden from peers — only the plan owner and their manager
 * may view them.
 */
@Service
@Transactional
public class RiskDetectionService {

	private static final Logger log = LoggerFactory.getLogger(RiskDetectionService.class);

	/** Blocked-for threshold in hours for the BLOCKED_CRITICAL signal. */
	static final long BLOCKED_CRITICAL_HOURS = 48;

	/** Carry-forward streak threshold for the REPEATED_CARRY_FORWARD signal. */
	static final int CARRY_FORWARD_STREAK_THRESHOLD = 2;

	/** Scope-change count threshold for SCOPE_VOLATILITY. */
	static final int SCOPE_VOLATILITY_THRESHOLD = 3;

	/** Minimum fill rate (fraction of capacity) to avoid UNDERCOMMIT. */
	static final double UNDERCOMMIT_THRESHOLD = 0.60;

	private static final String MODEL_VERSION = "rules-v1";
	private static final String STUB_RATIONALE_PREFIX = "Computed by rules-based risk engine";

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final WorkItemRepository workItemRepo;
	private final WorkItemStatusHistoryRepository statusHistoryRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final AiSuggestionRepository suggestionRepo;
	private final AuthorizationService authService;
	private final AiProviderRegistry aiProviderRegistry;
	private final ObjectMapper objectMapper;
	private final CalibrationService calibrationService;

	public RiskDetectionService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			WorkItemRepository workItemRepo, WorkItemStatusHistoryRepository statusHistoryRepo,
			ScopeChangeEventRepository scopeChangeRepo, AiSuggestionRepository suggestionRepo,
			AuthorizationService authService, AiProviderRegistry aiProviderRegistry, ObjectMapper objectMapper,
			CalibrationService calibrationService) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.workItemRepo = workItemRepo;
		this.statusHistoryRepo = statusHistoryRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.suggestionRepo = suggestionRepo;
		this.authService = authService;
		this.aiProviderRegistry = aiProviderRegistry;
		this.objectMapper = objectMapper;
		this.calibrationService = calibrationService;
	}

	// -------------------------------------------------------------------------
	// Scheduled daily job
	// -------------------------------------------------------------------------

	/**
	 * Daily job: refreshes risk signals for all currently LOCKED plans. Runs at
	 * 07:00 UTC every day.
	 */
	@Scheduled(cron = "0 0 7 * * *")
	public void runDailyRiskDetection() {
		log.info("Running daily risk detection for LOCKED plans");
		List<WeeklyPlan> lockedPlans = planRepo.findByState(PlanState.LOCKED);
		for (WeeklyPlan plan : lockedPlans) {
			try {
				detectAndStoreRiskSignals(plan);
			} catch (Exception ex) {
				log.warn("Risk detection failed for plan {}: {}", plan.getId(), ex.getMessage());
			}
		}
		log.info("Daily risk detection complete: {} plans processed", lockedPlans.size());
	}

	// -------------------------------------------------------------------------
	// On-demand risk detection (called on lock)
	// -------------------------------------------------------------------------

	/**
	 * Detects and persists risk signals for the given plan. Idempotent — existing
	 * signals for the plan are replaced on each run.
	 *
	 * @param planId
	 *            the plan to analyse
	 */
	public void detectAndStoreRiskSignalsById(UUID planId) {
		WeeklyPlan plan = planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));
		detectAndStoreRiskSignals(plan);
	}

	// -------------------------------------------------------------------------
	// Query
	// -------------------------------------------------------------------------

	/**
	 * Returns all current risk signals for a plan.
	 *
	 * <p>
	 * Privacy: only the plan owner, their manager, or an admin may access these
	 * signals.
	 *
	 * @param planId
	 *            the plan id
	 * @param callerId
	 *            the requesting user
	 * @return risk signals response
	 */
	@Transactional(readOnly = true)
	public PlanRiskSignals getRiskSignals(UUID planId, UUID callerId) {
		WeeklyPlan plan = planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));
		authService.checkCanAccessUserFullDetail(callerId, plan.getOwnerUserId());

		List<AiSuggestion> suggestions = suggestionRepo.findByPlanIdAndSuggestionType(planId, "RISK_SIGNAL");
		List<RiskSignalResponse> signals = suggestions.stream()
				.map(s -> new RiskSignalResponse(s.getId(),
						extractSignalType(s.getRationale(), s.getSuggestionPayload()), s.getRationale(), s.getPlanId(),
						s.getCommitId(), s.getCreatedAt()))
				.toList();

		return new PlanRiskSignals(true, planId, signals);
	}

	// -------------------------------------------------------------------------
	// Core detection logic
	// -------------------------------------------------------------------------

	/**
	 * Computes risk signals for a plan and stores them. Existing RISK_SIGNAL
	 * entries for this plan are deleted first to ensure freshness.
	 */
	void detectAndStoreRiskSignals(WeeklyPlan plan) {
		// Delete stale signals
		List<AiSuggestion> existing = suggestionRepo.findByPlanIdAndSuggestionType(plan.getId(), "RISK_SIGNAL");
		suggestionRepo.deleteAll(existing);

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		List<RawSignal> signals = new ArrayList<>();

		// 1. OVERCOMMIT
		int totalPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
				.sum();
		int budget = plan.getCapacityBudgetPoints();
		if (totalPoints > budget) {
			signals.add(new RawSignal("OVERCOMMIT", null, STUB_RATIONALE_PREFIX + ": planned " + totalPoints
					+ " pts exceeds capacity budget of " + budget + " pts.", "{}"));
		}

		// 2. UNDERCOMMIT
		if (budget > 0 && (double) totalPoints / budget < UNDERCOMMIT_THRESHOLD) {
			signals.add(new RawSignal("UNDERCOMMIT", null,
					STUB_RATIONALE_PREFIX + ": planned " + totalPoints + " pts is less than "
							+ (int) (UNDERCOMMIT_THRESHOLD * 100) + "% of capacity budget (" + budget + " pts).",
					"{}"));
		}

		// 3. REPEATED_CARRY_FORWARD
		for (WeeklyCommit commit : commits) {
			if (commit.getCarryForwardStreak() >= CARRY_FORWARD_STREAK_THRESHOLD) {
				signals.add(new RawSignal("REPEATED_CARRY_FORWARD", commit.getId(),
						STUB_RATIONALE_PREFIX + ": commit \"" + commit.getTitle() + "\" has been carried forward "
								+ commit.getCarryForwardStreak() + " times in a row (streak >= "
								+ CARRY_FORWARD_STREAK_THRESHOLD + ").",
						"{}"));
			}
		}

		// 4. BLOCKED_CRITICAL
		Instant now = Instant.now();
		for (WeeklyCommit commit : commits) {
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
			// Find when it became BLOCKED
			Instant blockedSince = findBlockedSince(ticket.getId());
			if (blockedSince != null && Duration.between(blockedSince, now).toHours() >= BLOCKED_CRITICAL_HOURS) {
				signals.add(new RawSignal("BLOCKED_CRITICAL", commit.getId(),
						STUB_RATIONALE_PREFIX + ": " + commit.getChessPiece() + " commit \"" + commit.getTitle()
								+ "\" linked ticket has been BLOCKED for more than " + BLOCKED_CRITICAL_HOURS
								+ " hours.",
						"{}"));
			}
		}

		// 5. SCOPE_VOLATILITY
		long scopeChanges = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId()).size();
		if (scopeChanges > SCOPE_VOLATILITY_THRESHOLD) {
			signals.add(
					new RawSignal("SCOPE_VOLATILITY", null,
							STUB_RATIONALE_PREFIX + ": plan has " + scopeChanges
									+ " post-lock scope changes (threshold: " + SCOPE_VOLATILITY_THRESHOLD + ").",
							"{}"));
		}

		// ── LLM augmentation: find risks that rules miss (BOAD/ADR-001 pattern) ──
		List<RawSignal> aiSignals = detectAiRiskSignals(plan, commits);
		signals.addAll(aiSignals);

		// Persist signals
		for (RawSignal signal : signals) {
			AiSuggestion s = new AiSuggestion();
			s.setSuggestionType("RISK_SIGNAL");
			s.setPlanId(plan.getId());
			s.setCommitId(signal.commitId());
			s.setUserId(plan.getOwnerUserId());
			s.setPrompt(signal.prompt());
			s.setRationale(signal.rationale());
			s.setSuggestionPayload("{\"signalType\":\"" + signal.type() + "\"}");
			s.setModelVersion(signal.type().startsWith("AI_")
					? aiProviderRegistry.getActiveProvider().map(p -> p.getVersion()).orElse(MODEL_VERSION)
					: MODEL_VERSION);
			suggestionRepo.save(s);
		}

		log.debug("Risk detection for plan {}: {} signal(s) stored ({} rules, {} AI)", plan.getId(), signals.size(),
				signals.size() - aiSignals.size(), aiSignals.size());
	}

	// -------------------------------------------------------------------------
	// LLM risk augmentation (BOAD/ADR-001: supplementary AI risk detection)
	// -------------------------------------------------------------------------

	/**
	 * Calls the LLM with the risk-signal prompt to find risks that the rules engine
	 * misses (hidden dependencies, unrealistic estimates, concentration risk,
	 * etc.). Returns an empty list if AI is unavailable — never blocks the rules
	 * engine.
	 */
	private List<RawSignal> detectAiRiskSignals(WeeklyPlan plan, List<WeeklyCommit> commits) {
		if (!aiProviderRegistry.isAiEnabled()) {
			return List.of();
		}

		try {
			// Build commit data for the LLM
			List<Map<String, Object>> commitDataList = new ArrayList<>();
			for (WeeklyCommit c : commits) {
				Map<String, Object> cd = new LinkedHashMap<>();
				cd.put("commitId", c.getId() != null ? c.getId().toString() : null);
				cd.put("title", c.getTitle());
				cd.put("chessPiece", c.getChessPiece() != null ? c.getChessPiece().name() : null);
				cd.put("estimatePoints", c.getEstimatePoints());
				cd.put("description", c.getDescription());
				cd.put("successCriteria", c.getSuccessCriteria());
				cd.put("carryForwardStreak", c.getCarryForwardStreak());
				cd.put("outcome", c.getOutcome() != null ? c.getOutcome().name() : null);
				commitDataList.add(cd);
			}

			Map<String, Object> planData = new LinkedHashMap<>();
			planData.put("state", plan.getState() != null ? plan.getState().name() : null);
			planData.put("capacityBudgetPoints", plan.getCapacityBudgetPoints());
			planData.put("weekStartDate", plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : null);
			planData.put("totalPlannedPoints",
					commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum());

			// Build scope change context
			var scopeChanges = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId());
			Map<String, Object> additionalContext = new LinkedHashMap<>();
			additionalContext.put("scopeChanges", scopeChanges.stream().map(sc -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("category", sc.getCategory() != null ? sc.getCategory().name() : null);
				m.put("reason", sc.getReason());
				return m;
			}).toList());

			// Include calibration profile so the LLM can see the user's historical
			// achievement rates and carry-forward probability when assessing risks.
			try {
				CalibrationService.CalibrationProfile calibration = calibrationService
						.getCalibration(plan.getOwnerUserId());
				if (!calibration.isInsufficient()) {
					additionalContext.put("calibration", objectMapper.writeValueAsString(calibration));
				}
			} catch (Exception ex) {
				log.debug("RiskDetectionService: calibration lookup failed for plan {} \u2014 {}", plan.getId(),
						ex.getMessage());
			}

			AiContext context = new AiContext(AiContext.TYPE_RISK_SIGNAL, plan.getOwnerUserId(), plan.getId(), null,
					Map.of(), planData, commitDataList, List.of(), additionalContext);

			// Serialize full context so AI signals carry their input prompt for
			// auditability
			String contextJson;
			try {
				contextJson = objectMapper.writeValueAsString(context);
			} catch (JsonProcessingException e) {
				log.debug("Failed to serialize AI context for plan {}: {}", plan.getId(), e.getMessage());
				contextJson = "{}";
			}

			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context);
			if (!result.available() || result.payload() == null) {
				return List.of();
			}

			// Parse the LLM response, passing the serialized context as prompt
			return parseAiRiskSignals(result.payload(), contextJson);
		} catch (Exception ex) {
			log.debug("AI risk augmentation failed for plan {}: {}", plan.getId(), ex.getMessage());
			return List.of();
		}
	}

	/**
	 * Parses the LLM risk signal JSON into RawSignal records. Expects the format
	 * defined in risk-signal.txt: {@code {"signals": [{"signalType": ...,
	 * "commitId": ..., "severity": ..., "description": ..., "suggestedAction":
	 * ...}]}}.
	 */
	private List<RawSignal> parseAiRiskSignals(String payload, String contextJson) {
		List<RawSignal> result = new ArrayList<>();
		try {
			com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(payload);
			com.fasterxml.jackson.databind.JsonNode signalsNode = root.get("signals");
			if (signalsNode == null || !signalsNode.isArray()) {
				return result;
			}
			for (com.fasterxml.jackson.databind.JsonNode signal : signalsNode) {
				String signalType = signal.path("signalType").asText("AI_RISK_DETECTED");
				String description = signal.path("description").asText("");
				String severity = signal.path("severity").asText("MEDIUM");
				String suggestedAction = signal.path("suggestedAction").asText("");
				String commitIdStr = signal.path("commitId").asText(null);
				UUID commitId = null;
				if (commitIdStr != null && !"null".equals(commitIdStr) && !commitIdStr.isBlank()) {
					try {
						commitId = UUID.fromString(commitIdStr);
					} catch (IllegalArgumentException ignored) {
					}
				}

				String rationale = "[" + severity + "] " + description;
				if (!suggestedAction.isBlank()) {
					rationale += " → " + suggestedAction;
				}
				result.add(new RawSignal(signalType, commitId, rationale, contextJson));
			}
		} catch (Exception ex) {
			log.debug("Failed to parse AI risk signals: {}", ex.getMessage());
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private Instant findBlockedSince(UUID workItemId) {
		List<WorkItemStatusHistory> history = statusHistoryRepo.findByWorkItemIdOrderByCreatedAtAsc(workItemId);
		// Walk in reverse to find most recent BLOCKED transition
		Instant mostRecent = null;
		for (int i = history.size() - 1; i >= 0; i--) {
			if ("BLOCKED".equals(history.get(i).getToStatus())) {
				mostRecent = history.get(i).getCreatedAt();
				break;
			}
		}
		return mostRecent;
	}

	private String extractSignalType(String rationale, String payload) {
		// Try to extract from payload JSON
		try {
			com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
			com.fasterxml.jackson.databind.JsonNode typeNode = node.get("signalType");
			if (typeNode != null && !typeNode.isNull()) {
				return typeNode.asText();
			}
		} catch (JsonProcessingException ignored) {
		}
		// Fallback: parse from rationale prefix
		if (rationale != null && rationale.contains(": ")) {
			String after = rationale.substring(rationale.indexOf(": ") + 2);
			String[] tokens = after.trim().split("\\s+");
			if (tokens.length > 0 && tokens[0].matches("[A-Z_]+")) {
				return tokens[0];
			}
		}
		return "UNKNOWN";
	}

	/**
	 * Simple value object for an in-memory signal before persistence.
	 *
	 * <p>
	 * {@code prompt} holds the serialized {@link AiContext} for AI-generated
	 * signals so the full input context is persisted alongside the signal.
	 * Rules-based signals use {@code "{}"} since there is no LLM context.
	 */
	private record RawSignal(String type, UUID commitId, String rationale, String prompt) {
	}
}
