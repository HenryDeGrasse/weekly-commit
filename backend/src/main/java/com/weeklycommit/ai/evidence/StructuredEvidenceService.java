package com.weeklycommit.ai.evidence;

import com.weeklycommit.ai.evidence.StructuredEvidence.LineageChain;
import com.weeklycommit.ai.evidence.StructuredEvidence.RiskFeatures;
import com.weeklycommit.ai.evidence.StructuredEvidence.SemanticMatch;
import com.weeklycommit.ai.evidence.StructuredEvidence.SqlFacts;
import com.weeklycommit.ai.rag.PineconeClient;
import com.weeklycommit.ai.rag.PineconeClient.PineconeMatch;
import com.weeklycommit.ai.rag.EmbeddingService;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles structured evidence bundles from four retrieval strategies:
 *
 * <ol>
 * <li><strong>SQL facts</strong> — exact states, counts, and diffs from
 * transactional + read-model tables</li>
 * <li><strong>Lineage</strong> — carry-forward chains, RCDO ancestry, and
 * scope-change timelines via recursive CTEs</li>
 * <li><strong>Semantic evidence</strong> — relevant text chunks from Pinecone
 * vector retrieval</li>
 * <li><strong>Risk features</strong> — pre-computed features from derived
 * read-model tables</li>
 * </ol>
 *
 * <p>
 * The resulting {@link StructuredEvidence} bundle is the only context the LLM
 * receives — it never invents facts from its own knowledge.
 */
@Service
@Transactional(readOnly = true)
public class StructuredEvidenceService {

	private static final Logger log = LoggerFactory.getLogger(StructuredEvidenceService.class);
	private static final int SEMANTIC_TOP_K = 10;

	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final UserAccountRepository userRepo;
	private final TeamRepository teamRepo;
	private final UserWeekFactRepository userWeekFactRepo;
	private final AiSuggestionRepository suggestionRepo;
	private final LineageQueryService lineageQueryService;

	@Autowired(required = false)
	private PineconeClient pineconeClient;

	@Autowired(required = false)
	private EmbeddingService embeddingService;

	public StructuredEvidenceService(WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			UserAccountRepository userRepo, TeamRepository teamRepo, UserWeekFactRepository userWeekFactRepo,
			AiSuggestionRepository suggestionRepo, LineageQueryService lineageQueryService) {
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.userRepo = userRepo;
		this.teamRepo = teamRepo;
		this.userWeekFactRepo = userWeekFactRepo;
		this.suggestionRepo = suggestionRepo;
		this.lineageQueryService = lineageQueryService;
	}

	/**
	 * Assembles a full evidence bundle for a specific plan.
	 *
	 * @param planId
	 *            the plan to gather evidence for
	 * @param question
	 *            optional natural-language question for semantic retrieval
	 *            relevance; pass {@code null} for non-RAG use cases
	 * @return structured evidence bundle, never {@code null}
	 */
	public StructuredEvidence gatherForPlan(UUID planId, String question) {
		if (planId == null) {
			return StructuredEvidence.empty();
		}

		Optional<WeeklyPlan> planOpt = planRepo.findById(planId);
		if (planOpt.isEmpty()) {
			return StructuredEvidence.empty();
		}

		WeeklyPlan plan = planOpt.get();
		// Query commits once and share across all builders to avoid redundant DB hits.
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		SqlFacts sqlFacts = buildSqlFacts(plan, commits);
		LineageChain lineage = buildLineage(commits);
		List<SemanticMatch> semanticMatches = retrieveSemanticEvidence(plan, question);
		RiskFeatures riskFeatures = buildRiskFeatures(plan, commits);

		return new StructuredEvidence(sqlFacts, lineage, semanticMatches, riskFeatures);
	}

	/**
	 * Assembles evidence for a specific commit (e.g., for commit-level drilldown).
	 *
	 * @param commitId
	 *            the commit to investigate
	 * @param question
	 *            optional question for semantic retrieval
	 * @return structured evidence bundle
	 */
	public StructuredEvidence gatherForCommit(UUID commitId, String question) {
		if (commitId == null) {
			return StructuredEvidence.empty();
		}

		Optional<WeeklyCommit> commitOpt = commitRepo.findById(commitId);
		if (commitOpt.isEmpty()) {
			return StructuredEvidence.empty();
		}

		WeeklyCommit commit = commitOpt.get();
		Optional<WeeklyPlan> planOpt = planRepo.findById(commit.getPlanId());
		if (planOpt.isEmpty()) {
			return StructuredEvidence.empty();
		}

		WeeklyPlan plan = planOpt.get();
		// Query commits once and share across all builders to avoid redundant DB hits.
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		SqlFacts sqlFacts = buildSqlFacts(plan, commits);
		LineageChain lineage = lineageQueryService.traceLineage(commitId);
		List<SemanticMatch> semanticMatches = retrieveSemanticEvidence(plan, question);
		RiskFeatures riskFeatures = buildRiskFeatures(plan, commits);

		return new StructuredEvidence(sqlFacts, lineage, semanticMatches, riskFeatures);
	}

	// ── SQL facts assembly ───────────────────────────────────────────────

	private SqlFacts buildSqlFacts(WeeklyPlan plan, List<WeeklyCommit> commits) {
		try {
			String userDisplayName = "";
			String teamName = "";
			if (plan.getOwnerUserId() != null) {
				userDisplayName = userRepo.findById(plan.getOwnerUserId()).map(UserAccount::getDisplayName).orElse("");
			}
			if (plan.getTeamId() != null) {
				teamName = teamRepo.findById(plan.getTeamId()).map(Team::getName).orElse("");
			}

			int totalPlanned = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
					.sum();
			int totalAchieved = commits.stream()
					.filter(c -> c.getOutcome() != null && "ACHIEVED".equals(c.getOutcome().name()))
					.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum();

			Map<String, Integer> chessDistribution = commits.stream().filter(c -> c.getChessPiece() != null)
					.collect(Collectors.groupingBy(c -> c.getChessPiece().name(), Collectors.summingInt(c -> 1)));

			int cfCount = (int) commits.stream().filter(c -> c.getCarryForwardStreak() > 0).count();

			// Scope change count from the fact table if available
			int scopeChangeCount = 0;
			boolean lockCompliance = false;
			boolean reconcileCompliance = false;
			if (plan.getOwnerUserId() != null && plan.getWeekStartDate() != null) {
				UserWeekFact fact = userWeekFactRepo
						.findByUserIdAndWeekStart(plan.getOwnerUserId(), plan.getWeekStartDate()).orElse(null);
				if (fact != null) {
					scopeChangeCount = fact.getScopeChangeCount();
					lockCompliance = fact.isLockCompliance();
					reconcileCompliance = fact.isReconcileCompliance();
				}
			}

			return new SqlFacts(plan.getOwnerUserId(), userDisplayName, plan.getTeamId(), teamName, plan.getId(),
					plan.getWeekStartDate(), plan.getState() != null ? plan.getState().name() : null,
					plan.getCapacityBudgetPoints(), totalPlanned, totalAchieved, commits.size(), cfCount,
					scopeChangeCount, lockCompliance, reconcileCompliance, chessDistribution);
		} catch (Exception e) {
			log.warn("StructuredEvidenceService: buildSqlFacts failed — {}", e.getMessage());
			return null;
		}
	}

	// ── Lineage assembly ─────────────────────────────────────────────────

	private LineageChain buildLineage(List<WeeklyCommit> commits) {
		try {
			// Find the first commit with carry-forward history
			for (WeeklyCommit commit : commits) {
				if (commit.getCarryForwardStreak() > 0) {
					LineageChain chain = lineageQueryService.traceLineage(commit.getId());
					if (chain != null) {
						return chain;
					}
				}
			}
			return null;
		} catch (Exception e) {
			log.warn("StructuredEvidenceService: buildLineage failed — {}", e.getMessage());
			return null;
		}
	}

	// ── Semantic retrieval ───────────────────────────────────────────────

	private List<SemanticMatch> retrieveSemanticEvidence(WeeklyPlan plan, String question) {
		if (pineconeClient == null || embeddingService == null || !pineconeClient.isAvailable()
				|| !embeddingService.isAvailable()) {
			return List.of();
		}
		if (question == null || question.isBlank()) {
			// Build a default query from the plan context
			question = "weekly plan commits and outcomes for week " + plan.getWeekStartDate();
		}
		try {
			float[] vector = embeddingService.embed(question);
			if (vector.length == 0) {
				return List.of();
			}

			String namespace = plan.getTeamId() != null
					? teamRepo.findById(plan.getTeamId()).map(t -> t.getOrganizationId())
							.map(orgId -> orgId != null ? orgId.toString() : "").orElse("")
					: "";

			Map<String, Object> filter = new HashMap<>();
			if (plan.getTeamId() != null) {
				filter.put("teamId", plan.getTeamId().toString());
			}

			List<PineconeMatch> matches = pineconeClient.query(namespace, vector, SEMANTIC_TOP_K,
					filter.isEmpty() ? null : filter);

			return matches.stream()
					.map(m -> new SemanticMatch(m.metadata().getOrDefault("entityType", "").toString(),
							m.metadata().getOrDefault("entityId", "").toString(), m.score(),
							m.metadata().getOrDefault("weekStartDate", "").toString(),
							m.metadata().getOrDefault("text", "").toString()))
					.toList();

		} catch (Exception e) {
			log.warn("StructuredEvidenceService: semantic retrieval failed — {}", e.getMessage());
			return List.of();
		}
	}

	// ── Risk features assembly ───────────────────────────────────────────

	private RiskFeatures buildRiskFeatures(WeeklyPlan plan, List<WeeklyCommit> commits) {
		try {
			UUID userId = plan.getOwnerUserId();
			LocalDate weekStart = plan.getWeekStartDate();
			if (userId == null || weekStart == null) {
				return null;
			}

			// Current week fact
			UserWeekFact current = userWeekFactRepo.findByUserIdAndWeekStart(userId, weekStart).orElse(null);

			// Historical completion ratios (last 4 weeks)
			LocalDate fourWeeksAgo = weekStart.minusWeeks(4);
			List<UserWeekFact> history = userWeekFactRepo.findByUserIdAndWeekStartBetween(userId, fourWeeksAgo,
					weekStart.minusWeeks(1));

			double completionRatio = 0.0;
			if (current != null && current.getTotalPlannedPoints() > 0) {
				completionRatio = (double) current.getTotalAchievedPoints() / current.getTotalPlannedPoints();
			}

			double avgCompletion4w = history.stream().filter(f -> f.getTotalPlannedPoints() > 0)
					.mapToDouble(f -> (double) f.getTotalAchievedPoints() / f.getTotalPlannedPoints()).average()
					.orElse(0.0);

			// Max carry-forward streak from current plan — uses pre-fetched commits
			int maxCfStreak = commits.stream().mapToInt(WeeklyCommit::getCarryForwardStreak).max().orElse(0);
			int kingCount = (int) commits.stream().filter(c -> c.getChessPiece() == ChessPiece.KING).count();
			int queenCount = (int) commits.stream().filter(c -> c.getChessPiece() == ChessPiece.QUEEN).count();

			// Active risk signals
			List<AiSuggestion> riskSignals = suggestionRepo.findByPlanIdAndSuggestionType(plan.getId(), "RISK_SIGNAL");
			List<String> activeRiskTypes = riskSignals.stream().map(s -> extractSignalType(s.getSuggestionPayload()))
					.filter(t -> t != null && !t.isBlank()).toList();

			int scopeChangeCount = current != null ? current.getScopeChangeCount() : 0;

			return new RiskFeatures(completionRatio, avgCompletion4w, maxCfStreak, scopeChangeCount, kingCount,
					queenCount, activeRiskTypes);
		} catch (Exception e) {
			log.warn("StructuredEvidenceService: buildRiskFeatures failed — {}", e.getMessage());
			return null;
		}
	}

	private String extractSignalType(String payload) {
		if (payload == null) {
			return null;
		}
		try {
			int idx = payload.indexOf("\"signalType\"");
			if (idx < 0) {
				return null;
			}
			int colon = payload.indexOf(":", idx);
			int quote1 = payload.indexOf("\"", colon + 1);
			int quote2 = payload.indexOf("\"", quote1 + 1);
			if (quote1 >= 0 && quote2 > quote1) {
				return payload.substring(quote1 + 1, quote2);
			}
		} catch (Exception ignored) {
		}
		return null;
	}
}
