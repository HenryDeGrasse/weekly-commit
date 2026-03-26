package com.weeklycommit.ai.rag;

import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ManagerComment;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
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
 * All fields are handled null-safely: optional values that are {@code null} are
 * replaced with an empty string in the text and omitted from metadata (or
 * stored as an empty string where the key is always expected).
 */
@Component
public class ChunkBuilder {

	// ── Inner record ─────────────────────────────────────────────────────

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
	public record ChunkData(String id, String text, Map<String, String> metadata) {
	}

	// ── Public build methods ─────────────────────────────────────────────

	/**
	 * Builds a chunk for a {@link WeeklyCommit}.
	 *
	 * <p>
	 * Text format: {@code "{title}" — {chessPiece}, {estimatePoints}pts.
	 * Description: {description}. Success criteria: {successCriteria}. Outcome:
	 * {outcome}. Notes: {outcomeNotes}}.
	 */
	public ChunkData buildCommitChunk(WeeklyCommit commit, WeeklyPlan plan) {
		String pts = commit.getEstimatePoints() != null ? commit.getEstimatePoints().toString() : "?";
		String chess = commit.getChessPiece() != null ? commit.getChessPiece().name() : "";
		String outcome = commit.getOutcome() != null ? commit.getOutcome().name() : "";

		String text = "\"" + commit.getTitle() + "\" — " + chess + ", " + pts + "pts. " + "Description: "
				+ orEmpty(commit.getDescription()) + ". " + "Success criteria: " + orEmpty(commit.getSuccessCriteria())
				+ ". " + "Outcome: " + outcome + ". " + "Notes: " + orEmpty(commit.getOutcomeNotes());

		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("entityType", "commit");
		meta.put("entityId", str(commit.getId()));
		meta.put("userId", str(commit.getOwnerUserId()));
		meta.put("teamId", str(plan.getTeamId()));
		meta.put("planId", str(plan.getId()));
		meta.put("weekStartDate", plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : "");

		return new ChunkData("commit:" + commit.getId(), text, meta);
	}

	/**
	 * Builds a chunk for a {@link ScopeChangeEvent}.
	 *
	 * <p>
	 * Text format: {@code Scope change ({category}): commit "{commitTitle}" —
	 * {reason}}.
	 */
	public ChunkData buildScopeChangeChunk(ScopeChangeEvent event, WeeklyCommit commit) {
		String category = event.getCategory() != null ? event.getCategory().name() : "";
		String commitTitle = commit != null ? commit.getTitle() : "";

		String text = "Scope change (" + category + "): commit \"" + commitTitle + "\" — " + orEmpty(event.getReason());

		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("entityType", "scope_change");
		meta.put("entityId", str(event.getId()));
		meta.put("planId", str(event.getPlanId()));
		meta.put("commitId", str(event.getCommitId()));
		meta.put("userId", str(event.getChangedByUserId()));

		return new ChunkData("scope_change:" + event.getId(), text, meta);
	}

	/**
	 * Builds a chunk for a {@link CarryForwardLink}.
	 *
	 * <p>
	 * Text format: {@code Carry forward: "{commitTitle}" carried forward for
	 * {streak} week(s). Reason: {reason}. Notes: {reasonNotes}}.
	 */
	public ChunkData buildCarryForwardChunk(CarryForwardLink link, WeeklyCommit sourceCommit) {
		String commitTitle = sourceCommit != null ? sourceCommit.getTitle() : "";
		int streak = sourceCommit != null ? sourceCommit.getCarryForwardStreak() : 0;
		String reason = link.getReason() != null ? link.getReason().name() : "";

		String text = "Carry forward: \"" + commitTitle + "\" carried forward for " + streak + " week(s). " + "Reason: "
				+ reason + ". " + "Notes: " + orEmpty(link.getReasonNotes());

		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("entityType", "carry_forward");
		meta.put("entityId", str(link.getId()));
		meta.put("sourceCommitId", str(link.getSourceCommitId()));
		meta.put("targetCommitId", str(link.getTargetCommitId()));

		return new ChunkData("carry_forward:" + link.getId(), text, meta);
	}

	/**
	 * Builds a chunk for a {@link ManagerComment}.
	 *
	 * <p>
	 * Text format: {@code Manager comment (week {weekStartDate}): {content}}.
	 */
	public ChunkData buildManagerCommentChunk(ManagerComment comment, WeeklyPlan plan) {
		String weekDate = plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : "";

		String text = "Manager comment (week " + weekDate + "): " + orEmpty(comment.getContent());

		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("entityType", "manager_comment");
		meta.put("entityId", str(comment.getId()));
		meta.put("planId", str(comment.getPlanId()));
		meta.put("userId", str(comment.getAuthorUserId()));
		meta.put("weekStartDate", weekDate);

		return new ChunkData("manager_comment:" + comment.getId(), text, meta);
	}

	/**
	 * Builds a chunk for a {@link WorkItem} (native ticket).
	 *
	 * <p>
	 * Text format: {@code {key}: {title}. Description: {description}. Status:
	 * {status}.}.
	 */
	public ChunkData buildWorkItemChunk(WorkItem item) {
		String status = item.getStatus() != null ? item.getStatus().name() : "";

		String text = item.getKey() + ": " + item.getTitle() + ". " + "Description: " + orEmpty(item.getDescription())
				+ ". " + "Status: " + status + ".";

		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("entityType", "ticket");
		meta.put("entityId", str(item.getId()));
		meta.put("teamId", str(item.getTeamId()));

		return new ChunkData("ticket:" + item.getId(), text, meta);
	}

	/**
	 * Builds a plan-level summary chunk from a {@link WeeklyPlan} and its commits.
	 *
	 * <p>
	 * Text format: {@code Week of {weekStartDate}: {count} commits,
	 * {totalPoints}pts, {state}}.
	 */
	public ChunkData buildPlanSummaryChunk(WeeklyPlan plan, List<WeeklyCommit> commits) {
		int count = commits != null ? commits.size() : 0;
		int totalPoints = commits != null
				? commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0).sum()
				: 0;
		String weekDate = plan.getWeekStartDate() != null ? plan.getWeekStartDate().toString() : "";
		String state = plan.getState() != null ? plan.getState().name() : "";

		String text = "Week of " + weekDate + ": " + count + " commits, " + totalPoints + "pts, " + state;

		Map<String, String> meta = new LinkedHashMap<>();
		meta.put("entityType", "plan_summary");
		meta.put("entityId", str(plan.getId()));
		meta.put("teamId", str(plan.getTeamId()));
		meta.put("userId", str(plan.getOwnerUserId()));
		meta.put("weekStartDate", weekDate);

		return new ChunkData("plan_summary:" + plan.getId(), text, meta);
	}

	// ── Private helpers ──────────────────────────────────────────────────

	private static String orEmpty(String value) {
		return value != null ? value : "";
	}

	private static String str(UUID uuid) {
		return uuid != null ? uuid.toString() : "";
	}
}
