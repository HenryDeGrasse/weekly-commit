package com.weeklycommit.ai.rag;

import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.repository.CarryForwardLinkRepository;
import com.weeklycommit.domain.repository.ManagerCommentRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

	public SemanticIndexService(PineconeClient pineconeClient, EmbeddingService embeddingService,
			ChunkBuilder chunkBuilder, WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			ScopeChangeEventRepository scopeChangeRepo, CarryForwardLinkRepository carryForwardLinkRepo,
			ManagerCommentRepository commentRepo, WorkItemRepository workItemRepo, TeamRepository teamRepo) {
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
		ChunkBuilder.ChunkData chunk = chunkBuilder.buildCommitChunk(commit, plan);
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
		ChunkBuilder.ChunkData chunk = chunkBuilder.buildScopeChangeChunk(event, commit);
		String namespace = planRepo.findById(event.getPlanId()).map(p -> resolveNamespace(p.getTeamId())).orElse("");
		upsertChunk(chunk, namespace);
	}

	private void indexCarryForward(UUID linkId) {
		carryForwardLinkRepo.findById(linkId).ifPresent(link -> {
			WeeklyCommit source = commitRepo.findById(link.getSourceCommitId()).orElse(null);
			indexCarryForwardDirect(link, source);
		});
	}

	private void indexCarryForwardDirect(CarryForwardLink link, WeeklyCommit sourceCommit) {
		ChunkBuilder.ChunkData chunk = chunkBuilder.buildCarryForwardChunk(link, sourceCommit);
		String namespace = "";
		if (sourceCommit != null) {
			namespace = planRepo.findById(sourceCommit.getPlanId()).map(p -> resolveNamespace(p.getTeamId()))
					.orElse("");
		}
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
			ChunkBuilder.ChunkData chunk = chunkBuilder.buildWorkItemChunk(item);
			String namespace = resolveNamespace(item.getTeamId());
			upsertChunk(chunk, namespace);
		});
	}

	private void indexPlanSummary(UUID planId) {
		planRepo.findById(planId).ifPresent(this::indexPlanSummaryDirect);
	}

	private void indexPlanSummaryDirect(WeeklyPlan plan) {
		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		ChunkBuilder.ChunkData chunk = chunkBuilder.buildPlanSummaryChunk(plan, commits);
		String namespace = resolveNamespace(plan.getTeamId());
		upsertChunk(chunk, namespace);
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
		pineconeClient.upsert(namespace, List.of(new PineconeClient.PineconeVector(chunk.id(), embedding, meta)));
	}
}
