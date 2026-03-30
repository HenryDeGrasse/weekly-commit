package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.carryforward.dto.CarryForwardLineageResponse;
import com.weeklycommit.carryforward.dto.CarryForwardResponse;
import com.weeklycommit.carryforward.service.CarryForwardService;
import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.reconcile.service.ReconciliationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test: carry-forward with provenance, lineage traversal, and
 * carry-forward into locked plans.
 */
class CarryForwardIntegrationTest extends IntegrationTestBase {

	@Autowired
	private LockService lockService;
	@Autowired
	private ReconciliationService reconciliationService;
	@Autowired
	private CarryForwardService carryForwardService;

	private Organization org;
	private Team team;
	private UserAccount user;
	private RcdoNode outcome;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "cf@example.com", "CF User", "IC");
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		outcome = createOutcome(defObj, team, "Outcome");
	}

	@Test
	void carryForward_createsNewCommitWithProvenance() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		WeeklyPlan plan1 = createDraftPlan(user, team, week1);
		WeeklyCommit source = createCommit(plan1, user, "Unfinished work", ChessPiece.ROOK, 1, outcome, 5);

		// Lock and reconcile week 1
		lockService.lockPlan(plan1.getId(), user.getId());
		reconciliationService.openReconciliation(plan1.getId());
		reconciliationService.setCommitOutcome(plan1.getId(), source.getId(), CommitOutcome.NOT_ACHIEVED,
				"Blocked by external team");
		reconciliationService.submitReconciliation(plan1.getId());

		// Carry forward into week 2
		CarryForwardResponse resp = carryForwardService.carryForward(plan1.getId(), source.getId(), week2,
				CarryForwardReason.BLOCKED_BY_DEPENDENCY, "External team delayed", user.getId());

		assertThat(resp.newCommit()).isNotNull();
		assertThat(resp.link()).isNotNull();
		assertThat(resp.postLockAdded()).isFalse(); // target plan was created as DRAFT

		// Verify new commit
		WeeklyCommit newCommit = commitRepo.findById(resp.newCommit().id()).orElseThrow();
		assertThat(newCommit.getTitle()).isEqualTo("Unfinished work");
		assertThat(newCommit.getChessPiece()).isEqualTo(ChessPiece.ROOK);
		assertThat(newCommit.getEstimatePoints()).isEqualTo(5);
		assertThat(newCommit.getCarryForwardSourceId()).isEqualTo(source.getId());
		assertThat(newCommit.getCarryForwardStreak()).isEqualTo(1);
		assertThat(newCommit.getRcdoNodeId()).isEqualTo(outcome.getId());

		// Verify provenance link
		CarryForwardLink link = cfLinkRepo.findByTargetCommitId(newCommit.getId()).orElseThrow();
		assertThat(link.getSourceCommitId()).isEqualTo(source.getId());
		assertThat(link.getReason()).isEqualTo(CarryForwardReason.BLOCKED_BY_DEPENDENCY);
		assertThat(link.getReasonNotes()).isEqualTo("External team delayed");
	}

	@Test
	void carryForward_incrementsStreakCorrectly() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		LocalDate week3 = week2.plusWeeks(1);
		WeeklyPlan plan1 = createDraftPlan(user, team, week1);
		WeeklyCommit c = createCommit(plan1, user, "Long saga", ChessPiece.ROOK, 1, outcome, 3);

		// Complete lifecycle for week 1
		lockService.lockPlan(plan1.getId(), user.getId());
		reconciliationService.openReconciliation(plan1.getId());
		reconciliationService.setCommitOutcome(plan1.getId(), c.getId(), CommitOutcome.NOT_ACHIEVED, "Still blocked");
		reconciliationService.submitReconciliation(plan1.getId());

		// Carry forward to week 2
		CarryForwardResponse r1 = carryForwardService.carryForward(plan1.getId(), c.getId(), week2,
				CarryForwardReason.STILL_IN_PROGRESS, null, user.getId());
		assertThat(r1.newCommit().carryForwardStreak()).isEqualTo(1);

		// Complete lifecycle for week 2
		WeeklyPlan plan2 = planRepo.findByOwnerUserIdAndWeekStartDate(user.getId(), week2).orElseThrow();
		WeeklyCommit c2 = commitRepo.findById(r1.newCommit().id()).orElseThrow();
		lockService.lockPlan(plan2.getId(), user.getId());
		reconciliationService.openReconciliation(plan2.getId());
		reconciliationService.setCommitOutcome(plan2.getId(), c2.getId(), CommitOutcome.NOT_ACHIEVED, "Still going");
		reconciliationService.submitReconciliation(plan2.getId());

		// Carry forward to week 3
		CarryForwardResponse r2 = carryForwardService.carryForward(plan2.getId(), c2.getId(), week3,
				CarryForwardReason.STILL_IN_PROGRESS, null, user.getId());
		assertThat(r2.newCommit().carryForwardStreak()).isEqualTo(2);
	}

	@Test
	void carryForwardLineage_traversesFullChain() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		LocalDate week3 = week2.plusWeeks(1);
		WeeklyPlan plan1 = createDraftPlan(user, team, week1);
		WeeklyCommit original = createCommit(plan1, user, "Root commit", ChessPiece.ROOK, 1, outcome, 3);

		// Week 1 → Week 2
		lockService.lockPlan(plan1.getId(), user.getId());
		reconciliationService.openReconciliation(plan1.getId());
		reconciliationService.setCommitOutcome(plan1.getId(), original.getId(), CommitOutcome.NOT_ACHIEVED, "N/A");
		reconciliationService.submitReconciliation(plan1.getId());

		CarryForwardResponse r1 = carryForwardService.carryForward(plan1.getId(), original.getId(), week2,
				CarryForwardReason.STILL_IN_PROGRESS, null, user.getId());

		// Week 2 → Week 3
		WeeklyPlan plan2 = planRepo.findByOwnerUserIdAndWeekStartDate(user.getId(), week2).orElseThrow();
		WeeklyCommit c2 = commitRepo.findById(r1.newCommit().id()).orElseThrow();
		lockService.lockPlan(plan2.getId(), user.getId());
		reconciliationService.openReconciliation(plan2.getId());
		reconciliationService.setCommitOutcome(plan2.getId(), c2.getId(), CommitOutcome.NOT_ACHIEVED, "N/A");
		reconciliationService.submitReconciliation(plan2.getId());

		CarryForwardResponse r2 = carryForwardService.carryForward(plan2.getId(), c2.getId(), week3,
				CarryForwardReason.STILL_IN_PROGRESS, null, user.getId());

		// Get lineage from the middle commit — should see entire chain
		CarryForwardLineageResponse lineage = carryForwardService.getCarryForwardLineage(c2.getId());
		assertThat(lineage.rootCommitId()).isEqualTo(original.getId());
		assertThat(lineage.chain()).hasSize(2); // original→c2, c2→c3
	}

	@Test
	void carryForwardIntoLockedPlan_createsScopeChangeEvent() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();

		// Create and lock week 2 plan first
		WeeklyPlan plan2 = createDraftPlan(user, team, week2);
		createCommit(plan2, user, "Existing commit", ChessPiece.PAWN, 1, outcome, 2);
		lockService.lockPlan(plan2.getId(), user.getId());

		// Create and complete week 1
		WeeklyPlan plan1 = createDraftPlan(user, team, week1);
		WeeklyCommit source = createCommit(plan1, user, "Needs carry-forward", ChessPiece.ROOK, 1, outcome, 3);
		lockService.lockPlan(plan1.getId(), user.getId());
		reconciliationService.openReconciliation(plan1.getId());
		reconciliationService.setCommitOutcome(plan1.getId(), source.getId(), CommitOutcome.NOT_ACHIEVED, "Blocked");
		reconciliationService.submitReconciliation(plan1.getId());

		// Carry forward into the already-locked week 2 plan
		CarryForwardResponse resp = carryForwardService.carryForward(plan1.getId(), source.getId(), week2,
				CarryForwardReason.BLOCKED_BY_DEPENDENCY, null, user.getId());

		assertThat(resp.postLockAdded()).isTrue();

		// Verify scope change event
		List<ScopeChangeEvent> events = scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(plan2.getId());
		assertThat(events).isNotEmpty();
		assertThat(events.stream().anyMatch(e -> e.getCategory() == ScopeChangeCategory.COMMIT_ADDED)).isTrue();
	}

	@Test
	void carryForward_lazilyCreatesTargetPlan() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		WeeklyPlan plan1 = createDraftPlan(user, team, week1);
		WeeklyCommit source = createCommit(plan1, user, "Carry me", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan1.getId(), user.getId());
		reconciliationService.openReconciliation(plan1.getId());
		reconciliationService.setCommitOutcome(plan1.getId(), source.getId(), CommitOutcome.NOT_ACHIEVED, "Nope");
		reconciliationService.submitReconciliation(plan1.getId());

		// No plan exists for week 2 yet
		assertThat(planRepo.findByOwnerUserIdAndWeekStartDate(user.getId(), week2)).isEmpty();

		// Carry forward should create the plan
		carryForwardService.carryForward(plan1.getId(), source.getId(), week2, CarryForwardReason.STILL_IN_PROGRESS,
				null, user.getId());

		assertThat(planRepo.findByOwnerUserIdAndWeekStartDate(user.getId(), week2)).isPresent();
	}
}
