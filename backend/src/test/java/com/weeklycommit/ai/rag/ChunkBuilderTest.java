package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ManagerComment;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.CarryForwardReason;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.enums.TicketStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChunkBuilder}.
 *
 * <p>
 * Verifies chunk ID format, text content, metadata keys/values, and null-safe
 * handling of optional entity fields for each supported entity type.
 */
class ChunkBuilderTest {

	private ChunkBuilder builder;

	// Shared UUIDs
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID TEAM_ID = UUID.randomUUID();
	private static final UUID PLAN_ID = UUID.randomUUID();
	private static final UUID COMMIT_ID = UUID.randomUUID();
	private static final LocalDate WEEK = LocalDate.of(2025, 1, 6);

	@BeforeEach
	void setUp() {
		builder = new ChunkBuilder();
	}

	// ── buildCommitChunk ──────────────────────────────────────────────────

	@Test
	void buildCommitChunk_idHasCorrectFormat() {
		WeeklyCommit commit = commit(COMMIT_ID, "Fix login bug");
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.id()).isEqualTo("commit:" + COMMIT_ID);
	}

	@Test
	void buildCommitChunk_textContainsTitleAndChessPiece() {
		WeeklyCommit commit = commit(COMMIT_ID, "Fix login bug");
		commit.setChessPiece(ChessPiece.QUEEN);
		commit.setEstimatePoints(3);
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.text()).contains("\"Fix login bug\"");
		assertThat(chunk.text()).contains("QUEEN");
		assertThat(chunk.text()).contains("3pts");
	}

	@Test
	void buildCommitChunk_textContainsDescription() {
		WeeklyCommit commit = commit(COMMIT_ID, "Deploy service");
		commit.setDescription("Deploy to production environment");
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.text()).contains("Deploy to production environment");
	}

	@Test
	void buildCommitChunk_textContainsOutcomeAndNotes() {
		WeeklyCommit commit = commit(COMMIT_ID, "Feature X");
		commit.setOutcome(CommitOutcome.ACHIEVED);
		commit.setOutcomeNotes("Delivered on time");
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.text()).contains("ACHIEVED");
		assertThat(chunk.text()).contains("Delivered on time");
	}

	@Test
	void buildCommitChunk_metadataContainsRequiredKeys() {
		WeeklyCommit commit = commit(COMMIT_ID, "Task A");
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.metadata()).containsKey("entityType");
		assertThat(chunk.metadata()).containsKey("entityId");
		assertThat(chunk.metadata()).containsKey("userId");
		assertThat(chunk.metadata()).containsKey("teamId");
		assertThat(chunk.metadata()).containsKey("planId");
		assertThat(chunk.metadata()).containsKey("weekStartDate");
	}

	@Test
	void buildCommitChunk_metadataValuesAreCorrect() {
		WeeklyCommit commit = commit(COMMIT_ID, "Task A");
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.metadata().get("entityType")).isEqualTo("commit");
		assertThat(chunk.metadata().get("entityId")).isEqualTo(COMMIT_ID.toString());
		assertThat(chunk.metadata().get("userId")).isEqualTo(USER_ID.toString());
		assertThat(chunk.metadata().get("teamId")).isEqualTo(TEAM_ID.toString());
		assertThat(chunk.metadata().get("planId")).isEqualTo(PLAN_ID.toString());
		assertThat(chunk.metadata().get("weekStartDate")).isEqualTo(WEEK.toString());
	}

	@Test
	void buildCommitChunk_planIdComesFromPlan() {
		WeeklyCommit commit = commit(COMMIT_ID, "Task A");
		commit.setPlanId(UUID.randomUUID());
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.metadata().get("planId")).isEqualTo(PLAN_ID.toString());
	}

	@Test
	void buildCommitChunk_nullDescription_doesNotThrow() {
		WeeklyCommit commit = commit(COMMIT_ID, "Nullable desc");
		commit.setDescription(null);
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.text()).contains("Description: ");
		assertThat(chunk).isNotNull();
	}

	@Test
	void buildCommitChunk_nullOutcome_doesNotThrow() {
		WeeklyCommit commit = commit(COMMIT_ID, "No outcome yet");
		commit.setOutcome(null);
		commit.setOutcomeNotes(null);
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.text()).contains("Outcome: ");
		assertThat(chunk.text()).contains("Notes: ");
	}

	@Test
	void buildCommitChunk_nullEstimatePoints_usesQuestionMark() {
		WeeklyCommit commit = commit(COMMIT_ID, "No estimate");
		commit.setEstimatePoints(null);
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildCommitChunk(commit, plan);

		assertThat(chunk.text()).contains("?pts");
	}

	// ── buildScopeChangeChunk ─────────────────────────────────────────────

	@Test
	void buildScopeChangeChunk_idHasCorrectFormat() {
		UUID eventId = UUID.randomUUID();
		ScopeChangeEvent event = scopeChangeEvent(eventId);
		WeeklyCommit commit = commit(COMMIT_ID, "Scoped commit");

		ChunkBuilder.ChunkData chunk = builder.buildScopeChangeChunk(event, commit);

		assertThat(chunk.id()).isEqualTo("scope_change:" + eventId);
	}

	@Test
	void buildScopeChangeChunk_textContainsCategoryAndCommitTitle() {
		UUID eventId = UUID.randomUUID();
		ScopeChangeEvent event = scopeChangeEvent(eventId);
		event.setCategory(ScopeChangeCategory.COMMIT_ADDED);
		event.setReason("New requirement from stakeholder");
		WeeklyCommit commit = commit(COMMIT_ID, "Add auth module");

		ChunkBuilder.ChunkData chunk = builder.buildScopeChangeChunk(event, commit);

		assertThat(chunk.text()).contains("COMMIT_ADDED");
		assertThat(chunk.text()).contains("\"Add auth module\"");
		assertThat(chunk.text()).contains("New requirement from stakeholder");
	}

	@Test
	void buildScopeChangeChunk_metadataEntityType() {
		UUID eventId = UUID.randomUUID();
		ScopeChangeEvent event = scopeChangeEvent(eventId);

		ChunkBuilder.ChunkData chunk = builder.buildScopeChangeChunk(event, commit(COMMIT_ID, "Title"));

		assertThat(chunk.metadata().get("entityType")).isEqualTo("scope_change");
	}

	// ── buildCarryForwardChunk ────────────────────────────────────────────

	@Test
	void buildCarryForwardChunk_idHasCorrectFormat() {
		UUID linkId = UUID.randomUUID();
		CarryForwardLink link = carryForwardLink(linkId, COMMIT_ID, UUID.randomUUID());
		WeeklyCommit sourceCommit = commit(COMMIT_ID, "Blocked feature");

		ChunkBuilder.ChunkData chunk = builder.buildCarryForwardChunk(link, sourceCommit);

		assertThat(chunk.id()).isEqualTo("carry_forward:" + linkId);
	}

	@Test
	void buildCarryForwardChunk_textContainsReasonAndStreak() {
		UUID linkId = UUID.randomUUID();
		CarryForwardLink link = carryForwardLink(linkId, COMMIT_ID, UUID.randomUUID());
		link.setReason(CarryForwardReason.BLOCKED_BY_DEPENDENCY);
		link.setReasonNotes("Waiting on API team");
		WeeklyCommit sourceCommit = commit(COMMIT_ID, "Integration work");
		sourceCommit.setCarryForwardStreak(2);

		ChunkBuilder.ChunkData chunk = builder.buildCarryForwardChunk(link, sourceCommit);

		assertThat(chunk.text()).contains("BLOCKED_BY_DEPENDENCY");
		assertThat(chunk.text()).contains("2 week(s)");
		assertThat(chunk.text()).contains("Waiting on API team");
		assertThat(chunk.text()).contains("\"Integration work\"");
	}

	@Test
	void buildCarryForwardChunk_metadataEntityType() {
		UUID linkId = UUID.randomUUID();
		CarryForwardLink link = carryForwardLink(linkId, COMMIT_ID, UUID.randomUUID());

		ChunkBuilder.ChunkData chunk = builder.buildCarryForwardChunk(link, commit(COMMIT_ID, "X"));

		assertThat(chunk.metadata().get("entityType")).isEqualTo("carry_forward");
		assertThat(chunk.metadata().get("sourceCommitId")).isEqualTo(COMMIT_ID.toString());
	}

	@Test
	void buildCarryForwardChunk_nullReasonNotes_doesNotThrow() {
		UUID linkId = UUID.randomUUID();
		CarryForwardLink link = carryForwardLink(linkId, COMMIT_ID, UUID.randomUUID());
		link.setReason(CarryForwardReason.UNDERESTIMATED);
		link.setReasonNotes(null);

		ChunkBuilder.ChunkData chunk = builder.buildCarryForwardChunk(link, commit(COMMIT_ID, "Task"));

		assertThat(chunk.text()).contains("Notes: ");
		assertThat(chunk).isNotNull();
	}

	// ── buildManagerCommentChunk ──────────────────────────────────────────

	@Test
	void buildManagerCommentChunk_idHasCorrectFormat() {
		UUID commentId = UUID.randomUUID();
		ManagerComment comment = managerComment(commentId);
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildManagerCommentChunk(comment, plan);

		assertThat(chunk.id()).isEqualTo("manager_comment:" + commentId);
	}

	@Test
	void buildManagerCommentChunk_textContainsWeekAndContent() {
		UUID commentId = UUID.randomUUID();
		ManagerComment comment = managerComment(commentId);
		comment.setContent("Great progress this week!");
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildManagerCommentChunk(comment, plan);

		assertThat(chunk.text()).contains(WEEK.toString());
		assertThat(chunk.text()).contains("Great progress this week!");
	}

	@Test
	void buildManagerCommentChunk_metadataContainsWeekStartDate() {
		UUID commentId = UUID.randomUUID();
		ManagerComment comment = managerComment(commentId);
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildManagerCommentChunk(comment, plan);

		assertThat(chunk.metadata().get("entityType")).isEqualTo("manager_comment");
		assertThat(chunk.metadata().get("weekStartDate")).isEqualTo(WEEK.toString());
	}

	// ── buildWorkItemChunk ────────────────────────────────────────────────

	@Test
	void buildWorkItemChunk_idHasCorrectFormat() {
		UUID itemId = UUID.randomUUID();
		WorkItem item = workItem(itemId);

		ChunkBuilder.ChunkData chunk = builder.buildWorkItemChunk(item);

		assertThat(chunk.id()).isEqualTo("ticket:" + itemId);
	}

	@Test
	void buildWorkItemChunk_textContainsKeyTitleStatusDescription() {
		UUID itemId = UUID.randomUUID();
		WorkItem item = workItem(itemId);
		item.setKey("WC-42");
		item.setTitle("Fix memory leak");
		item.setDescription("Memory leak in event loop");
		item.setStatus(TicketStatus.IN_PROGRESS);

		ChunkBuilder.ChunkData chunk = builder.buildWorkItemChunk(item);

		assertThat(chunk.text()).contains("WC-42");
		assertThat(chunk.text()).contains("Fix memory leak");
		assertThat(chunk.text()).contains("Memory leak in event loop");
		assertThat(chunk.text()).contains("IN_PROGRESS");
	}

	@Test
	void buildWorkItemChunk_metadataEntityType() {
		UUID itemId = UUID.randomUUID();
		WorkItem item = workItem(itemId);

		ChunkBuilder.ChunkData chunk = builder.buildWorkItemChunk(item);

		assertThat(chunk.metadata().get("entityType")).isEqualTo("ticket");
		assertThat(chunk.metadata().get("teamId")).isEqualTo(TEAM_ID.toString());
	}

	@Test
	void buildWorkItemChunk_nullDescription_doesNotThrow() {
		UUID itemId = UUID.randomUUID();
		WorkItem item = workItem(itemId);
		item.setDescription(null);

		ChunkBuilder.ChunkData chunk = builder.buildWorkItemChunk(item);

		assertThat(chunk.text()).contains("Description: ");
		assertThat(chunk).isNotNull();
	}

	// ── buildPlanSummaryChunk ─────────────────────────────────────────────

	@Test
	void buildPlanSummaryChunk_idHasCorrectFormat() {
		WeeklyPlan plan = plan(PLAN_ID);
		List<WeeklyCommit> commits = List.of(commit(COMMIT_ID, "C1"));

		ChunkBuilder.ChunkData chunk = builder.buildPlanSummaryChunk(plan, commits);

		assertThat(chunk.id()).isEqualTo("plan_summary:" + PLAN_ID);
	}

	@Test
	void buildPlanSummaryChunk_textContainsCountPointsState() {
		WeeklyPlan plan = plan(PLAN_ID);
		plan.setState(PlanState.RECONCILED);

		WeeklyCommit c1 = commit(UUID.randomUUID(), "Commit 1");
		c1.setEstimatePoints(3);
		WeeklyCommit c2 = commit(UUID.randomUUID(), "Commit 2");
		c2.setEstimatePoints(5);

		ChunkBuilder.ChunkData chunk = builder.buildPlanSummaryChunk(plan, List.of(c1, c2));

		assertThat(chunk.text()).contains("week of " + WEEK);
		assertThat(chunk.text()).contains("2 commits");
		assertThat(chunk.text()).contains("8pts planned");
		assertThat(chunk.text()).contains("RECONCILED");
		// Verify inline commit titles are present
		assertThat(chunk.text()).contains("\"Commit 1\"");
		assertThat(chunk.text()).contains("\"Commit 2\"");
	}

	@Test
	void buildPlanSummaryChunk_nullEstimatePointsTreatedAsZero() {
		WeeklyPlan plan = plan(PLAN_ID);
		WeeklyCommit c1 = commit(UUID.randomUUID(), "No estimate");
		c1.setEstimatePoints(null);
		WeeklyCommit c2 = commit(UUID.randomUUID(), "Has estimate");
		c2.setEstimatePoints(5);

		ChunkBuilder.ChunkData chunk = builder.buildPlanSummaryChunk(plan, List.of(c1, c2));

		assertThat(chunk.text()).contains("5pts");
	}

	@Test
	void buildPlanSummaryChunk_emptyCommitsList() {
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildPlanSummaryChunk(plan, List.of());

		assertThat(chunk.text()).contains("0 commits");
		assertThat(chunk.text()).contains("0pts");
	}

	@Test
	void buildPlanSummaryChunk_nullCommitsList() {
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildPlanSummaryChunk(plan, null);

		assertThat(chunk.text()).contains("0 commits");
	}

	@Test
	void buildPlanSummaryChunk_metadataContainsRequiredKeys() {
		WeeklyPlan plan = plan(PLAN_ID);

		ChunkBuilder.ChunkData chunk = builder.buildPlanSummaryChunk(plan, List.of());

		assertThat(chunk.metadata().get("entityType")).isEqualTo("plan_summary");
		assertThat(chunk.metadata().get("entityId")).isEqualTo(PLAN_ID.toString());
		assertThat(chunk.metadata().get("teamId")).isEqualTo(TEAM_ID.toString());
		assertThat(chunk.metadata().get("weekStartDate")).isEqualTo(WEEK.toString());
	}

	// ── helpers ───────────────────────────────────────────────────────────

	private WeeklyCommit commit(UUID id, String title) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(id);
		c.setTitle(title);
		c.setOwnerUserId(USER_ID);
		c.setPlanId(PLAN_ID);
		c.setChessPiece(ChessPiece.PAWN);
		c.setEstimatePoints(1);
		c.setPriorityOrder(1);
		return c;
	}

	private WeeklyPlan plan(UUID id) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(id);
		p.setOwnerUserId(USER_ID);
		p.setTeamId(TEAM_ID);
		p.setWeekStartDate(WEEK);
		p.setState(PlanState.DRAFT);
		p.setLockDeadline(java.time.Instant.now().plusSeconds(3600));
		p.setReconcileDeadline(java.time.Instant.now().plusSeconds(7200));
		return p;
	}

	private ScopeChangeEvent scopeChangeEvent(UUID id) {
		ScopeChangeEvent e = new ScopeChangeEvent();
		e.setId(id);
		e.setPlanId(PLAN_ID);
		e.setCommitId(COMMIT_ID);
		e.setChangedByUserId(USER_ID);
		e.setCategory(ScopeChangeCategory.COMMIT_REMOVED);
		e.setReason("Deprioritised");
		return e;
	}

	private CarryForwardLink carryForwardLink(UUID id, UUID sourceId, UUID targetId) {
		CarryForwardLink link = new CarryForwardLink();
		link.setId(id);
		link.setSourceCommitId(sourceId);
		link.setTargetCommitId(targetId);
		link.setReason(CarryForwardReason.STILL_IN_PROGRESS);
		return link;
	}

	private ManagerComment managerComment(UUID id) {
		ManagerComment c = new ManagerComment();
		c.setId(id);
		c.setPlanId(PLAN_ID);
		c.setAuthorUserId(USER_ID);
		c.setContent("Good work!");
		return c;
	}

	private WorkItem workItem(UUID id) {
		WorkItem item = new WorkItem();
		item.setId(id);
		item.setTeamId(TEAM_ID);
		item.setKey("WC-1");
		item.setTitle("Sample ticket");
		item.setStatus(TicketStatus.TODO);
		item.setReporterUserId(USER_ID);
		return item;
	}
}
