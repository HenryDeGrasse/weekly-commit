package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.lock.service.LockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test: lock validation rules — chess piece limits, required
 * fields, estimate points constraints.
 */
class LockValidationIntegrationTest extends IntegrationTestBase {

	@Autowired
	private LockService lockService;

	private Organization org;
	private Team team;
	private UserAccount user;
	private RcdoNode outcome;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "val@example.com", "Val User", "IC");
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		outcome = createOutcome(defObj, team, "Outcome");
	}

	@Test
	void emptyPlan_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		// No commits
		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors()).isNotEmpty();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("At least one commit"))).isTrue();
	}

	@Test
	void missingRcdoLink_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "No RCDO", ChessPiece.PAWN, 1, null, 2); // null RCDO

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("RCDO"))).isTrue();
	}

	@Test
	void missingEstimatePoints_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = new WeeklyCommit();
		c.setPlanId(plan.getId());
		c.setOwnerUserId(user.getId());
		c.setTitle("No estimate");
		c.setChessPiece(ChessPiece.PAWN);
		c.setPriorityOrder(1);
		c.setRcdoNodeId(outcome.getId());
		// estimatePoints left null
		commitRepo.save(c);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("Estimate points"))).isTrue();
	}

	@Test
	void invalidEstimatePoints_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = new WeeklyCommit();
		c.setPlanId(plan.getId());
		c.setOwnerUserId(user.getId());
		c.setTitle("Bad estimate");
		c.setChessPiece(ChessPiece.PAWN);
		c.setPriorityOrder(1);
		c.setRcdoNodeId(outcome.getId());
		c.setEstimatePoints(4); // invalid — must be 1, 2, 3, 5, or 8
		commitRepo.save(c);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("{1, 2, 3, 5, 8}"))).isTrue();
	}

	@Test
	void twoKings_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "King 1", ChessPiece.KING, 1, outcome, 5, "Criteria 1");
		createCommit(plan, user, "King 2", ChessPiece.KING, 2, outcome, 5, "Criteria 2");

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("Maximum 1 King"))).isTrue();
	}

	@Test
	void threeQueens_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Queen 1", ChessPiece.QUEEN, 1, outcome, 5, "Criteria 1");
		createCommit(plan, user, "Queen 2", ChessPiece.QUEEN, 2, outcome, 5, "Criteria 2");
		createCommit(plan, user, "Queen 3", ChessPiece.QUEEN, 3, outcome, 5, "Criteria 3");

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("Maximum 2 Queen"))).isTrue();
	}

	@Test
	void kingWithoutSuccessCriteria_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "King no criteria", ChessPiece.KING, 1, outcome, 5, null);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("Success criteria required for KING")))
				.isTrue();
	}

	@Test
	void queenWithoutSuccessCriteria_failsValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Queen no criteria", ChessPiece.QUEEN, 1, outcome, 5, null);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isFalse();
		assertThat(resp.errors().stream().anyMatch(e -> e.message().contains("Success criteria required for QUEEN")))
				.isTrue();
	}

	@Test
	void validPlan_passesValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "King", ChessPiece.KING, 1, outcome, 5, "Deploy zero-downtime");
		createCommit(plan, user, "Queen", ChessPiece.QUEEN, 2, outcome, 3, "API migrated");
		createCommit(plan, user, "Rook", ChessPiece.ROOK, 3, outcome, 2);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isTrue();
		assertThat(resp.errors()).isNullOrEmpty();
	}

	@Test
	void oneKingTwoQueens_passesValidation() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "King", ChessPiece.KING, 1, outcome, 5, "Critical deploy");
		createCommit(plan, user, "Queen 1", ChessPiece.QUEEN, 2, outcome, 3, "High leverage A");
		createCommit(plan, user, "Queen 2", ChessPiece.QUEEN, 3, outcome, 3, "High leverage B");
		createCommit(plan, user, "Pawn", ChessPiece.PAWN, 4, outcome, 1);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isTrue();
	}
}
