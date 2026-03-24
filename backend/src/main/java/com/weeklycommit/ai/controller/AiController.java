package com.weeklycommit.ai.controller;

import com.weeklycommit.ai.dto.AiFeedbackRequest;
import com.weeklycommit.ai.dto.CommitDraftAssistRequest;
import com.weeklycommit.ai.dto.CommitDraftAssistResponse;
import com.weeklycommit.ai.dto.CommitLintRequest;
import com.weeklycommit.ai.dto.CommitLintResponse;
import com.weeklycommit.ai.dto.ManagerAiSummaryResponse;
import com.weeklycommit.ai.dto.RcdoSuggestRequest;
import com.weeklycommit.ai.dto.RcdoSuggestResponse;
import com.weeklycommit.ai.dto.ReconcileAssistRequest;
import com.weeklycommit.ai.dto.ReconcileAssistResponse;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.ai.service.CommitDraftAssistService;
import com.weeklycommit.ai.service.CommitLintService;
import com.weeklycommit.ai.service.ManagerAiSummaryService;
import com.weeklycommit.ai.service.RcdoSuggestService;
import com.weeklycommit.ai.service.RiskDetectionService;
import com.weeklycommit.ai.service.ReconcileAssistService;
import com.weeklycommit.ai.service.AiSuggestionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for AI-powered commit assistance, linting, RCDO suggestions,
 * risk detection, reconciliation assistance, and manager summaries.
 *
 * <p>
 * All endpoints degrade gracefully: when AI is disabled or unavailable the
 * response includes {@code aiAvailable: false} and no error is returned. Core
 * workflow is never blocked by AI unavailability.
 */
@RestController
public class AiController {

	private final CommitDraftAssistService draftAssistService;
	private final CommitLintService lintService;
	private final RcdoSuggestService rcdoSuggestService;
	private final RiskDetectionService riskDetectionService;
	private final ReconcileAssistService reconcileAssistService;
	private final ManagerAiSummaryService managerSummaryService;
	private final AiSuggestionService suggestionService;

	public AiController(CommitDraftAssistService draftAssistService, CommitLintService lintService,
			RcdoSuggestService rcdoSuggestService, RiskDetectionService riskDetectionService,
			ReconcileAssistService reconcileAssistService, ManagerAiSummaryService managerSummaryService,
			AiSuggestionService suggestionService) {
		this.draftAssistService = draftAssistService;
		this.lintService = lintService;
		this.rcdoSuggestService = rcdoSuggestService;
		this.riskDetectionService = riskDetectionService;
		this.reconcileAssistService = reconcileAssistService;
		this.managerSummaryService = managerSummaryService;
		this.suggestionService = suggestionService;
	}

	// -------------------------------------------------------------------------
	// Capability 1: Commit draft assistance
	// -------------------------------------------------------------------------

	/**
	 * Returns suggestions for improving a commit draft (title, description, success
	 * criteria, estimate). Suggestions are editable proposals — never auto-applied.
	 */
	@PostMapping("/api/ai/commit-draft-assist")
	public ResponseEntity<CommitDraftAssistResponse> commitDraftAssist(
			@Valid @RequestBody CommitDraftAssistRequest request) {
		return ResponseEntity.ok(draftAssistService.assist(request));
	}

	// -------------------------------------------------------------------------
	// Capability 2: Commit quality lint
	// -------------------------------------------------------------------------

	/**
	 * Runs commit quality lint for a plan. Returns {@code hardValidation} (blocks
	 * lock) and {@code softGuidance} (informational) messages.
	 */
	@PostMapping("/api/ai/commit-lint")
	public ResponseEntity<CommitLintResponse> commitLint(@Valid @RequestBody CommitLintRequest request) {
		return ResponseEntity.ok(lintService.lint(request));
	}

	// -------------------------------------------------------------------------
	// Capability 3: RCDO link suggestion
	// -------------------------------------------------------------------------

	/**
	 * Suggests a primary RCDO node for a commit draft. Only surfaced when
	 * confidence &ge; 0.7. Never auto-links.
	 */
	@PostMapping("/api/ai/rcdo-suggest")
	public ResponseEntity<RcdoSuggestResponse> rcdoSuggest(@Valid @RequestBody RcdoSuggestRequest request) {
		return ResponseEntity.ok(rcdoSuggestService.suggest(request));
	}

	// -------------------------------------------------------------------------
	// Capability 4: Risk signals
	// -------------------------------------------------------------------------

	/**
	 * Returns current risk signals for the given plan. Hidden from peers — only the
	 * plan owner and their manager should call this endpoint.
	 */
	@GetMapping("/api/plans/{id}/risk-signals")
	public ResponseEntity<PlanRiskSignals> getRiskSignals(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {
		if (callerId == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(riskDetectionService.getRiskSignals(id, callerId));
	}

	// -------------------------------------------------------------------------
	// Capability 5: Reconciliation assistance
	// -------------------------------------------------------------------------

	/**
	 * Returns reconciliation assistance: likely outcomes, draft summary, and
	 * carry-forward recommendations for a plan entering the reconcile phase.
	 */
	@PostMapping("/api/ai/reconcile-assist")
	public ResponseEntity<ReconcileAssistResponse> reconcileAssist(@Valid @RequestBody ReconcileAssistRequest request) {
		return ResponseEntity.ok(reconcileAssistService.assist(request));
	}

	// -------------------------------------------------------------------------
	// Capability 6: Manager team AI summary
	// -------------------------------------------------------------------------

	/**
	 * Returns the AI-generated team summary for the given week. Only accessible to
	 * managers.
	 */
	@GetMapping("/api/teams/{id}/week/{weekStart}/ai-summary")
	public ResponseEntity<ManagerAiSummaryResponse> getTeamAiSummary(@PathVariable UUID id,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {
		return ResponseEntity.ok(managerSummaryService.getSummary(id, weekStart, callerId));
	}

	// -------------------------------------------------------------------------
	// Feedback
	// -------------------------------------------------------------------------

	/**
	 * Records user feedback (ACCEPTED / DISMISSED / EDITED) on an AI suggestion.
	 */
	@PostMapping("/api/ai/feedback")
	public ResponseEntity<Void> recordFeedback(@Valid @RequestBody AiFeedbackRequest request) {
		suggestionService.recordFeedback(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}
}
