package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.reconcile.dto.AddCommitData;
import com.weeklycommit.reconcile.dto.EditCommitChanges;
import com.weeklycommit.reconcile.dto.ScopeChangeEventResponse;
import com.weeklycommit.reconcile.dto.ScopeChangeTimelineResponse;
import com.weeklycommit.reconcile.service.ScopeChangeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test: post-lock scope changes — add, remove, edit commits after
 * lock, verify append-only events are created.
 */
class ScopeChangeIntegrationTest extends IntegrationTestBase {

	@Autowired
	private LockService lockService;
	@Autowired
	private ScopeChangeService scopeChangeService;

	private Organization org;
	private Team team;
	private UserAccount user;
	private RcdoNode outcome;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "scope@example.com", "Scope User", "IC");
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		outcome = createOutcome(defObj, team, "Outcome");
	}

	@Test
	void addCommitAfterLock_createsScopeChangeEvent() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Original", ChessPiece.PAWN, 1, outcome, 2);
		lockService.lockPlan(plan.getId(), user.getId());

		AddCommitData data = new AddCommitData("New urgent commit", ChessPiece.ROOK, "Emergency fix", outcome.getId(),
				null, 3, null);
		ScopeChangeTimelineResponse resp = scopeChangeService.addPostLockCommit(plan.getId(), data, "Priority changed",
				user.getId());

		assertThat(resp.events()).isNotEmpty();
		assertThat(resp.events().stream().anyMatch(e -> ScopeChangeCategory.COMMIT_ADDED == e.category())).isTrue();

		// Verify event in DB
		List<ScopeChangeEvent> events = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId());
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getCategory()).isEqualTo(ScopeChangeCategory.COMMIT_ADDED);
		assertThat(events.get(0).getReason()).isEqualTo("Priority changed");
		assertThat(events.get(0).getChangedByUserId()).isEqualTo(user.getId());
		assertThat(events.get(0).getCreatedAt()).isNotNull();

		// Verify commit was actually created
		List<WeeklyCommit> allCommits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		assertThat(allCommits).hasSize(2);
		assertThat(allCommits.stream().anyMatch(c -> c.getTitle().equals("New urgent commit"))).isTrue();
	}

	@Test
	void removeCommitAfterLock_createsScopeChangeEvent() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Keep", ChessPiece.PAWN, 1, outcome, 2);
		WeeklyCommit c2 = createCommit(plan, user, "Remove me", ChessPiece.ROOK, 2, outcome, 3);
		lockService.lockPlan(plan.getId(), user.getId());

		scopeChangeService.removePostLockCommit(plan.getId(), c2.getId(), "De-scoped", user.getId());

		List<ScopeChangeEvent> events = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId());
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getCategory()).isEqualTo(ScopeChangeCategory.COMMIT_REMOVED);
		assertThat(events.get(0).getCommitId()).isEqualTo(c2.getId());

		// Commit should be soft-deleted (outcome = CANCELED)
		WeeklyCommit removed = commitRepo.findById(c2.getId()).orElseThrow();
		assertThat(removed.getOutcome()).isEqualTo(CommitOutcome.CANCELED);
	}

	@Test
	void editCommitAfterLock_createsChangeEvent() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Original title", ChessPiece.PAWN, 1, outcome, 2);
		lockService.lockPlan(plan.getId(), user.getId());

		EditCommitChanges changes = new EditCommitChanges(5, null, null, null);
		scopeChangeService.editPostLockCommit(plan.getId(), c.getId(), changes, "Scope grew", user.getId());

		List<ScopeChangeEvent> events = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId());
		assertThat(events).isNotEmpty();
		assertThat(events.get(0).getCommitId()).isEqualTo(c.getId());
		assertThat(events.get(0).getCategory()).isEqualTo(ScopeChangeCategory.ESTIMATE_CHANGED);
		assertThat(events.get(0).getReason()).isEqualTo("Scope grew");

		// Verify the commit was actually updated
		WeeklyCommit updated = commitRepo.findById(c.getId()).orElseThrow();
		assertThat(updated.getEstimatePoints()).isEqualTo(5);
	}

	@Test
	void multipleChanges_areAllAppendOnly() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c1 = createCommit(plan, user, "Commit A", ChessPiece.PAWN, 1, outcome, 2);
		WeeklyCommit c2 = createCommit(plan, user, "Commit B", ChessPiece.ROOK, 2, outcome, 3);
		lockService.lockPlan(plan.getId(), user.getId());

		// Multiple scope changes
		AddCommitData addData = new AddCommitData("Added C", ChessPiece.BISHOP, null, outcome.getId(), null, 2, null);
		scopeChangeService.addPostLockCommit(plan.getId(), addData, "New priority", user.getId());
		scopeChangeService.removePostLockCommit(plan.getId(), c2.getId(), "De-scoped", user.getId());
		EditCommitChanges editChanges = new EditCommitChanges(5, null, null, null);
		scopeChangeService.editPostLockCommit(plan.getId(), c1.getId(), editChanges, "Grew", user.getId());

		List<ScopeChangeEvent> events = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan.getId());
		assertThat(events).hasSize(3);

		// Events are ordered by creation time (append-only)
		for (int i = 1; i < events.size(); i++) {
			assertThat(events.get(i).getCreatedAt()).isAfterOrEqualTo(events.get(i - 1).getCreatedAt());
		}
	}

	@Test
	void scopeChangeTimeline_containsAllEventTypes() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);
		lockService.lockPlan(plan.getId(), user.getId());

		AddCommitData addData = new AddCommitData("New", ChessPiece.KNIGHT, null, outcome.getId(), null, 3, null);
		scopeChangeService.addPostLockCommit(plan.getId(), addData, "Added", user.getId());
		scopeChangeService.removePostLockCommit(plan.getId(), c.getId(), "Removed", user.getId());

		ScopeChangeTimelineResponse timeline = scopeChangeService.getChangeTimeline(plan.getId());
		assertThat(timeline.events()).hasSize(2);
		assertThat(timeline.events().stream().map(ScopeChangeEventResponse::category))
				.containsExactlyInAnyOrder(ScopeChangeCategory.COMMIT_ADDED, ScopeChangeCategory.COMMIT_REMOVED);
	}

	@Test
	void kingScopeChange_triggersManagerException() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);
		lockService.lockPlan(plan.getId(), user.getId());

		// Add a King after lock
		AddCommitData kingData = new AddCommitData("Critical King", ChessPiece.KING, null, outcome.getId(), null, 8,
				"Must not fail");
		scopeChangeService.addPostLockCommit(plan.getId(), kingData, "Emergency requirement", user.getId());

		ScopeChangeTimelineResponse timeline = scopeChangeService.getChangeTimeline(plan.getId());
		assertThat(timeline.managerExceptions()).isNotEmpty();
		assertThat(timeline.managerExceptions().stream().anyMatch(e -> "KING_CHANGE".equals(e.type()))).isTrue();
	}
}
