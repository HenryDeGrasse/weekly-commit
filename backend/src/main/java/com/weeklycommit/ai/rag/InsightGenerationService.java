package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.CarryForwardLinkRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates proactive AI insights for teams and individual plans.
 *
 * <p>
 * Insights are derived by combining current-week structured data with
 * semantically similar historical context retrieved from Pinecone. The LLM
 * synthesises actionable recommendations and each insight is persisted as an
 * {@link com.weeklycommit.domain.entity.AiSuggestion} row so they can be
 * surfaced through the API.
 *
 * <p>
 * Two insight types are produced:
 * <ul>
 * <li>{@link AiContext#TYPE_TEAM_INSIGHT} — weekly team-level management
 * insights stored with {@code teamId + weekStartDate}.</li>
 * <li>{@link AiContext#TYPE_PERSONAL_INSIGHT} — individual plan insights stored
 * with {@code planId + userId + teamId + weekStartDate}.</li>
 * </ul>
 *
 * <p>
 * All methods degrade gracefully: if Pinecone / the LLM is unavailable the
 * method returns silently without failing the caller's transaction.
 */
@Service
public class InsightGenerationService {

	private static final Logger log = LoggerFactory.getLogger(InsightGenerationService.class);
	private static final int HISTORY_TOP_K = 8;

	private final PineconeClient pineconeClient;
	private final EmbeddingService embeddingService;
	private final AiProviderRegistry aiProviderRegistry;
	private final AiSuggestionService aiSuggestionService;
	private final AiSuggestionRepository suggestionRepo;
	private final TeamRepository teamRepo;
	private final TeamMembershipRepository membershipRepo;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final CarryForwardLinkRepository carryForwardLinkRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final ObjectMapper objectMapper;

	public InsightGenerationService(PineconeClient pineconeClient, EmbeddingService embeddingService,
			AiProviderRegistry aiProviderRegistry, AiSuggestionService aiSuggestionService,
			AiSuggestionRepository suggestionRepo, TeamRepository teamRepo, TeamMembershipRepository membershipRepo,
			WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			CarryForwardLinkRepository carryForwardLinkRepo, ScopeChangeEventRepository scopeChangeRepo,
			ObjectMapper objectMapper) {
		this.pineconeClient = pineconeClient;
		this.embeddingService = embeddingService;
		this.aiProviderRegistry = aiProviderRegistry;
		this.aiSuggestionService = aiSuggestionService;
		this.suggestionRepo = suggestionRepo;
		this.teamRepo = teamRepo;
		this.membershipRepo = membershipRepo;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.carryForwardLinkRepo = carryForwardLinkRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.objectMapper = objectMapper;
	}

	// ── Scheduled daily job ───────────────────────────────────────────────

	/**
	 * Daily insight generation — runs at 08:00 UTC. Iterates all teams and
	 * generates both team-level and personal insights for the current week. Acts as
	 * a catch-up sweep for plans whose on-lock insight generation was skipped (e.g.
	 * Pinecone was down).
	 */
	@Scheduled(cron = "0 0 8 * * *")
	@Transactional(readOnly = true)
	public void generateDailyInsights() {
		if (!pineconeClient.isAvailable() || !embeddingService.isAvailable()) {
			log.debug("InsightGenerationService: daily sweep skipped — dependencies unavailable");
			return;
		}
		LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
		List<Team> teams = teamRepo.findAll();
		log.info("InsightGenerationService: daily sweep for {} teams, week={}", teams.size(), weekStart);

		for (Team team : teams) {
			// Team-level insights
			try {
				generateTeamInsights(team.getId(), weekStart);
			} catch (Exception ex) {
				log.warn("InsightGenerationService: team insight failed for team {} — {}", team.getId(),
						ex.getMessage());
			}

			// Personal insights for LOCKED plans that don't already have insights
			List<WeeklyPlan> lockedPlans = planRepo.findByTeamIdAndWeekStartDate(team.getId(), weekStart).stream()
					.filter(p -> p.getState() == PlanState.LOCKED).toList();

			for (WeeklyPlan plan : lockedPlans) {
				List<com.weeklycommit.domain.entity.AiSuggestion> existing = suggestionRepo
						.findByPlanIdAndSuggestionType(plan.getId(), AiContext.TYPE_PERSONAL_INSIGHT);
				if (existing.isEmpty()) {
					try {
						generatePersonalInsights(plan.getId());
					} catch (Exception ex) {
						log.warn("InsightGenerationService: personal insight failed for plan {} — {}", plan.getId(),
								ex.getMessage());
					}
				}
			}
		}
	}

	// ── Public API ────────────────────────────────────────────────────────

	/**
	 * Generates team-level insights for the given team and week. Stores one
	 * {@link com.weeklycommit.domain.entity.AiSuggestion} row per insight with
	 * {@code teamId} and {@code weekStartDate} set for subsequent retrieval.
	 *
	 * @param teamId
	 *            target team
	 * @param weekStartDate
	 *            Monday of the target week
	 */
	public void generateTeamInsights(UUID teamId, LocalDate weekStartDate) {
		if (!pineconeClient.isAvailable() || !embeddingService.isAvailable()) {
			log.debug("InsightGenerationService: generateTeamInsights skipped — dependencies unavailable");
			return;
		}
		try {
			// Gather current week data
			List<TeamMembership> memberships = membershipRepo.findByTeamId(teamId);
			List<WeeklyPlan> plans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStartDate);
			List<WeeklyCommit> commits = plans.stream()
					.flatMap(plan -> commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()).stream()).toList();
			List<ScopeChangeEvent> scopeChanges = plans.stream()
					.flatMap(plan -> scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId()).stream()).toList();
			List<CarryForwardLink> carryForwards = commits.stream()
					.flatMap(commit -> carryForwardLinkRepo.findBySourceCommitId(commit.getId()).stream()).toList();
			List<Map<String, Object>> planSummaries = buildPlanSummaries(plans);
			String contextString = buildTeamContextString(teamId, weekStartDate, memberships, planSummaries,
					buildCommitSummaries(commits), buildScopeChangeSummaries(scopeChanges),
					buildCarryForwardSummaries(carryForwards));

			// Retrieve historical context from Pinecone
			float[] queryVector = embeddingService
					.embed("patterns in this team's last 4 weeks of planning and commitments");
			String namespace = resolveNamespace(teamId);
			Map<String, Object> filter = Map.of("teamId", teamId.toString());
			List<PineconeClient.PineconeMatch> history = queryVector.length > 0
					? pineconeClient.query(namespace, queryVector, HISTORY_TOP_K, filter)
					: List.of();

			// Build LLM context and generate insights
			AiContext ctx = new AiContext(AiContext.TYPE_TEAM_INSIGHT, null, null, null, Map.of(),
					Map.of("teamId", teamId.toString(), "weekStartDate", weekStartDate.toString()), planSummaries,
					List.of(), buildHistoricalAdditionalContext(contextString, history));
			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(ctx);
			if (!result.available()) {
				log.debug("InsightGenerationService: LLM unavailable for TEAM_INSIGHT teamId={}", teamId);
				return;
			}

			// Parse and persist each insight
			List<Map<String, Object>> insights = parseInsights(result.payload());
			for (Map<String, Object> insight : insights) {
				String insightJson = serializeInsight(insight);
				AiSuggestionResult perInsightResult = new AiSuggestionResult(true, insightJson,
						(String) insight.getOrDefault("insightText", "Team insight"), result.confidence(),
						result.modelVersion());
				aiSuggestionService.storeSuggestion(AiContext.TYPE_TEAM_INSIGHT, null, null, null, contextString,
						perInsightResult, teamId, weekStartDate);
			}
			log.info("InsightGenerationService: stored {} TEAM_INSIGHT(s) for teamId={}, week={}", insights.size(),
					teamId, weekStartDate);
		} catch (Exception e) {
			log.warn("InsightGenerationService: generateTeamInsights failed for teamId={} — {}", teamId,
					e.getMessage());
		}
	}

	/**
	 * Generates personal insights for the plan identified by {@code planId}. Stores
	 * one {@link com.weeklycommit.domain.entity.AiSuggestion} row per insight with
	 * {@code planId}, {@code userId}, {@code teamId}, and {@code weekStartDate}
	 * set.
	 *
	 * @param planId
	 *            the plan to analyse
	 */
	public void generatePersonalInsights(UUID planId) {
		if (!pineconeClient.isAvailable() || !embeddingService.isAvailable()) {
			log.debug("InsightGenerationService: generatePersonalInsights skipped — dependencies unavailable");
			return;
		}
		try {
			WeeklyPlan plan = planRepo.findById(planId).orElse(null);
			if (plan == null) {
				log.warn("InsightGenerationService: plan not found: {}", planId);
				return;
			}

			List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(planId);
			List<ScopeChangeEvent> scopeChanges = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId);
			List<CarryForwardLink> carryForwards = commits.stream()
					.flatMap(c -> carryForwardLinkRepo.findBySourceCommitId(c.getId()).stream()).toList();

			String contextString = buildPersonalContextString(plan, commits, scopeChanges, carryForwards);

			// Retrieve user's historical context
			float[] queryVector = embeddingService
					.embed("patterns for this user's recent weekly plan performance and commitments");
			String namespace = resolveNamespace(plan.getTeamId());
			Map<String, Object> filter = Map.of("userId", plan.getOwnerUserId().toString());
			List<PineconeClient.PineconeMatch> history = queryVector.length > 0
					? pineconeClient.query(namespace, queryVector, HISTORY_TOP_K, filter)
					: List.of();

			// Build LLM context and generate insights
			List<Map<String, Object>> commitSummaries = buildCommitSummaries(commits);
			AiContext ctx = new AiContext(AiContext.TYPE_PERSONAL_INSIGHT, plan.getOwnerUserId(), planId, null,
					Map.of(), Map.of("planId", planId.toString(), "weekStartDate", plan.getWeekStartDate().toString()),
					commitSummaries, List.of(), buildHistoricalAdditionalContext(contextString, history));
			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(ctx);
			if (!result.available()) {
				log.debug("InsightGenerationService: LLM unavailable for PERSONAL_INSIGHT planId={}", planId);
				return;
			}

			// Parse and persist each insight
			List<Map<String, Object>> insights = parseInsights(result.payload());
			for (Map<String, Object> insight : insights) {
				String insightJson = serializeInsight(insight);
				AiSuggestionResult perInsightResult = new AiSuggestionResult(true, insightJson,
						(String) insight.getOrDefault("insightText", "Personal insight"), result.confidence(),
						result.modelVersion());
				aiSuggestionService.storeSuggestion(AiContext.TYPE_PERSONAL_INSIGHT, plan.getOwnerUserId(), planId,
						null, contextString, perInsightResult, plan.getTeamId(), plan.getWeekStartDate());
			}
			log.info("InsightGenerationService: stored {} PERSONAL_INSIGHT(s) for planId={}", insights.size(), planId);
		} catch (Exception e) {
			log.warn("InsightGenerationService: generatePersonalInsights failed for planId={} — {}", planId,
					e.getMessage());
		}
	}

	/**
	 * Async wrapper around {@link #generatePersonalInsights(UUID)}. Called from
	 * {@link com.weeklycommit.lock.service.LockService} after a plan is locked so
	 * insight generation does not block the lock transaction.
	 *
	 * @param planId
	 *            the locked plan to analyse
	 */
	@Async
	public void generatePersonalInsightsAsync(UUID planId) {
		try {
			generatePersonalInsights(planId);
		} catch (Exception e) {
			log.warn("InsightGenerationService: async personal insight generation failed for planId={} — {}", planId,
					e.getMessage());
		}
	}

	// ── Private helpers ───────────────────────────────────────────────────

	private String resolveNamespace(UUID teamId) {
		if (teamId == null) {
			return "";
		}
		return teamRepo.findById(teamId).map(Team::getOrganizationId)
				.map(orgId -> orgId != null ? orgId.toString() : "").orElse("");
	}

	private List<Map<String, Object>> parseInsights(String payload) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			JsonNode insightsNode = root.path("insights");
			if (!insightsNode.isArray() || insightsNode.isEmpty()) {
				return List.of();
			}
			List<Map<String, Object>> result = new ArrayList<>(insightsNode.size());
			for (JsonNode node : insightsNode) {
				@SuppressWarnings("unchecked")
				Map<String, Object> insight = objectMapper.treeToValue(node, Map.class);
				result.add(insight);
			}
			return result;
		} catch (Exception e) {
			log.debug("InsightGenerationService: could not parse insights payload — {}", e.getMessage());
			return List.of();
		}
	}

	private String serializeInsight(Map<String, Object> insight) {
		try {
			return objectMapper.writeValueAsString(insight);
		} catch (Exception e) {
			return "{}";
		}
	}

	private List<Map<String, Object>> buildPlanSummaries(List<WeeklyPlan> plans) {
		return plans.stream().map(p -> {
			Map<String, Object> m = new HashMap<>();
			m.put("planId", p.getId().toString());
			m.put("ownerUserId", p.getOwnerUserId().toString());
			m.put("state", p.getState().name());
			m.put("weekStartDate", p.getWeekStartDate().toString());
			List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(p.getId());
			m.put("commitCount", commits.size());
			m.put("totalPoints",
					commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum());
			return m;
		}).toList();
	}

	private List<Map<String, Object>> buildCommitSummaries(List<WeeklyCommit> commits) {
		return commits.stream().map(c -> {
			Map<String, Object> m = new HashMap<>();
			m.put("id", c.getId().toString());
			m.put("title", c.getTitle());
			m.put("chessPiece", c.getChessPiece() != null ? c.getChessPiece().name() : "");
			m.put("estimatePoints", c.getEstimatePoints());
			m.put("outcome", c.getOutcome() != null ? c.getOutcome().name() : "");
			m.put("carryForwardStreak", c.getCarryForwardStreak());
			return m;
		}).toList();
	}

	private String buildTeamContextString(UUID teamId, LocalDate weekStartDate, List<TeamMembership> memberships,
			List<Map<String, Object>> summaries, List<Map<String, Object>> commitSummaries,
			List<Map<String, Object>> scopeChangeSummaries, List<Map<String, Object>> carryForwardSummaries) {
		try {
			Map<String, Object> ctx = new HashMap<>();
			ctx.put("teamId", teamId.toString());
			ctx.put("weekStartDate", weekStartDate.toString());
			ctx.put("teamMemberIds", memberships.stream().map(m -> m.getUserId().toString()).toList());
			ctx.put("plans", summaries);
			ctx.put("commits", commitSummaries);
			ctx.put("scopeChanges", scopeChangeSummaries);
			ctx.put("carryForwards", carryForwardSummaries);
			return objectMapper.writeValueAsString(ctx);
		} catch (Exception e) {
			return "{}";
		}
	}

	private String buildPersonalContextString(WeeklyPlan plan, List<WeeklyCommit> commits,
			List<ScopeChangeEvent> scopeChanges, List<CarryForwardLink> carryForwards) {
		try {
			Map<String, Object> ctx = new HashMap<>();
			ctx.put("planId", plan.getId().toString());
			ctx.put("userId", plan.getOwnerUserId().toString());
			ctx.put("teamId", plan.getTeamId().toString());
			ctx.put("weekStartDate", plan.getWeekStartDate().toString());
			ctx.put("state", plan.getState() != null ? plan.getState().name() : "");
			ctx.put("commits", buildCommitSummaries(commits));
			ctx.put("scopeChanges", buildScopeChangeSummaries(scopeChanges));
			ctx.put("carryForwards", buildCarryForwardSummaries(carryForwards));
			return objectMapper.writeValueAsString(ctx);
		} catch (Exception e) {
			return "{}";
		}
	}

	private List<Map<String, Object>> buildScopeChangeSummaries(List<ScopeChangeEvent> scopeChanges) {
		return scopeChanges.stream().map(e -> {
			Map<String, Object> m = new HashMap<>();
			m.put("id", e.getId().toString());
			m.put("planId", e.getPlanId() != null ? e.getPlanId().toString() : "");
			m.put("commitId", e.getCommitId() != null ? e.getCommitId().toString() : "");
			m.put("category", e.getCategory() != null ? e.getCategory().name() : "");
			m.put("reason", e.getReason());
			return m;
		}).toList();
	}

	private List<Map<String, Object>> buildCarryForwardSummaries(List<CarryForwardLink> carryForwards) {
		return carryForwards.stream().map(link -> {
			Map<String, Object> m = new HashMap<>();
			m.put("id", link.getId().toString());
			m.put("sourceCommitId", link.getSourceCommitId() != null ? link.getSourceCommitId().toString() : "");
			m.put("targetCommitId", link.getTargetCommitId() != null ? link.getTargetCommitId().toString() : "");
			m.put("reason", link.getReason() != null ? link.getReason().name() : "");
			m.put("reasonNotes", link.getReasonNotes() != null ? link.getReasonNotes() : "");
			return m;
		}).toList();
	}

	private Map<String, Object> buildHistoricalAdditionalContext(String contextString,
			List<PineconeClient.PineconeMatch> history) {
		List<Map<String, Object>> chunks = history.stream().map(m -> {
			Map<String, Object> chunk = new HashMap<>();
			chunk.put("id", m.id());
			chunk.put("score", m.score());
			chunk.put("metadata", m.metadata());
			return chunk;
		}).toList();
		return Map.of("currentContext", contextString, "historicalChunks", chunks);
	}
}
