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
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.service.AuthorizationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
	private final ObjectMapper objectMapper;

	public RiskDetectionService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			WorkItemRepository workItemRepo, WorkItemStatusHistoryRepository statusHistoryRepo,
			ScopeChangeEventRepository scopeChangeRepo, AiSuggestionRepository suggestionRepo,
			AuthorizationService authService, ObjectMapper objectMapper) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.workItemRepo = workItemRepo;
		this.statusHistoryRepo = statusHistoryRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.suggestionRepo = suggestionRepo;
		this.authService = authService;
		this.objectMapper = objectMapper;
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
					+ " pts exceeds capacity budget of " + budget + " pts."));
		}

		// 2. UNDERCOMMIT
		if (budget > 0 && (double) totalPoints / budget < UNDERCOMMIT_THRESHOLD) {
			signals.add(new RawSignal("UNDERCOMMIT", null,
					STUB_RATIONALE_PREFIX + ": planned " + totalPoints + " pts is less than "
							+ (int) (UNDERCOMMIT_THRESHOLD * 100) + "% of capacity budget (" + budget + " pts)."));
		}

		// 3. REPEATED_CARRY_FORWARD
		for (WeeklyCommit commit : commits) {
			if (commit.getCarryForwardStreak() >= CARRY_FORWARD_STREAK_THRESHOLD) {
				signals.add(new RawSignal("REPEATED_CARRY_FORWARD", commit.getId(),
						STUB_RATIONALE_PREFIX + ": commit \"" + commit.getTitle() + "\" has been carried forward "
								+ commit.getCarryForwardStreak() + " times in a row (streak >= "
								+ CARRY_FORWARD_STREAK_THRESHOLD + ")."));
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
								+ " hours."));
			}
		}

		// 5. SCOPE_VOLATILITY
		long scopeChanges = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId()).size();
		if (scopeChanges > SCOPE_VOLATILITY_THRESHOLD) {
			signals.add(new RawSignal("SCOPE_VOLATILITY", null, STUB_RATIONALE_PREFIX + ": plan has " + scopeChanges
					+ " post-lock scope changes (threshold: " + SCOPE_VOLATILITY_THRESHOLD + ")."));
		}

		// Persist signals
		for (RawSignal signal : signals) {
			AiSuggestion s = new AiSuggestion();
			s.setSuggestionType("RISK_SIGNAL");
			s.setPlanId(plan.getId());
			s.setCommitId(signal.commitId());
			s.setUserId(plan.getOwnerUserId());
			s.setPrompt("{}");
			s.setRationale(signal.rationale());
			s.setSuggestionPayload("{\"signalType\":\"" + signal.type() + "\"}");
			s.setModelVersion(MODEL_VERSION);
			suggestionRepo.save(s);
		}

		log.debug("Risk detection for plan {}: {} signal(s) stored", plan.getId(), signals.size());
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

	/** Simple value object for an in-memory signal before persistence. */
	private record RawSignal(String type, UUID commitId, String rationale) {
	}
}
