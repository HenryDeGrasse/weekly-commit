package com.weeklycommit.ai.rag;

import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.repository.CarryForwardLinkRepository;
import com.weeklycommit.domain.repository.ManagerCommentRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates embedding and vector-store indexing for domain entities.
 *
 * <p>
 * Each public mutating method is annotated {@link Async} so indexing calls
 * placed at the end of write-path services do not block HTTP response latency.
 * All methods guard on {@link PineconeClient#isAvailable()} and catch all
 * exceptions, ensuring the RAG pipeline never disrupts the primary write path.
 *
 * <p>
 * Vectors are partitioned into Pinecone namespaces by organisation ID, derived
 * by traversing the entity → team → organisation chain.
 */
@Service
public class SemanticIndexService {

	private static final Logger log = LoggerFactory.getLogger(SemanticIndexService.class);

	// ── Entity type constants ─────────────────────────────────────────────
	public static final String TYPE_COMMIT = "commit";
	public static final String TYPE_SCOPE_CHANGE = "scope_change";
	public static final String TYPE_CARRY_FORWARD = "carry_forward";
	public static final String TYPE_MANAGER_COMMENT = "manager_comment";
	public static final String TYPE_TICKET = "ticket";
	public static final String TYPE_PLAN_SUMMARY = "plan_summary";

	private final PineconeClient pineconeClient;
	private final EmbeddingService embeddingService;
	private final ChunkBuilder chunkBuilder;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final ScopeChangeEventRepository scopeChangeRepo;
	private final CarryForwardLinkRepository carryForwardLinkRepo;
	private final ManagerCommentRepository commentRepo;
	private final WorkItemRepository workItemRepo;
	private final TeamRepository teamRepo;
	private final RcdoNodeRepository rcdoNodeRepo;
	private final UserAccountRepository userRepo;
	private final Executor taskExecutor;

	public SemanticIndexService(PineconeClient pineconeClient, EmbeddingService embeddingService,
			ChunkBuilder chunkBuilder, WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository scopeChangeRepo, CarryForwardLinkRepository carryForwardLinkRepo,
			ManagerCommentRepository commentRepo, WorkItemRepository workItemRepo, TeamRepository teamRepo,
			RcdoNodeRepository rcdoNodeRepo, UserAccountRepository userRepo,
			@Qualifier("taskExecutor") Executor taskExecutor) {
		this.pineconeClient = pineconeClient;
		this.embeddingService = embeddingService;
		this.chunkBuilder = chunkBuilder;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.scopeChangeRepo = scopeChangeRepo;
		this.carryForwardLinkRepo = carryForwardLinkRepo;
		this.commentRepo = commentRepo;
		this.workItemRepo = workItemRepo;
		this.teamRepo = teamRepo;
		this.rcdoNodeRepo = rcdoNodeRepo;
		this.userRepo = userRepo;
		this.taskExecutor = taskExecutor;
	}

	// ── Async indexing ────────────────────────────────────────────────────

	/**
	 * Embeds and upserts the entity identified by {@code entityType} /
	 * {@code entityId} into Pinecone.
	 *
	 * <p>
	 * Runs asynchronously so it never blocks the write path. All failures are
	 * caught and logged.
	 */
	@Async
	@Transactional(readOnly = true)
	public void indexEntity(String entityType, UUID entityId) {
		if (!pineconeClient.isAvailable()) {
			return;
		}
		try {
			doIndex(entityType, entityId);
		} catch (Exception e) {
			log.warn("SemanticIndexService: failed to index {}:{} — {}", entityType, entityId, e.getMessage());
		}
	}

	/**
	 * Removes the vector for the given entity from Pinecone.
	 *
	 * <p>
	 * Runs asynchronously. Failure is logged but does not propagate.
	 */
	@Async
	public void deleteEntity(String entityType, UUID entityId) {
		if (!pineconeClient.isAvailable()) {
			return;
		}
		try {
			String namespace = resolveNamespaceForEntity(entityType, entityId);
			pineconeClient.deleteByIds(namespace, List.of(entityType + ":" + entityId));
		} catch (Exception e) {
			log.warn("SemanticIndexService: failed to delete {}:{} — {}", entityType, entityId, e.getMessage());
		}
	}

	// ── On-demand full reindex ────────────────────────────────────────────

	/**
	 * Kicks off a full reindex of every plan in the background. Returns immediately
	 * with the plan count; indexing continues asynchronously. Intended for
	 * admin/dev use after bulk SQL seed or Pinecone wipes.
	 *
	 * @return number of plans queued for indexing (not yet necessarily completed)
	 */
	@Transactional(readOnly = true)
	public int fullReindex() {
		if (!pineconeClient.isAvailable()) {
			log.warn("SemanticIndexService: fullReindex skipped — Pinecone unavailable");
			return 0;
		}
		List<WeeklyPlan> plans = planRepo.findAll();
		log.info("SemanticIndexService: fullReindex queuing {} plans", plans.size());
		// Use taskExecutor directly to avoid @Async self-invocation proxy bypass.
		taskExecutor.execute(() -> {
			log.info("SemanticIndexService: fullReindex background started — {} plans", plans.size());
			int ok = 0;
			for (WeeklyPlan plan : plans) {
				try {
					reindexPlan(plan);
					ok++;
				} catch (Exception ex) {
					log.warn("SemanticIndexService: fullReindex failed for plan {} — {}", plan.getId(),
							ex.getMessage());
				}
			}
			log.info("SemanticIndexService: fullReindex complete — {}/{} plans indexed", ok, plans.size());
		});
		return plans.size();
	}

	// ── Scheduled daily sweep ─────────────────────────────────────────────

	/**
	 * Re-indexes all entities belonging to plans updated in the last 48 hours.
	 *
	 * <p>
	 * This sweep catches anything that might have been missed during async indexing
	 * (e.g. if Pinecone was temporarily unavailable). Runs at 03:00 UTC daily and
	 * is skipped entirely when Pinecone is unavailable.
	 */
	@Scheduled(cron = "0 0 3 * * *")
	@Transactional(readOnly = true)
	public void dailySweepReindex() {
		if (!pineconeClient.isAvailable()) {
			log.debug("SemanticIndexService: daily sweep skipped — Pinecone unavailable");
			return;
		}
		try {
			Instant cutoff = Instant.now().minus(48, ChronoUnit.HOURS);
			List<WeeklyPlan> plans = planRepo.findUpdatedSince(cutoff);
			log.info("SemanticIndexService: daily sweep indexing {} recently-updated plans", plans.size());

			for (WeeklyPlan plan : plans) {
				try {
					reindexPlan(plan);
				} catch (Exception ex) {
					log.warn("SemanticIndexService: sweep failed for plan {} — {}", plan.getId(), ex.getMessage());
				}
			}
		} catch (Exception e) {
			log.warn("SemanticIndexService: daily sweep failed — {}", e.getMessage());
		}
	}

	// ── Private implementation ────────────────────────────────────────────

	/**
	 * Core indexing logic, shared by {@link #indexEntity} (async) and
	 * {@link #dailySweepReindex} (scheduled).
	 */
	private void doIndex(String entityType, UUID entityId) {
		switch (entityType) {
			case TYPE_COMMIT -> indexCommit(entityId);
			case TYPE_SCOPE_CHANGE -> indexScopeChange(entityId);
			case TYPE_CARRY_FORWARD -> indexCarryForward(entityId);
			case TYPE_MANAGER_COMMENT -> indexManagerComment(entityId);
			case TYPE_TICKET -> indexWorkItem(entityId);
			case TYPE_PLAN_SUMMARY -> indexPlanSummary(entityId);
			default -> log.warn("SemanticIndexService: unknown entityType '{}'", entityType);
		}
	}

	private void reindexPlan(WeeklyPlan plan) {
		// Plan summary
		indexPlanSummaryDirect(plan);

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		// All commits
		for (WeeklyCommit commit : commits) {
			try {
				indexCommitDirect(commit, plan);
			} catch (Exception ex) {
				log.warn("SemanticIndexService: sweep failed for commit {} — {}", commit.getId(), ex.getMessage());
			}
		}

		// All scope-change events
		for (ScopeChangeEvent event : scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId())) {
			try {
				indexScopeChangeDirect(event);
			} catch (Exception ex) {
				log.warn("SemanticIndexService: sweep failed for scope_change {} — {}", event.getId(), ex.getMessage());
			}
		}

		// Carry-forward links for each commit
		for (WeeklyCommit commit : commits) {
			for (CarryForwardLink link : carryForwardLinkRepo.findBySourceCommitId(commit.getId())) {
				try {
					indexCarryForwardDirect(link, commit);
				} catch (Exception ex) {
					log.warn("SemanticIndexService: sweep failed for carry_forward {} — {}", link.getId(),
							ex.getMessage());
				}
			}
		}
	}

	// ── Entity-specific index methods ─────────────────────────────────────

	private void indexCommit(UUID commitId) {
		commitRepo.findById(commitId).ifPresent(
				commit -> planRepo.findById(commit.getPlanId()).ifPresent(plan -> indexCommitDirect(commit, plan)));
	}

	private void indexCommitDirect(WeeklyCommit commit, WeeklyPlan plan) {
		// Resolve full RCDO ancestry path
		String rcdoPath = resolveRcdoPath(commit.getRcdoNodeId());

		// Resolve team name
		String teamName = resolveTeamName(plan.getTeamId());

		// Owner display name
		String ownerName = resolveUserName(commit.getOwnerUserId());

		// Carry-forward lineage
		String cfLineage = null;
		if (commit.getCarryForwardStreak() > 0 && commit.getCarryForwardSourceId() != null) {
			cfLineage = resolveCarryForwardLineage(commit);
		}

		// Linked ticket summary
		String ticketSummary = null;
		if (commit.getWorkItemId() != null) {
			ticketSummary = workItemRepo.findById(commit.getWorkItemId()).map(wi -> wi.getKey() + " " + wi.getTitle()
					+ " [" + (wi.getStatus() != null ? wi.getStatus().name() : "") + "]").orElse(null);
		}

		// Cross-team RCDO note
		String crossTeamNote = resolveCrossTeamRcdoNote(commit.getRcdoNodeId(), plan.getTeamId());

		ChunkBuilder.EnrichmentContext enrichment = new ChunkBuilder.EnrichmentContext(rcdoPath, teamName, ownerName,
				cfLineage, ticketSummary, crossTeamNote);
		ChunkBuilder.ChunkData chunk = chunkBuilder.buildCommitChunk(commit, plan, enrichment);
		String namespace = resolveNamespace(plan.getTeamId());
		upsertChunk(chunk, namespace);
	}

	private void indexScopeChange(UUID eventId) {
		scopeChangeRepo.findById(eventId).ifPresent(this::indexScopeChangeDirect);
	}

	private void indexScopeChangeDirect(ScopeChangeEvent event) {
		WeeklyCommit commit = event.getCommitId() != null
				? commitRepo.findById(event.getCommitId()).orElse(null)
				: null;
		// Resolve plan for temporal + team context
		WeeklyPlan plan = planRepo.findById(event.getPlanId()).orElse(null);
		String weekDate = plan != null && plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : null;
		String teamName = plan != null ? resolveTeamName(plan.getTeamId()) : null;
		UUID teamId = plan != null ? plan.getTeamId() : null;

		ChunkBuilder.ChunkData chunk = chunkBuilder.buildScopeChangeChunk(event, commit, weekDate, teamName, teamId);
		String namespace = plan != null ? resolveNamespace(plan.getTeamId()) : "";
		upsertChunk(chunk, namespace);
	}

	private void indexCarryForward(UUID linkId) {
		carryForwardLinkRepo.findById(linkId).ifPresent(link -> {
			WeeklyCommit source = commitRepo.findById(link.getSourceCommitId()).orElse(null);
			indexCarryForwardDirect(link, source);
		});
	}

	private void indexCarryForwardDirect(CarryForwardLink link, WeeklyCommit sourceCommit) {
		// Resolve RCDO path and team for strategic context
		String rcdoPath = sourceCommit != null ? resolveRcdoPath(sourceCommit.getRcdoNodeId()) : null;
		String teamName = null;
		UUID teamId = null;
		String weekStartDate = null;
		String namespace = "";
		if (sourceCommit != null) {
			WeeklyPlan plan = planRepo.findById(sourceCommit.getPlanId()).orElse(null);
			if (plan != null) {
				teamName = resolveTeamName(plan.getTeamId());
				teamId = plan.getTeamId();
				weekStartDate = plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : null;
				namespace = resolveNamespace(plan.getTeamId());
			}
		}
		ChunkBuilder.ChunkData chunk = chunkBuilder.buildCarryForwardChunk(link, sourceCommit, rcdoPath, teamName,
				teamId, weekStartDate);
		upsertChunk(chunk, namespace);
	}

	private void indexManagerComment(UUID commentId) {
		commentRepo.findById(commentId)
				.ifPresent(comment -> resolvePlanForManagerComment(comment)
						.ifPresent(plan -> upsertChunk(chunkBuilder.buildManagerCommentChunk(comment, plan),
								resolveNamespace(plan.getTeamId()))));
	}

	private void indexWorkItem(UUID itemId) {
		workItemRepo.findById(itemId).ifPresent(item -> {
			String rcdoPath = resolveRcdoPath(item.getRcdoNodeId());
			String assigneeName = resolveUserName(item.getAssigneeUserId());
			ChunkBuilder.ChunkData chunk = chunkBuilder.buildWorkItemChunk(item, rcdoPath, assigneeName);
			String namespace = resolveNamespace(item.getTeamId());
			upsertChunk(chunk, namespace);
		});
	}

	private void indexPlanSummary(UUID planId) {
		planRepo.findById(planId).ifPresent(this::indexPlanSummaryDirect);
	}

	private void indexPlanSummaryDirect(WeeklyPlan plan) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());

		// Owner and team names
		String ownerName = resolveUserName(plan.getOwnerUserId());
		String teamName = resolveTeamName(plan.getTeamId());

		// RCDO effort distribution: resolve each commit's RCDO leaf title and sum
		// points
		String rcdoDist = buildRcdoDistribution(commits);

		ChunkBuilder.ChunkData chunk = chunkBuilder.buildPlanSummaryChunk(plan, commits, ownerName, teamName, rcdoDist);
		String namespace = resolveNamespace(plan.getTeamId());
		upsertChunk(chunk, namespace);
	}

	// ── Enrichment helpers ────────────────────────────────────────────────

	/**
	 * Walks the RCDO tree from a leaf node up to the root Rally Cry and returns the
	 * full ancestry path, e.g.
	 * {@code "Rally Cry: Win Enterprise > DO: Improve Uptime > Outcome: 99.9% SLA"}.
	 *
	 * <p>
	 * Returns {@code null} when the RCDO node ID is null or cannot be resolved.
	 * Stops after 5 levels to guard against cycles.
	 */
	private String resolveRcdoPath(UUID rcdoNodeId) {
		if (rcdoNodeId == null) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		UUID current = rcdoNodeId;
		int maxDepth = 5;
		while (current != null && maxDepth-- > 0) {
			RcdoNode node = rcdoNodeRepo.findById(current).orElse(null);
			if (node == null) {
				break;
			}
			String typeLabel = switch (node.getNodeType()) {
				case RALLY_CRY -> "Rally Cry";
				case DEFINING_OBJECTIVE -> "DO";
				case OUTCOME -> "Outcome";
			};
			parts.add(0, typeLabel + ": " + node.getTitle());
			current = node.getParentId();
		}
		return parts.isEmpty() ? null : String.join(" > ", parts);
	}

	/**
	 * Resolves a team UUID to its display name. Returns {@code null} if absent.
	 */
	private String resolveTeamName(UUID teamId) {
		if (teamId == null) {
			return null;
		}
		return teamRepo.findById(teamId).map(Team::getName).orElse(null);
	}

	/**
	 * Resolves a user UUID to their display name. Returns {@code null} if absent.
	 */
	private String resolveUserName(UUID userId) {
		if (userId == null) {
			return null;
		}
		return userRepo.findById(userId).map(UserAccount::getDisplayName).orElse(null);
	}

	/**
	 * Builds a human-readable carry-forward lineage for a commit with
	 * {@code carryForwardStreak > 0}, e.g.
	 * {@code "Carried forward 3 weeks (original: \"Auth refactor\", week 2026-03-02)"}.
	 */
	private String resolveCarryForwardLineage(WeeklyCommit commit) {
		int streak = commit.getCarryForwardStreak();
		// Walk back to the original commit (up to streak depth)
		UUID sourceId = commit.getCarryForwardSourceId();
		String originalTitle = null;
		String originalWeek = null;
		int hops = 0;
		while (sourceId != null && hops < streak) {
			WeeklyCommit source = commitRepo.findById(sourceId).orElse(null);
			if (source == null) {
				break;
			}
			originalTitle = source.getTitle();
			// Resolve the source plan's week
			WeeklyPlan sourcePlan = planRepo.findById(source.getPlanId()).orElse(null);
			if (sourcePlan != null && sourcePlan.getWeekStartDate() != null) {
				originalWeek = sourcePlan.getWeekStartDate().toString();
			}
			sourceId = source.getCarryForwardSourceId();
			hops++;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Carried forward ").append(streak).append(" week(s)");
		if (originalTitle != null) {
			sb.append(" (original: \"").append(originalTitle).append("\"");
			if (originalWeek != null) {
				sb.append(", week ").append(originalWeek);
			}
			sb.append(")");
		}
		return sb.toString();
	}

	/**
	 * Checks whether other teams also have commits targeting the same RCDO node and
	 * returns a note like {@code "Also targeted by: Backend, QA"}, or {@code null}
	 * if there is no cross-team overlap.
	 */
	private String resolveCrossTeamRcdoNote(UUID rcdoNodeId, UUID thisTeamId) {
		if (rcdoNodeId == null) {
			return null;
		}
		try {
			List<WeeklyCommit> peerCommits = commitRepo.findByRcdoNodeId(rcdoNodeId);
			Set<UUID> otherTeamIds = peerCommits.stream()
					.map(c -> planRepo.findById(c.getPlanId()).map(WeeklyPlan::getTeamId).orElse(null))
					.filter(tid -> tid != null && !tid.equals(thisTeamId)).collect(Collectors.toSet());
			if (otherTeamIds.isEmpty()) {
				return null;
			}
			String teamNames = otherTeamIds.stream().map(this::resolveTeamName).filter(n -> n != null && !n.isEmpty())
					.collect(Collectors.joining(", "));
			return teamNames.isEmpty() ? null : "Also targeted by: " + teamNames;
		} catch (Exception e) {
			log.debug("SemanticIndexService: cross-team RCDO resolution failed — {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Builds a human-readable RCDO effort distribution for a plan's commits, e.g.
	 * {@code "Improve Uptime: 8pts (3 commits), Reduce Churn: 5pts (2 commits)"}.
	 */
	private String buildRcdoDistribution(List<WeeklyCommit> commits) {
		if (commits == null || commits.isEmpty()) {
			return null;
		}
		// Group by RCDO node ID → (title, total points, count)
		Map<UUID, String> titleCache = new HashMap<>();
		Map<UUID, int[]> stats = new LinkedHashMap<>(); // int[0]=points, int[1]=count
		for (WeeklyCommit c : commits) {
			UUID rcdoId = c.getRcdoNodeId();
			if (rcdoId == null) {
				continue;
			}
			stats.computeIfAbsent(rcdoId, k -> new int[]{0, 0});
			int[] s = stats.get(rcdoId);
			s[0] += c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
			s[1]++;
			titleCache.computeIfAbsent(rcdoId, k -> rcdoNodeRepo.findById(k).map(RcdoNode::getTitle).orElse("Unknown"));
		}
		if (stats.isEmpty()) {
			return null;
		}
		return stats.entrySet().stream().map(e -> titleCache.getOrDefault(e.getKey(), "Unknown") + ": "
				+ e.getValue()[0] + "pts (" + e.getValue()[1] + " commits)").collect(Collectors.joining(", "));
	}

	// ── Namespace resolution ──────────────────────────────────────────────

	/**
	 * Resolves the Pinecone namespace (organisation ID string) for the given
	 * entity. Returns empty string if the chain cannot be resolved.
	 */
	private String resolveNamespaceForEntity(String entityType, UUID entityId) {
		try {
			return switch (entityType) {
				case TYPE_COMMIT -> commitRepo.findById(entityId).flatMap(c -> planRepo.findById(c.getPlanId()))
						.map(p -> resolveNamespace(p.getTeamId())).orElse("");
				case TYPE_SCOPE_CHANGE ->
					scopeChangeRepo.findById(entityId).flatMap(e -> planRepo.findById(e.getPlanId()))
							.map(p -> resolveNamespace(p.getTeamId())).orElse("");
				case TYPE_CARRY_FORWARD ->
					carryForwardLinkRepo.findById(entityId).flatMap(l -> commitRepo.findById(l.getSourceCommitId()))
							.flatMap(c -> planRepo.findById(c.getPlanId())).map(p -> resolveNamespace(p.getTeamId()))
							.orElse("");
				case TYPE_MANAGER_COMMENT -> commentRepo.findById(entityId).flatMap(this::resolvePlanForManagerComment)
						.map(p -> resolveNamespace(p.getTeamId())).orElse("");
				case TYPE_TICKET ->
					workItemRepo.findById(entityId).map(i -> resolveNamespace(i.getTeamId())).orElse("");
				case TYPE_PLAN_SUMMARY ->
					planRepo.findById(entityId).map(p -> resolveNamespace(p.getTeamId())).orElse("");
				default -> "";
			};
		} catch (Exception e) {
			log.warn("SemanticIndexService: could not resolve namespace for {}:{}", entityType, entityId);
			return "";
		}
	}

	private String resolveNamespace(UUID teamId) {
		if (teamId == null) {
			return "";
		}
		return teamRepo.findById(teamId).map(t -> t.getOrganizationId() != null ? t.getOrganizationId().toString() : "")
				.orElse("");
	}

	private java.util.Optional<WeeklyPlan> resolvePlanForManagerComment(
			com.weeklycommit.domain.entity.ManagerComment comment) {
		if (comment.getPlanId() != null) {
			return planRepo.findById(comment.getPlanId());
		}
		if (comment.getCommitId() != null) {
			return commitRepo.findById(comment.getCommitId()).flatMap(c -> planRepo.findById(c.getPlanId()));
		}
		return java.util.Optional.empty();
	}

	// ── Embed + upsert helper ─────────────────────────────────────────────

	private void upsertChunk(ChunkBuilder.ChunkData chunk, String namespace) {
		if (chunk == null || chunk.text() == null || chunk.text().isBlank()) {
			return;
		}
		float[] embedding = embeddingService.embed(chunk.text());
		if (embedding.length == 0) {
			log.debug("SemanticIndexService: embedding unavailable for chunk '{}' — skipping upsert", chunk.id());
			return;
		}
		Map<String, Object> meta = new HashMap<>(chunk.metadata());
		meta.put("text", chunk.text());
		pineconeClient.upsert(namespace, List.of(new PineconeClient.PineconeVector(chunk.id(), embedding, meta)));
	}
}
