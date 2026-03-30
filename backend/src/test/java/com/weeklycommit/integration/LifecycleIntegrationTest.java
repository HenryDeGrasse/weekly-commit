package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.reconcile.service.ReconciliationService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test: full DRAFT → LOCKED → RECONCILING → RECONCILED lifecycle
 * against a real H2 database.
 */
class LifecycleIntegrationTest extends IntegrationTestBase {

	@Autowired
	private LockService lockService;
	@Autowired
	private ReconciliationService reconciliationService;

	private Organization org;
	private Team team;
	private UserAccount user;
	private RcdoNode outcome;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "test@example.com", "Test User", "IC");
		RcdoNode rally = createRallyCry(team, "Rally Cry");
		RcdoNode defObj = createDefiningObjective(rally, team, "Defining Objective");
		outcome = createOutcome(defObj, team, "Outcome");
	}

	@Test
	void fullLifecycle_draftToReconciled() {
		LocalDate weekStart = thisMonday();
		WeeklyPlan plan = createDraftPlan(user, team, weekStart);

		// Start as DRAFT
		assertThat(plan.getState()).isEqualTo(PlanState.DRAFT);

		// Add commits
		WeeklyCommit c1 = createCommit(plan, user, "Commit 1", ChessPiece.KING, 1, outcome, 5,
				"Deploy to prod without downtime");
		WeeklyCommit c2 = createCommit(plan, user, "Commit 2", ChessPiece.ROOK, 2, outcome, 3);

		// Lock the plan
		LockResponse lockResp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(lockResp.success()).isTrue();

		// Verify state transition
		WeeklyPlan locked = planRepo.findById(plan.getId()).orElseThrow();
		assertThat(locked.getState()).isEqualTo(PlanState.LOCKED);

		// Verify lock snapshot was created
		assertThat(lockHeaderRepo.findByPlanId(plan.getId())).isPresent();

		// Open reconciliation → RECONCILING
		reconciliationService.openReconciliation(plan.getId());
		WeeklyPlan reconciling = planRepo.findById(plan.getId()).orElseThrow();
		assertThat(reconciling.getState()).isEqualTo(PlanState.RECONCILING);

		// Set outcomes on all commits
		reconciliationService.setCommitOutcome(plan.getId(), c1.getId(), CommitOutcome.ACHIEVED, null);
		reconciliationService.setCommitOutcome(plan.getId(), c2.getId(), CommitOutcome.PARTIALLY_ACHIEVED,
				"80% complete");

		// Submit reconciliation → RECONCILED
		reconciliationService.submitReconciliation(plan.getId());
		WeeklyPlan reconciled = planRepo.findById(plan.getId()).orElseThrow();
		assertThat(reconciled.getState()).isEqualTo(PlanState.RECONCILED);

		// Verify reconcile snapshot was created
		assertThat(reconcileHeaderRepo.findByPlanId(plan.getId())).isPresent();
	}

	@Test
	void lockPlan_isIdempotent() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		LockResponse first = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(first.success()).isTrue();

		// Second lock on same plan is idempotent
		LockResponse second = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(second.success()).isTrue();

		// Only one snapshot
		assertThat(lockHeaderRepo.findByPlanId(plan.getId())).isPresent();
	}

	@Test
	void cannotLock_reconciledPlan() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan.getId(), user.getId());
		reconciliationService.openReconciliation(plan.getId());
		reconciliationService.setCommitOutcome(plan.getId(), c.getId(), CommitOutcome.ACHIEVED, null);
		reconciliationService.submitReconciliation(plan.getId());

		// Now try to lock a RECONCILED plan
		assertThatThrownBy(() -> lockService.lockPlan(plan.getId(), user.getId()))
				.isInstanceOf(PlanValidationException.class);
	}

	@Test
	void cannotReconcile_draftPlan() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		assertThatThrownBy(() -> reconciliationService.openReconciliation(plan.getId()))
				.isInstanceOf(PlanValidationException.class);
	}

	@Test
	void cannotSetOutcome_onLockedPlan() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);
		lockService.lockPlan(plan.getId(), user.getId());

		// Plan is LOCKED, not RECONCILING
		assertThatThrownBy(
				() -> reconciliationService.setCommitOutcome(plan.getId(), c.getId(), CommitOutcome.ACHIEVED, null))
				.isInstanceOf(PlanValidationException.class);
	}

	@Test
	void cannotSubmitReconciliation_withMissingOutcomes() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit 1", ChessPiece.PAWN, 1, outcome, 2);
		createCommit(plan, user, "Commit 2", ChessPiece.ROOK, 2, outcome, 3);

		lockService.lockPlan(plan.getId(), user.getId());
		reconciliationService.openReconciliation(plan.getId());

		// Only set outcome on one commit
		WeeklyCommit c1 = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId()).get(0);
		reconciliationService.setCommitOutcome(plan.getId(), c1.getId(), CommitOutcome.ACHIEVED, null);

		assertThatThrownBy(() -> reconciliationService.submitReconciliation(plan.getId()))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("outcome");
	}

	@Test
	void cannotSubmitReconciliation_twice() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan.getId(), user.getId());
		reconciliationService.openReconciliation(plan.getId());
		reconciliationService.setCommitOutcome(plan.getId(), c.getId(), CommitOutcome.ACHIEVED, null);
		reconciliationService.submitReconciliation(plan.getId());

		// Cannot submit again — plan is RECONCILED
		assertThatThrownBy(() -> reconciliationService.submitReconciliation(plan.getId()))
				.isInstanceOf(PlanValidationException.class);
	}

	@Test
	void openReconciliation_isIdempotent() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan.getId(), user.getId());
		reconciliationService.openReconciliation(plan.getId());
		// Second call is idempotent
		reconciliationService.openReconciliation(plan.getId());

		WeeklyPlan result = planRepo.findById(plan.getId()).orElseThrow();
		assertThat(result.getState()).isEqualTo(PlanState.RECONCILING);
	}

	@Test
	void outcomesRequireNotes_forPartialAndNotAchieved() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan.getId(), user.getId());
		reconciliationService.openReconciliation(plan.getId());

		// PARTIALLY_ACHIEVED without notes should fail
		assertThatThrownBy(() -> reconciliationService.setCommitOutcome(plan.getId(), c.getId(),
				CommitOutcome.PARTIALLY_ACHIEVED, null)).isInstanceOf(PlanValidationException.class);

		// NOT_ACHIEVED without notes should fail
		assertThatThrownBy(
				() -> reconciliationService.setCommitOutcome(plan.getId(), c.getId(), CommitOutcome.NOT_ACHIEVED, ""))
				.isInstanceOf(PlanValidationException.class);

		// CANCELED without notes should fail
		assertThatThrownBy(
				() -> reconciliationService.setCommitOutcome(plan.getId(), c.getId(), CommitOutcome.CANCELED, null))
				.isInstanceOf(PlanValidationException.class);

		// ACHIEVED without notes is fine
		reconciliationService.setCommitOutcome(plan.getId(), c.getId(), CommitOutcome.ACHIEVED, null);
		WeeklyCommit updated = commitRepo.findById(c.getId()).orElseThrow();
		assertThat(updated.getOutcome()).isEqualTo(CommitOutcome.ACHIEVED);
	}
}
