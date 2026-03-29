package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.CarryForwardFact;
import com.weeklycommit.domain.entity.ComplianceFact;
import com.weeklycommit.domain.entity.RcdoWeekRollup;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.TeamWeekRollup;
import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.repository.CarryForwardFactRepository;
import com.weeklycommit.domain.repository.ComplianceFactRepository;
import com.weeklycommit.domain.repository.RcdoWeekRollupRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamWeekRollupRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes analytical and compliance questions to pre-computed read model tables
 * instead of the vector search path.
 *
 * <p>
 * This avoids costly semantic retrieval for questions that can be answered
 * directly from aggregate fact tables (e.g. team-week rollups, compliance
 * facts).
 *
 * <p>
 * The {@link #canHandle} method is a fast gate check. Callers should call it
 * first and fall back to the vector path when it returns {@code false}.
 */
@Service
public class SqlQueryRouter {

	private static final Logger log = LoggerFactory.getLogger(SqlQueryRouter.class);

	/** Aggregation keywords that indicate an analytical (non-vector) query. */
	private static final Set<String> AGGREGATION_KEYWORDS = Set.of("total", "average", "most", "least", "how many",
			"percentage", "rate", "trend", "compare", "ranking", "count", "sum", "ratio", "highest", "lowest", "across",
			"breakdown");

	private final TeamMembershipRepository teamMembershipRepo;
	private final UserWeekFactRepository userWeekFactRepo;
	private final TeamWeekRollupRepository teamWeekRollupRepo;
	private final RcdoWeekRollupRepository rcdoWeekRollupRepo;
	private final CarryForwardFactRepository carryForwardFactRepo;
	private final ComplianceFactRepository complianceFactRepo;
	private final AiProviderRegistry aiProviderRegistry;
	private final ObjectMapper objectMapper;

	public SqlQueryRouter(TeamMembershipRepository teamMembershipRepo, UserWeekFactRepository userWeekFactRepo,
			TeamWeekRollupRepository teamWeekRollupRepo, RcdoWeekRollupRepository rcdoWeekRollupRepo,
			CarryForwardFactRepository carryForwardFactRepo, ComplianceFactRepository complianceFactRepo,
			AiProviderRegistry aiProviderRegistry, ObjectMapper objectMapper) {
		this.teamMembershipRepo = teamMembershipRepo;
		this.userWeekFactRepo = userWeekFactRepo;
		this.teamWeekRollupRepo = teamWeekRollupRepo;
		this.rcdoWeekRollupRepo = rcdoWeekRollupRepo;
		this.carryForwardFactRepo = carryForwardFactRepo;
		this.complianceFactRepo = complianceFactRepo;
		this.aiProviderRegistry = aiProviderRegistry;
		this.objectMapper = objectMapper;
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Returns {@code true} when this router can handle the question without vector
	 * search.
	 *
	 * <p>
	 * Handles:
	 * <ul>
	 * <li>{@code "analytical"} intent — always routed here</li>
	 * <li>{@code "compliance_query"} intent — always routed here</li>
	 * <li>{@code "status_query"} intent with aggregation keywords</li>
	 * </ul>
	 *
	 * @param intent
	 *            classified intent string (may be null)
	 * @param keywords
	 *            extracted keywords list (may be null)
	 */
	public boolean canHandle(String intent, List<String> keywords) {
		if ("analytical".equals(intent) || "compliance_query".equals(intent)) {
			return true;
		}
		if ("status_query".equals(intent) && keywords != null) {
			String combined = keywords.stream().map(String::toLowerCase).collect(Collectors.joining(" "));
			for (String agg : AGGREGATION_KEYWORDS) {
				if (combined.contains(agg)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Executes an analytical query against the read model tables and synthesises a
	 * natural-language answer via the LLM.
	 *
	 * @param question
	 *            the user's natural-language question
	 * @param intent
	 *            classified intent (e.g. {@code "analytical"})
	 * @param keywords
	 *            extracted keywords for data-source selection
	 * @param teamId
	 *            scoping team UUID (may be null for cross-team queries)
	 * @param timeRangeFrom
	 *            ISO date string for the range start (may be null)
	 * @param timeRangeTo
	 *            ISO date string for the range end (may be null)
	 * @param userId
	 *            requesting user UUID (for audit)
	 * @return a {@link SqlQueryResult}, or an unavailable result on failure
	 */
	public SqlQueryResult query(String question, String intent, List<String> keywords, UUID teamId,
			String timeRangeFrom, String timeRangeTo, UUID userId) {

		try {
			LocalDate from = parseDate(timeRangeFrom);
			LocalDate to = parseDate(timeRangeTo);

			// Resolve team members for per-user fact queries
			List<UUID> memberUserIds = resolveTeamMemberIds(teamId);

			// Gather relevant read model data
			Map<String, Object> readModelData = gatherReadModelData(intent, keywords, teamId, memberUserIds, from, to);

			if (readModelData.isEmpty()) {
				log.debug("SqlQueryRouter: no read model data found for question='{}'", question);
				return SqlQueryResult.unavailable();
			}

			// Build AI context with read model data as additional context
			Map<String, Object> additionalContext = new LinkedHashMap<>();
			additionalContext.put("question", question);
			additionalContext.put("readModelData", readModelData);

			AiContext context = new AiContext(AiContext.TYPE_SQL_SYNTHESIS, userId, null, null, Map.of(), Map.of(),
					java.util.List.of(), java.util.List.of(), additionalContext);

			AiSuggestionResult result = aiProviderRegistry.generateSuggestion(context, userId);
			if (!result.available()) {
				log.debug("SqlQueryRouter: AI provider unavailable for question='{}'", question);
				return SqlQueryResult.unavailable();
			}

			return parseSqlQueryResult(result);

		} catch (Exception e) {
			log.warn("SqlQueryRouter: failed to execute query for question='{}': {}", question, e.getMessage());
			return SqlQueryResult.unavailable();
		}
	}

	// ── Private helpers ──────────────────────────────────────────────────

	private List<UUID> resolveTeamMemberIds(UUID teamId) {
		if (teamId == null) {
			return List.of();
		}
		return teamMembershipRepo.findByTeamId(teamId).stream().map(TeamMembership::getUserId)
				.collect(Collectors.toList());
	}

	private Map<String, Object> gatherReadModelData(String intent, List<String> keywords, UUID teamId,
			List<UUID> memberUserIds, LocalDate from, LocalDate to) {

		Map<String, Object> data = new LinkedHashMap<>();

		boolean isComplianceFocused = "compliance_query".equals(intent)
				|| containsKeyword(keywords, "compliance", "locked", "lock", "reconcil");
		boolean isCarryForwardFocused = containsKeyword(keywords, "carry", "forward", "streak");
		boolean isRcdoFocused = containsKeyword(keywords, "rcdo", "rally cry", "objective", "outcome", "node");
		boolean isTeamRollupFocused = "analytical".equals(intent)
				|| containsKeyword(keywords, "team", "total", "average", "rate", "trend", "compare");

		// Team-level rollups
		if (teamId != null && isTeamRollupFocused) {
			List<TeamWeekRollup> rollups = fetchTeamRollups(teamId, from, to);
			if (!rollups.isEmpty()) {
				data.put("teamWeekRollups", rollups.stream().map(this::rollupToMap).collect(Collectors.toList()));
			}
		}

		// Per-user week facts
		if (!memberUserIds.isEmpty() && (isTeamRollupFocused || isComplianceFocused)) {
			List<UserWeekFact> facts = fetchUserWeekFacts(memberUserIds, from, to);
			if (!facts.isEmpty()) {
				data.put("userWeekFacts", facts.stream().map(this::userWeekFactToMap).collect(Collectors.toList()));
			}
		}

		// Carry-forward facts
		if (isCarryForwardFocused) {
			List<CarryForwardFact> cfFacts = fetchCarryForwardFacts(from, to);
			if (!cfFacts.isEmpty()) {
				data.put("carryForwardFacts",
						cfFacts.stream().map(this::carryForwardFactToMap).collect(Collectors.toList()));
			}
		}

		// RCDO rollups for node/objective/outcome analytical questions
		if (isRcdoFocused) {
			List<RcdoWeekRollup> rcdoRollups = fetchRcdoWeekRollups(teamId, from, to);
			if (!rcdoRollups.isEmpty()) {
				data.put("rcdoWeekRollups",
						rcdoRollups.stream().map(this::rcdoWeekRollupToMap).collect(Collectors.toList()));
			}
		}

		// Compliance facts
		if (isComplianceFocused && !memberUserIds.isEmpty()) {
			List<ComplianceFact> compFacts = fetchComplianceFacts(memberUserIds, from, to);
			if (!compFacts.isEmpty()) {
				data.put("complianceFacts",
						compFacts.stream().map(this::complianceFactToMap).collect(Collectors.toList()));
			}
		}

		return data;
	}

	private boolean containsKeyword(List<String> keywords, String... targets) {
		if (keywords == null) {
			return false;
		}
		String combined = keywords.stream().map(String::toLowerCase).collect(Collectors.joining(" "));
		for (String target : targets) {
			if (combined.contains(target.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private List<TeamWeekRollup> fetchTeamRollups(UUID teamId, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(teamId, from, to);
		}
		// Fall back to last 12 weeks if no time range
		LocalDate defaultTo = LocalDate.now();
		LocalDate defaultFrom = defaultTo.minusWeeks(12);
		return teamWeekRollupRepo.findByTeamIdAndWeekStartBetween(teamId, defaultFrom, defaultTo);
	}

	private List<UserWeekFact> fetchUserWeekFacts(List<UUID> memberIds, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return userWeekFactRepo.findByUserIdInAndWeekStartBetween(memberIds, from, to);
		}
		return userWeekFactRepo.findByUserIdIn(memberIds);
	}

	private List<CarryForwardFact> fetchCarryForwardFacts(LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return carryForwardFactRepo.findByCurrentWeekBetween(from, to);
		}
		return carryForwardFactRepo.findAll();
	}

	private List<ComplianceFact> fetchComplianceFacts(List<UUID> memberIds, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return complianceFactRepo.findByUserIdInAndWeekStartBetween(memberIds, from, to);
		}
		// No all-members query available — return empty for safety
		return new ArrayList<>();
	}

	private List<RcdoWeekRollup> fetchRcdoWeekRollups(UUID teamId, LocalDate from, LocalDate to) {
		LocalDate effectiveTo = to != null ? to : LocalDate.now();
		LocalDate effectiveFrom = from != null ? from : effectiveTo.minusWeeks(12);
		return rcdoWeekRollupRepo.findAll().stream()
				.filter(r -> r.getWeekStart() != null && !r.getWeekStart().isBefore(effectiveFrom)
						&& !r.getWeekStart().isAfter(effectiveTo))
				.filter(r -> teamId == null || includesTeamContribution(r.getTeamContributionBreakdown(), teamId))
				.collect(Collectors.toList());
	}

	private boolean includesTeamContribution(String teamContributionBreakdown, UUID teamId) {
		if (teamId == null) {
			return true;
		}
		if (teamContributionBreakdown == null || teamContributionBreakdown.isBlank()) {
			return false;
		}
		try {
			JsonNode breakdown = objectMapper.readTree(teamContributionBreakdown);
			return breakdown.has(teamId.toString());
		} catch (Exception e) {
			log.debug("SqlQueryRouter: could not parse teamContributionBreakdown for team {}: {}", teamId,
					e.getMessage());
			return teamContributionBreakdown.contains(teamId.toString());
		}
	}

	private LocalDate parseDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(dateStr);
		} catch (Exception e) {
			log.debug("SqlQueryRouter: could not parse date '{}': {}", dateStr, e.getMessage());
			return null;
		}
	}

	private SqlQueryResult parseSqlQueryResult(AiSuggestionResult result) {
		try {
			JsonNode payload = objectMapper.readTree(result.payload());
			String answer = payload.path("answer").asText("");
			String dataSource = payload.path("dataSource").asText("read models");
			double confidence = 0.8;
			JsonNode confNode = payload.get("confidence");
			if (confNode != null && confNode.isNumber()) {
				double parsed = confNode.asDouble();
				if (parsed >= 0.0 && parsed <= 1.0) {
					confidence = parsed;
				}
			}
			if (answer.isBlank()) {
				return SqlQueryResult.unavailable();
			}
			return new SqlQueryResult(true, answer, dataSource, confidence, result.modelVersion());
		} catch (Exception e) {
			log.warn("SqlQueryRouter: failed to parse LLM result: {}", e.getMessage());
			return SqlQueryResult.unavailable();
		}
	}

	// ── Entity → map helpers ─────────────────────────────────────────────

	private Map<String, Object> rollupToMap(TeamWeekRollup r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("weekStart", r.getWeekStart() != null ? r.getWeekStart().toString() : null);
		m.put("memberCount", r.getMemberCount());
		m.put("lockedCount", r.getLockedCount());
		m.put("reconciledCount", r.getReconciledCount());
		m.put("totalPlannedPoints", r.getTotalPlannedPoints());
		m.put("totalAchievedPoints", r.getTotalAchievedPoints());
		m.put("avgCarryForwardRate", r.getAvgCarryForwardRate());
		m.put("exceptionCount", r.getExceptionCount());
		return m;
	}

	private Map<String, Object> userWeekFactToMap(UserWeekFact f) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("userId", f.getUserId() != null ? f.getUserId().toString() : null);
		m.put("weekStart", f.getWeekStart() != null ? f.getWeekStart().toString() : null);
		m.put("planState", f.getPlanState());
		m.put("lockCompliance", f.isLockCompliance());
		m.put("reconcileCompliance", f.isReconcileCompliance());
		m.put("totalPlannedPoints", f.getTotalPlannedPoints());
		m.put("totalAchievedPoints", f.getTotalAchievedPoints());
		m.put("commitCount", f.getCommitCount());
		m.put("carryForwardCount", f.getCarryForwardCount());
		m.put("kingCount", f.getKingCount());
		m.put("queenCount", f.getQueenCount());
		return m;
	}

	private Map<String, Object> carryForwardFactToMap(CarryForwardFact f) {
		Map<String, Object> m = new HashMap<>();
		m.put("commitId", f.getCommitId() != null ? f.getCommitId().toString() : null);
		m.put("streakLength", f.getStreakLength());
		m.put("chessPiece", f.getChessPiece());
		m.put("sourceWeek", f.getSourceWeek() != null ? f.getSourceWeek().toString() : null);
		m.put("currentWeek", f.getCurrentWeek() != null ? f.getCurrentWeek().toString() : null);
		return m;
	}

	private Map<String, Object> complianceFactToMap(ComplianceFact f) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("userId", f.getUserId() != null ? f.getUserId().toString() : null);
		m.put("weekStart", f.getWeekStart() != null ? f.getWeekStart().toString() : null);
		m.put("lockOnTime", f.isLockOnTime());
		m.put("lockLate", f.isLockLate());
		m.put("autoLocked", f.isAutoLocked());
		m.put("reconcileOnTime", f.isReconcileOnTime());
		m.put("reconcileLate", f.isReconcileLate());
		m.put("reconcileMissed", f.isReconcileMissed());
		return m;
	}

	private Map<String, Object> rcdoWeekRollupToMap(RcdoWeekRollup r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("rcdoNodeId", r.getRcdoNodeId() != null ? r.getRcdoNodeId().toString() : null);
		m.put("weekStart", r.getWeekStart() != null ? r.getWeekStart().toString() : null);
		m.put("plannedPoints", r.getPlannedPoints());
		m.put("achievedPoints", r.getAchievedPoints());
		m.put("commitCount", r.getCommitCount());
		m.put("teamContributionBreakdown", r.getTeamContributionBreakdown());
		return m;
	}

	// ── Inner record ─────────────────────────────────────────────────────

	/**
	 * Result of an analytical SQL-routing query.
	 *
	 * @param available
	 *            {@code false} if the provider was unavailable or data was
	 *            insufficient
	 * @param answer
	 *            synthesised natural-language answer
	 * @param dataSource
	 *            name of the primary read model(s) used
	 * @param confidence
	 *            LLM confidence in the answer [0.0, 1.0]
	 * @param suggestionId
	 *            model version identifier (for audit)
	 */
	public record SqlQueryResult(boolean available, String answer, String dataSource, double confidence,
			String suggestionId) {

		/** Convenience factory for a failed/degraded result. */
		public static SqlQueryResult unavailable() {
			return new SqlQueryResult(false, null, null, 0.0, null);
		}
	}
}
