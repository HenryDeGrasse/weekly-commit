package com.weeklycommit.ai.rag;

import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ManagerComment;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Converts domain entities into {@link ChunkData} records suitable for
 * embedding and storage in Pinecone.
 *
 * <p>
 * Each chunk has a stable ID ({@code entityType:entityId}), a human-readable
 * text representation, and a flat {@code Map<String, String>} of metadata
 * stored alongside the vector for filtered retrieval.
 *
 * <p>
 * Chunk text is enriched with relationship context (RCDO ancestry, carry-
 * forward lineage, cross-team overlap, etc.) so the embedding captures the
 * relational structure that raw fields alone would flatten away.
 *
 * <p>
 * All fields are handled null-safely: optional values that are {@code null} are
 * replaced with an empty string in the text and omitted from metadata (or
 * stored as an empty string where the key is always expected).
 */
@Component
public class ChunkBuilder {

	// ── Inner records ────────────────────────────────────────────────────

	/**
	 * A text chunk ready to be embedded and stored in Pinecone.
	 *
	 * @param id
	 *            stable Pinecone vector ID: {@code entityType:entityId}
	 * @param text
	 *            human-readable text that will be embedded
	 * @param metadata
	 *            flat string map stored alongside the vector
	 */
	public record ChunkData(String id, String text, Map<String, Object> metadata) {
	}

	/**
	 * Optional enrichment context that callers populate from DB lookups. Every
	 * field is nullable; the builder treats null as absent.
	 *
	 * @param rcdoPath
	 *            full RCDO ancestry, e.g.
	 *            {@code "Rally Cry: Win Enterprise > DO: Improve Uptime > Outcome: 99.9% SLA"}
	 * @param teamName
	 *            resolved team display name
	 * @param ownerDisplayName
	 *            resolved owner user display name
	 * @param carryForwardLineage
	 *            human-readable lineage, e.g.
	 *            {@code "Carried forward 3 weeks (original: \"Auth refactor\", week 2026-03-02)"}
	 * @param linkedTicketSummary
	 *            brief ticket context, e.g.
	 *            {@code "WC-42 Fix memory leak [IN_PROGRESS]"}
	 * @param crossTeamRcdoNote
	 *            teams also targeting the same RCDO, e.g.
	 *            {@code "Also targeted by: Backend, QA"}
	 */
	public record EnrichmentContext(String rcdoPath, String teamName, String ownerDisplayName,
			String carryForwardLineage, String linkedTicketSummary, String crossTeamRcdoNote) {

		/** Empty context — equivalent to no enrichment. */
		public static EnrichmentContext empty() {
			return new EnrichmentContext(null, null, null, null, null, null);
		}
	}

	// ── Public build methods ─────────────────────────────────────────────

	/**
	 * Builds a chunk for a {@link WeeklyCommit} with full relationship context.
	 *
	 * @param commit
	 *            the commit to chunk
	 * @param plan
	 *            the owning weekly plan
	 * @param enrichment
	 *            pre-resolved relationship context (may be
	 *            {@link EnrichmentContext#empty()})
	 */
	public ChunkData buildCommitChunk(WeeklyCommit commit, WeeklyPlan plan, EnrichmentContext enrichment) {
		String pts = commit.getEstimatePoints() != null ? commit.getEstimatePoints().toString() : "?";
		String chess = commit.getChessPiece() != null ? commit.getChessPiece().name() : "";
		String outcome = commit.getOutcome() != null ? commit.getOutcome().name() : "";

		StringBuilder sb = new StringBuilder();
		sb.append("\"").append(commit.getTitle()).append("\" — ").append(chess).append(", ").append(pts).append("pts.");

		// Owner
		if (enrichment.ownerDisplayName() != null && !enrichment.ownerDisplayName().isEmpty()) {
			sb.append(" Owner: ").append(enrichment.ownerDisplayName()).append(".");
		}

		// RCDO: full path or flat title
		if (enrichment.rcdoPath() != null && !enrichment.rcdoPath().isEmpty()) {
			sb.append(" RCDO path: ").append(enrichment.rcdoPath()).append(".");
		}

		// Team
		if (enrichment.teamName() != null && !enrichment.teamName().isEmpty()) {
			sb.append(" Team: ").append(enrichment.teamName()).append(".");
		}

		// Cross-team RCDO overlap
		if (enrichment.crossTeamRcdoNote() != null && !enrichment.crossTeamRcdoNote().isEmpty()) {
			sb.append(" ").append(enrichment.crossTeamRcdoNote()).append(".");
		}

		// Carry-forward lineage
		if (enrichment.carryForwardLineage() != null && !enrichment.carryForwardLineage().isEmpty()) {
			sb.append(" ").append(enrichment.carryForwardLineage()).append(".");
		}

		// Linked ticket
		if (enrichment.linkedTicketSummary() != null && !enrichment.linkedTicketSummary().isEmpty()) {
			sb.append(" Linked ticket: ").append(enrichment.linkedTicketSummary()).append(".");
		}

		// Core fields
		sb.append(" Description: ").append(orEmpty(commit.getDescription())).append(".");
		sb.append(" Success criteria: ").append(orEmpty(commit.getSuccessCriteria())).append(".");
		sb.append(" Outcome: ").append(outcome).append(".");
		sb.append(" Notes: ").append(orEmpty(commit.getOutcomeNotes()));

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("entityType", "commit");
		meta.put("entityId", str(commit.getId()));
		meta.put("userId", str(commit.getOwnerUserId()));
		meta.put("teamId", str(plan.getTeamId()));
		meta.put("planId", str(plan.getId()));
		putWeekStartDate(meta, plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : null);

		return new ChunkData("commit:" + commit.getId(), sb.toString(), meta);
	}

	/**
	 * Builds a chunk for a {@link WeeklyCommit} with flat RCDO title + team name
	 * (legacy enrichment — preserved for backward compatibility).
	 */
	public ChunkData buildCommitChunk(WeeklyCommit commit, WeeklyPlan plan, String rcdoNodeTitle, String teamName) {
		return buildCommitChunk(commit, plan, new EnrichmentContext(
				rcdoNodeTitle != null ? "RCDO: " + rcdoNodeTitle : null, teamName, null, null, null, null));
	}

	/**
	 * Backwards-compatible overload without any enrichment.
	 */
	public ChunkData buildCommitChunk(WeeklyCommit commit, WeeklyPlan plan) {
		return buildCommitChunk(commit, plan, EnrichmentContext.empty());
	}

	/**
	 * Builds a chunk for a {@link ScopeChangeEvent} with optional enrichment.
	 *
	 * @param event
	 *            the scope change event
	 * @param commit
	 *            the affected commit (may be null)
	 * @param weekStartDate
	 *            ISO week date for temporal context (may be null)
	 * @param teamName
	 *            team name for context (may be null)
	 */
	public ChunkData buildScopeChangeChunk(ScopeChangeEvent event, WeeklyCommit commit, String weekStartDate,
			String teamName) {
		return buildScopeChangeChunk(event, commit, weekStartDate, teamName, null);
	}

	/**
	 * Builds a chunk for a {@link ScopeChangeEvent} with full enrichment including
	 * team ID for metadata filtering.
	 */
	public ChunkData buildScopeChangeChunk(ScopeChangeEvent event, WeeklyCommit commit, String weekStartDate,
			String teamName, UUID teamId) {
		String category = event.getCategory() != null ? event.getCategory().name() : "";
		String commitTitle = commit != null ? commit.getTitle() : "";

		StringBuilder sb = new StringBuilder();
		sb.append("Scope change (").append(category).append(")");
		if (weekStartDate != null && !weekStartDate.isEmpty()) {
			sb.append(" during week of ").append(weekStartDate);
		}
		if (teamName != null && !teamName.isEmpty()) {
			sb.append(" [").append(teamName).append("]");
		}
		sb.append(": commit \"").append(commitTitle).append("\" — ").append(orEmpty(event.getReason()));
		if (event.getPreviousValue() != null && event.getNewValue() != null) {
			sb.append(" (changed from ").append(event.getPreviousValue()).append(" to ").append(event.getNewValue())
					.append(")");
		}

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("entityType", "scope_change");
		meta.put("entityId", str(event.getId()));
		meta.put("planId", str(event.getPlanId()));
		meta.put("commitId", str(event.getCommitId()));
		meta.put("userId", str(event.getChangedByUserId()));
		if (teamId != null)
			meta.put("teamId", str(teamId));
		putWeekStartDate(meta, weekStartDate);

		return new ChunkData("scope_change:" + event.getId(), sb.toString(), meta);
	}

	/**
	 * Backwards-compatible overload without temporal/team enrichment.
	 */
	public ChunkData buildScopeChangeChunk(ScopeChangeEvent event, WeeklyCommit commit) {
		return buildScopeChangeChunk(event, commit, null, null);
	}

	/**
	 * Builds a chunk for a {@link CarryForwardLink} with optional enrichment.
	 *
	 * @param link
	 *            the carry-forward link
	 * @param sourceCommit
	 *            the source commit being carried
	 * @param rcdoPath
	 *            RCDO ancestry of the source commit (may be null)
	 * @param teamName
	 *            team name (may be null)
	 */
	public ChunkData buildCarryForwardChunk(CarryForwardLink link, WeeklyCommit sourceCommit, String rcdoPath,
			String teamName) {
		return buildCarryForwardChunk(link, sourceCommit, rcdoPath, teamName, null, null);
	}

	/**
	 * Builds a carry-forward chunk with team ID and week date for metadata
	 * filtering.
	 */
	public ChunkData buildCarryForwardChunk(CarryForwardLink link, WeeklyCommit sourceCommit, String rcdoPath,
			String teamName, UUID teamId, String weekStartDate) {
		String commitTitle = sourceCommit != null ? sourceCommit.getTitle() : "";
		int streak = sourceCommit != null ? sourceCommit.getCarryForwardStreak() : 0;
		String reason = link.getReason() != null ? link.getReason().name() : "";

		StringBuilder sb = new StringBuilder();
		sb.append("Carry forward: \"").append(commitTitle).append("\" carried forward for ").append(streak)
				.append(" week(s).");
		if (rcdoPath != null && !rcdoPath.isEmpty()) {
			sb.append(" Strategic context: ").append(rcdoPath).append(".");
		}
		if (teamName != null && !teamName.isEmpty()) {
			sb.append(" Team: ").append(teamName).append(".");
		}
		sb.append(" Reason: ").append(reason).append(".");
		sb.append(" Notes: ").append(orEmpty(link.getReasonNotes()));

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("entityType", "carry_forward");
		meta.put("entityId", str(link.getId()));
		meta.put("sourceCommitId", str(link.getSourceCommitId()));
		meta.put("targetCommitId", str(link.getTargetCommitId()));
		if (teamId != null)
			meta.put("teamId", str(teamId));
		putWeekStartDate(meta, weekStartDate);

		return new ChunkData("carry_forward:" + link.getId(), sb.toString(), meta);
	}

	/**
	 * Backwards-compatible overload without relationship enrichment.
	 */
	public ChunkData buildCarryForwardChunk(CarryForwardLink link, WeeklyCommit sourceCommit) {
		return buildCarryForwardChunk(link, sourceCommit, null, null);
	}

	/**
	 * Builds a chunk for a {@link ManagerComment}.
	 */
	public ChunkData buildManagerCommentChunk(ManagerComment comment, WeeklyPlan plan) {
		String weekDate = plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : "";

		String text = "Manager comment (week " + weekDate + "): " + orEmpty(comment.getContent());

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("entityType", "manager_comment");
		meta.put("entityId", str(comment.getId()));
		meta.put("planId", str(plan.getId()));
		meta.put("teamId", str(plan.getTeamId()));
		meta.put("userId", str(comment.getAuthorUserId()));
		putWeekStartDate(meta, weekDate);

		return new ChunkData("manager_comment:" + comment.getId(), text, meta);
	}

	/**
	 * Builds a chunk for a {@link WorkItem} (native ticket) with optional
	 * enrichment.
	 *
	 * @param item
	 *            the work item
	 * @param rcdoPath
	 *            RCDO ancestry if the ticket has an RCDO link (may be null)
	 * @param assigneeName
	 *            resolved assignee display name (may be null)
	 */
	public ChunkData buildWorkItemChunk(WorkItem item, String rcdoPath, String assigneeName) {
		String status = item.getStatus() != null ? item.getStatus().name() : "";
		String priority = item.getPriority() != null ? item.getPriority().name() : "";

		StringBuilder sb = new StringBuilder();
		sb.append(item.getKey()).append(": ").append(item.getTitle()).append(".");
		if (assigneeName != null && !assigneeName.isEmpty()) {
			sb.append(" Assignee: ").append(assigneeName).append(".");
		}
		sb.append(" Status: ").append(status).append(".");
		sb.append(" Priority: ").append(priority).append(".");
		if (rcdoPath != null && !rcdoPath.isEmpty()) {
			sb.append(" RCDO path: ").append(rcdoPath).append(".");
		}
		sb.append(" Description: ").append(orEmpty(item.getDescription())).append(".");

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("entityType", "ticket");
		meta.put("entityId", str(item.getId()));
		meta.put("teamId", str(item.getTeamId()));

		return new ChunkData("ticket:" + item.getId(), sb.toString(), meta);
	}

	/**
	 * Backwards-compatible overload without enrichment.
	 */
	public ChunkData buildWorkItemChunk(WorkItem item) {
		return buildWorkItemChunk(item, null, null);
	}

	/**
	 * Builds a plan-level summary chunk with RCDO effort distribution.
	 *
	 * @param plan
	 *            the weekly plan
	 * @param commits
	 *            commits in the plan (may be null/empty)
	 * @param ownerDisplayName
	 *            plan owner's display name (may be null)
	 * @param teamName
	 *            team name (may be null)
	 * @param rcdoDistribution
	 *            human-readable RCDO effort breakdown, e.g.
	 *            {@code "Improve Uptime: 8pts (3 commits), Reduce Churn: 5pts (2 commits)"}
	 *            (may be null)
	 */
	public ChunkData buildPlanSummaryChunk(WeeklyPlan plan, List<WeeklyCommit> commits, String ownerDisplayName,
			String teamName, String rcdoDistribution) {
		int count = commits != null ? commits.size() : 0;
		int totalPoints = commits != null
				? commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum()
				: 0;
		int achievedPoints = commits != null
				? commits.stream().filter(c -> c.getOutcome() != null && c.getOutcome().name().equals("ACHIEVED"))
						.mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum()
				: 0;
		String weekDate = plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : "";
		String state = plan.getState() != null ? plan.getState().name() : "";

		StringBuilder sb = new StringBuilder();
		sb.append("Weekly plan summary for week of ").append(weekDate).append(": ").append(count).append(" commits, ")
				.append(totalPoints).append("pts planned, ").append(achievedPoints).append("pts achieved, state=")
				.append(state).append(".");
		if (ownerDisplayName != null && !ownerDisplayName.isEmpty()) {
			sb.append(" Owner: ").append(ownerDisplayName).append(".");
		}
		if (teamName != null && !teamName.isEmpty()) {
			sb.append(" Team: ").append(teamName).append(".");
		}

		// Inline commit listing — the most critical enrichment for plan-level queries
		if (commits != null && !commits.isEmpty()) {
			sb.append(" Commits: ");
			for (int i = 0; i < commits.size(); i++) {
				WeeklyCommit c = commits.get(i);
				if (i > 0)
					sb.append(" | ");
				sb.append("\"").append(c.getTitle()).append("\"");
				if (c.getChessPiece() != null)
					sb.append(" (").append(c.getChessPiece().name()).append(")");
				if (c.getEstimatePoints() != null)
					sb.append(" ").append(c.getEstimatePoints()).append("pts");
				if (c.getOutcome() != null)
					sb.append(" → ").append(c.getOutcome().name());
			}
			sb.append(".");
		}

		if (rcdoDistribution != null && !rcdoDistribution.isEmpty()) {
			sb.append(" RCDO effort: ").append(rcdoDistribution).append(".");
		}

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("entityType", "plan_summary");
		meta.put("entityId", str(plan.getId()));
		meta.put("teamId", str(plan.getTeamId()));
		meta.put("userId", str(plan.getOwnerUserId()));
		putWeekStartDate(meta, weekDate);

		return new ChunkData("plan_summary:" + plan.getId(), sb.toString(), meta);
	}

	/**
	 * Backwards-compatible overload without enrichment.
	 */
	public ChunkData buildPlanSummaryChunk(WeeklyPlan plan, List<WeeklyCommit> commits) {
		return buildPlanSummaryChunk(plan, commits, null, null, null);
	}

	// ── Private helpers ──────────────────────────────────────────────────

	private static String orEmpty(String value) {
		return value != null ? value : "";
	}

	private static String str(UUID uuid) {
		return uuid != null ? uuid.toString() : "";
	}

	/**
	 * Converts an ISO date string ("YYYY-MM-DD") to epoch days (days since
	 * 1970-01-01). Returns {@code null} when the string is null or blank.
	 *
	 * <p>
	 * Stored as a numeric field in Pinecone metadata alongside the string
	 * {@code weekStartDate} so that {@code $gte} / {@code $lte} range filters work
	 * (Pinecone comparison operators require numeric values, not strings).
	 */
	static Long toEpochDay(String isoDate) {
		if (isoDate == null || isoDate.isBlank())
			return null;
		try {
			return LocalDate.parse(isoDate).toEpochDay();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Adds both the human-readable {@code weekStartDate} string and the numeric
	 * {@code weekStartEpochDay} to the given metadata map. Safe to call with a null
	 * or blank date — in that case neither field is written.
	 */
	static void putWeekStartDate(Map<String, Object> meta, String isoDate) {
		if (isoDate == null || isoDate.isBlank())
			return;
		meta.put("weekStartDate", isoDate);
		Long epochDay = toEpochDay(isoDate);
		if (epochDay != null)
			meta.put("weekStartEpochDay", epochDay);
	}
}
