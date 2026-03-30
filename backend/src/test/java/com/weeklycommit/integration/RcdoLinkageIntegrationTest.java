package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.lock.dto.LockResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test: RCDO hierarchy management, commit linkage rules, and
 * archival behavior.
 */
class RcdoLinkageIntegrationTest extends IntegrationTestBase {

	@Autowired
	private LockService lockService;

	private Organization org;
	private Team team;
	private UserAccount user;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "rcdo@example.com", "RCDO User", "IC");
	}

	@Test
	void rcdoHierarchy_parentChildRelationships() {
		RcdoNode rally = createRallyCry(team, "Win Enterprise");
		RcdoNode defObj = createDefiningObjective(rally, team, "Improve Uptime");
		RcdoNode outcome1 = createOutcome(defObj, team, "99.9% SLA");
		RcdoNode outcome2 = createOutcome(defObj, team, "Zero-downtime deploys");

		// Verify parent-child relationships
		assertThat(defObj.getParentId()).isEqualTo(rally.getId());
		assertThat(outcome1.getParentId()).isEqualTo(defObj.getId());
		assertThat(outcome2.getParentId()).isEqualTo(defObj.getId());

		// Verify node types
		assertThat(rally.getNodeType()).isEqualTo(RcdoNodeType.RALLY_CRY);
		assertThat(defObj.getNodeType()).isEqualTo(RcdoNodeType.DEFINING_OBJECTIVE);
		assertThat(outcome1.getNodeType()).isEqualTo(RcdoNodeType.OUTCOME);
	}

	@Test
	void commitLinksToActiveOutcome_lockSucceeds() {
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		RcdoNode outcome = createOutcome(defObj, team, "Active Outcome");

		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Linked commit", ChessPiece.PAWN, 1, outcome, 2);

		LockResponse resp = lockService.lockPlan(plan.getId(), user.getId());
		assertThat(resp.success()).isTrue();
	}

	@Test
	void archivedNode_remainsInHistoricalSnapshots() {
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		RcdoNode outcome = createOutcome(defObj, team, "Will be archived");

		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		// Lock while RCDO is active
		lockService.lockPlan(plan.getId(), user.getId());

		// Archive the node AFTER lock
		outcome.setStatus(RcdoNodeStatus.ARCHIVED);
		rcdoNodeRepo.save(outcome);

		// The lock snapshot should still reference the RCDO
		LockSnapshotHeader header = lockHeaderRepo.findByPlanId(plan.getId()).orElseThrow();
		assertThat(header.getSnapshotPayload()).contains("Will be archived");
	}

	@Test
	void findCommitsByRcdoNodeId_returnsLinkedCommits() {
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		RcdoNode outcome1 = createOutcome(defObj, team, "Outcome 1");
		RcdoNode outcome2 = createOutcome(defObj, team, "Outcome 2");

		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Linked to 1", ChessPiece.PAWN, 1, outcome1, 2);
		createCommit(plan, user, "Also linked to 1", ChessPiece.ROOK, 2, outcome1, 3);
		createCommit(plan, user, "Linked to 2", ChessPiece.BISHOP, 3, outcome2, 1);

		List<WeeklyCommit> forOutcome1 = commitRepo.findByRcdoNodeId(outcome1.getId());
		assertThat(forOutcome1).hasSize(2);
		assertThat(forOutcome1.stream().map(WeeklyCommit::getTitle)).containsExactlyInAnyOrder("Linked to 1",
				"Also linked to 1");

		List<WeeklyCommit> forOutcome2 = commitRepo.findByRcdoNodeId(outcome2.getId());
		assertThat(forOutcome2).hasSize(1);
	}

	@Test
	void rcdoNodeStatuses_activeAndArchived() {
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		RcdoNode active = createOutcome(defObj, team, "Active");
		RcdoNode archived = createRcdoNode(RcdoNodeType.OUTCOME, defObj.getId(), team, "Archived",
				RcdoNodeStatus.ARCHIVED);
		RcdoNode draft = createRcdoNode(RcdoNodeType.OUTCOME, defObj.getId(), team, "Draft", RcdoNodeStatus.DRAFT);

		assertThat(active.getStatus()).isEqualTo(RcdoNodeStatus.ACTIVE);
		assertThat(archived.getStatus()).isEqualTo(RcdoNodeStatus.ARCHIVED);
		assertThat(draft.getStatus()).isEqualTo(RcdoNodeStatus.DRAFT);

		// All are persisted and findable
		assertThat(rcdoNodeRepo.findById(active.getId())).isPresent();
		assertThat(rcdoNodeRepo.findById(archived.getId())).isPresent();
		assertThat(rcdoNodeRepo.findById(draft.getId())).isPresent();
	}

	@Test
	void multipleCommitsAcrossPlans_sameRcdo() {
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		RcdoNode outcome = createOutcome(defObj, team, "Shared Outcome");

		UserAccount user2 = createUser(org, team, "user2@example.com", "User 2", "IC");

		WeeklyPlan plan1 = createDraftPlan(user, team, thisMonday());
		WeeklyPlan plan2 = createDraftPlan(user2, team, thisMonday());

		createCommit(plan1, user, "User 1 commit", ChessPiece.PAWN, 1, outcome, 2);
		createCommit(plan2, user2, "User 2 commit", ChessPiece.ROOK, 1, outcome, 3);

		List<WeeklyCommit> allForOutcome = commitRepo.findByRcdoNodeId(outcome.getId());
		assertThat(allForOutcome).hasSize(2);
	}
}
