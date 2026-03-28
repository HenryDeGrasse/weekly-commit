package com.weeklycommit.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.AiFeedbackRequest;
import com.weeklycommit.ai.dto.StructuredEvidenceResponse;
import com.weeklycommit.ai.evidence.StructuredEvidence;
import com.weeklycommit.ai.evidence.StructuredEvidenceService;
import com.weeklycommit.ai.dto.CommitDraftAssistRequest;
import com.weeklycommit.ai.dto.CommitDraftAssistResponse;
import com.weeklycommit.ai.dto.CommitLintRequest;
import com.weeklycommit.ai.dto.CommitLintResponse;
import com.weeklycommit.ai.dto.InsightCardDto;
import com.weeklycommit.ai.dto.InsightListResponse;
import com.weeklycommit.ai.dto.ManagerAiSummaryResponse;
import com.weeklycommit.ai.dto.RagQueryRequest;
import com.weeklycommit.ai.dto.RagQueryResponse;
import com.weeklycommit.ai.dto.RagSourceDto;
import com.weeklycommit.ai.dto.RcdoSuggestRequest;
import com.weeklycommit.ai.dto.RcdoSuggestResponse;
import com.weeklycommit.ai.dto.ReconcileAssistRequest;
import com.weeklycommit.ai.dto.ReconcileAssistResponse;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProvider;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.rag.SemanticIndexService;
import com.weeklycommit.ai.rag.SemanticQueryService;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.ai.service.CommitDraftAssistService;
import com.weeklycommit.ai.service.CommitLintService;
import com.weeklycommit.ai.service.ManagerAiSummaryService;
import com.weeklycommit.ai.service.RcdoSuggestService;
import com.weeklycommit.ai.service.RiskDetectionService;
import com.weeklycommit.ai.service.ReconcileAssistService;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * risk detection, reconciliation assistance, manager summaries, RAG queries,
 * and proactive insight retrieval.
 *
 * <p>
 * All endpoints degrade gracefully: when AI is disabled or unavailable the
 * response includes {@code aiAvailable: false} and no error is returned. Core
 * workflow is never blocked by AI unavailability.
 */
@RestController
public class AiController {

	private static final Logger log = LoggerFactory.getLogger(AiController.class);

	private final CommitDraftAssistService draftAssistService;
	private final CommitLintService lintService;
	private final RcdoSuggestService rcdoSuggestService;
	private final RiskDetectionService riskDetectionService;
	private final ReconcileAssistService reconcileAssistService;
	private final ManagerAiSummaryService managerSummaryService;
	private final AiSuggestionService suggestionService;
	private final AiProviderRegistry providerRegistry;
	private final SemanticIndexService semanticIndexService;
	private final SemanticQueryService semanticQueryService;
	private final AiSuggestionRepository suggestionRepo;
	private final StructuredEvidenceService evidenceService;
	private final ObjectMapper objectMapper;

	public AiController(CommitDraftAssistService draftAssistService, CommitLintService lintService,
			RcdoSuggestService rcdoSuggestService, RiskDetectionService riskDetectionService,
			ReconcileAssistService reconcileAssistService, ManagerAiSummaryService managerSummaryService,
			AiSuggestionService suggestionService, AiProviderRegistry providerRegistry,
			SemanticIndexService semanticIndexService, SemanticQueryService semanticQueryService,
			AiSuggestionRepository suggestionRepo, StructuredEvidenceService evidenceService,
			ObjectMapper objectMapper) {
		this.draftAssistService = draftAssistService;
		this.lintService = lintService;
		this.rcdoSuggestService = rcdoSuggestService;
		this.riskDetectionService = riskDetectionService;
		this.reconcileAssistService = reconcileAssistService;
		this.managerSummaryService = managerSummaryService;
		this.suggestionService = suggestionService;
		this.providerRegistry = providerRegistry;
		this.semanticIndexService = semanticIndexService;
		this.semanticQueryService = semanticQueryService;
		this.suggestionRepo = suggestionRepo;
		this.evidenceService = evidenceService;
		this.objectMapper = objectMapper;
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
	// Capability 7: RAG query
	// -------------------------------------------------------------------------

	/**
	 * Executes a semantic (RAG) query against the team's indexed planning history.
	 * Returns an AI-generated answer with source citations.
	 *
	 * <p>
	 * Degrades gracefully: returns {@code {aiAvailable: false}} when Pinecone or
	 * the LLM is unavailable.
	 */
	@PostMapping("/api/ai/query")
	public ResponseEntity<RagQueryResponse> ragQuery(@Valid @RequestBody RagQueryRequest request) {
		try {
			SemanticQueryService.RagQueryResult result = semanticQueryService.query(request.question(),
					request.teamId(), request.userId());
			if (!result.available()) {
				return ResponseEntity.ok(RagQueryResponse.unavailable());
			}
			List<RagSourceDto> sources = result.sources().stream()
					.map(s -> new RagSourceDto(s.entityType(), s.entityId(), s.weekStartDate(), s.snippet())).toList();
			return ResponseEntity.ok(
					new RagQueryResponse(true, result.answer(), sources, result.confidence(), result.suggestionId()));
		} catch (Exception ex) {
			log.warn("RAG query failed: {}", ex.getMessage());
			return ResponseEntity.ok(RagQueryResponse.unavailable());
		}
	}

	// -------------------------------------------------------------------------
	// Capability 8: Team AI insights
	// -------------------------------------------------------------------------

	/**
	 * Returns persisted TEAM_INSIGHT rows for the given team and week.
	 *
	 * <p>
	 * Insights are generated by the scheduled job in
	 * {@link com.weeklycommit.ai.rag.InsightGenerationService}. This endpoint reads
	 * them back from the {@code ai_suggestion} table.
	 */
	@GetMapping("/api/teams/{id}/week/{weekStart}/ai-insights")
	public ResponseEntity<InsightListResponse> getTeamInsights(@PathVariable UUID id,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		try {
			List<AiSuggestion> rows = suggestionRepo.findByTeamIdAndWeekStartDateAndSuggestionType(id, weekStart,
					AiContext.TYPE_TEAM_INSIGHT);
			List<InsightCardDto> insights = parseInsightCards(rows);
			return ResponseEntity.ok(new InsightListResponse(true, insights));
		} catch (Exception ex) {
			log.warn("Failed to retrieve team insights for team={}, week={}: {}", id, weekStart, ex.getMessage());
			return ResponseEntity.ok(InsightListResponse.unavailable());
		}
	}

	// -------------------------------------------------------------------------
	// Capability 9: Personal (plan) AI insights
	// -------------------------------------------------------------------------

	/**
	 * Returns persisted PERSONAL_INSIGHT rows for the given plan.
	 *
	 * <p>
	 * Insights are generated on plan lock (via
	 * {@link com.weeklycommit.ai.rag.InsightGenerationService#generatePersonalInsightsAsync})
	 * and by the daily sweep.
	 */
	@GetMapping("/api/plans/{id}/ai-insights")
	public ResponseEntity<InsightListResponse> getPlanInsights(@PathVariable UUID id) {
		try {
			List<AiSuggestion> rows = suggestionRepo.findByPlanIdAndSuggestionType(id, AiContext.TYPE_PERSONAL_INSIGHT);
			List<InsightCardDto> insights = parseInsightCards(rows);
			return ResponseEntity.ok(new InsightListResponse(true, insights));
		} catch (Exception ex) {
			log.warn("Failed to retrieve personal insights for plan={}: {}", id, ex.getMessage());
			return ResponseEntity.ok(InsightListResponse.unavailable());
		}
	}

	// -------------------------------------------------------------------------
	// Admin: full reindex
	// -------------------------------------------------------------------------

	/**
	 * Triggers an async full reindex of all plans into Pinecone. Returns
	 * immediately; indexing runs in a background thread. Intended for dev/admin use
	 * after bulk SQL seed or Pinecone wipes.
	 */
	@PostMapping("/api/admin/ai/reindex")
	public ResponseEntity<java.util.Map<String, Object>> triggerFullReindex() {
		log.info("Admin full reindex triggered");
		int count = semanticIndexService.fullReindex();
		java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
		result.put("plansQueued", count);
		result.put("status", count > 0 ? "indexing_started" : "pinecone_unavailable");
		result.put("message",
				count > 0
						? "Indexing " + count + " plans in background. Check server logs for completion."
						: "Pinecone not configured.");
		return ResponseEntity.ok(result);
	}

	// -------------------------------------------------------------------------
	// Structured Evidence
	// -------------------------------------------------------------------------

	/**
	 * Returns a structured evidence bundle for a plan — SQL facts, carry-forward
	 * lineage, semantic matches, and risk features. Used by the frontend evidence
	 * drawer to show exactly what the AI used to produce its answers.
	 */
	@GetMapping("/api/plans/{planId}/evidence")
	public ResponseEntity<StructuredEvidenceResponse> getEvidence(@PathVariable UUID planId,
			@org.springframework.web.bind.annotation.RequestParam(required = false) String question) {
		try {
			StructuredEvidence evidence = evidenceService.gatherForPlan(planId, question);
			return ResponseEntity.ok(StructuredEvidenceResponse.of(evidence));
		} catch (Exception e) {
			log.warn("Evidence gathering failed for plan {}: {}", planId, e.getMessage());
			return ResponseEntity.ok(StructuredEvidenceResponse.unavailable());
		}
	}

	/**
	 * Returns a structured evidence bundle for a specific commit — includes
	 * carry-forward lineage tracing.
	 */
	@GetMapping("/api/commits/{commitId}/evidence")
	public ResponseEntity<StructuredEvidenceResponse> getCommitEvidence(@PathVariable UUID commitId,
			@org.springframework.web.bind.annotation.RequestParam(required = false) String question) {
		try {
			StructuredEvidence evidence = evidenceService.gatherForCommit(commitId, question);
			return ResponseEntity.ok(StructuredEvidenceResponse.of(evidence));
		} catch (Exception e) {
			log.warn("Evidence gathering failed for commit {}: {}", commitId, e.getMessage());
			return ResponseEntity.ok(StructuredEvidenceResponse.unavailable());
		}
	}

	// -------------------------------------------------------------------------
	// AI Status
	// -------------------------------------------------------------------------

	/**
	 * Returns the current AI provider status: enabled, provider name/version,
	 * availability. Useful for ops dashboards and frontend feature checks.
	 */
	@GetMapping("/api/ai/status")
	public ResponseEntity<java.util.Map<String, Object>> getAiStatus() {
		java.util.Optional<AiProvider> active = providerRegistry.getActiveProvider();
		java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
		status.put("aiEnabled", providerRegistry.isAiEnabled());
		status.put("providerName", active.map(AiProvider::getName).orElse("none"));
		status.put("providerVersion", active.map(AiProvider::getVersion).orElse("none"));
		status.put("available", active.map(AiProvider::isAvailable).orElse(false));
		return ResponseEntity.ok(status);
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

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	/**
	 * Parses each {@link AiSuggestion}'s {@code suggestionPayload} JSON into an
	 * {@link InsightCardDto}. Rows whose payload cannot be parsed are silently
	 * skipped.
	 */
	private List<InsightCardDto> parseInsightCards(List<AiSuggestion> rows) {
		List<InsightCardDto> result = new ArrayList<>(rows.size());
		for (AiSuggestion row : rows) {
			try {
				JsonNode root = objectMapper.readTree(row.getSuggestionPayload());
				String insightText = root.path("insightText").asText(row.getRationale());
				String severity = root.path("severity").asText("LOW");
				String actionSuggestion = root.path("actionSuggestion").asText("");
				List<String> sourceEntityIds = new ArrayList<>();
				JsonNode idsNode = root.path("sourceEntityIds");
				if (idsNode.isArray()) {
					for (JsonNode n : idsNode) {
						sourceEntityIds.add(n.asText());
					}
				}
				String createdAt = row.getCreatedAt() != null ? row.getCreatedAt().toString() : "";
				result.add(new InsightCardDto(row.getId(), insightText, severity, sourceEntityIds, actionSuggestion,
						createdAt));
			} catch (Exception ex) {
				log.debug("Skipping unparseable insight payload for suggestion {}: {}", row.getId(), ex.getMessage());
			}
		}
		return result;
	}
}
