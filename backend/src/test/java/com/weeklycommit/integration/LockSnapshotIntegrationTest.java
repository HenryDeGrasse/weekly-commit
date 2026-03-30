package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.lock.service.LockService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test: lock snapshot creation, immutability, and content
 * verification.
 */
class LockSnapshotIntegrationTest extends IntegrationTestBase {

	@Autowired
	private LockService lockService;
	@Autowired
	private ObjectMapper objectMapper;

	private Organization org;
	private Team team;
	private UserAccount user;
	private RcdoNode rallyCry;
	private RcdoNode defObj;
	private RcdoNode outcome;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "test@example.com", "Test User", "IC");
		rallyCry = createRallyCry(team, "Win Enterprise");
		defObj = createDefiningObjective(rallyCry, team, "Improve Uptime");
		outcome = createOutcome(defObj, team, "99.9% SLA");
	}

	@Test
	void lockCreatesSnapshotHeader_andCommitSnapshots() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c1 = createCommit(plan, user, "Deploy auth", ChessPiece.KING, 1, outcome, 5,
				"Zero-downtime deployment");
		WeeklyCommit c2 = createCommit(plan, user, "Update docs", ChessPiece.PAWN, 2, outcome, 1);

		lockService.lockPlan(plan.getId(), user.getId());

		// Verify header
		LockSnapshotHeader header = lockHeaderRepo.findByPlanId(plan.getId()).orElseThrow();
		assertThat(header.getPlanId()).isEqualTo(plan.getId());
		assertThat(header.isLockedBySystem()).isFalse();
		assertThat(header.getLockedAt()).isNotNull();
		assertThat(header.getSnapshotPayload()).isNotBlank();

		// Verify commit snapshots
		List<LockSnapshotCommit> snapshots = lockCommitRepo.findBySnapshotId(header.getId());
		assertThat(snapshots).hasSize(2);
		assertThat(snapshots.stream().map(LockSnapshotCommit::getCommitId)).containsExactlyInAnyOrder(c1.getId(),
				c2.getId());
	}

	@Test
	void snapshotPayload_containsCorrectJson() throws Exception {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Deploy auth", ChessPiece.KING, 1, outcome, 5, "Zero-downtime");

		lockService.lockPlan(plan.getId(), user.getId());

		LockSnapshotHeader header = lockHeaderRepo.findByPlanId(plan.getId()).orElseThrow();
		JsonNode payload = objectMapper.readTree(header.getSnapshotPayload());

		assertThat(payload.has("planId")).isTrue();
		assertThat(payload.get("planId").asText()).isEqualTo(plan.getId().toString());
		assertThat(payload.has("commits")).isTrue();
		assertThat(payload.get("commits").isArray()).isTrue();
		assertThat(payload.get("commits").size()).isEqualTo(1);

		JsonNode commitNode = payload.get("commits").get(0);
		assertThat(commitNode.get("title").asText()).isEqualTo("Deploy auth");
		assertThat(commitNode.get("chessPiece").asText()).isEqualTo("KING");
		assertThat(commitNode.get("estimatePoints").asInt()).isEqualTo(5);
	}

	@Test
	void snapshotPayload_containsDenormalizedRcdoPath() throws Exception {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Deploy auth", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan.getId(), user.getId());

		LockSnapshotHeader header = lockHeaderRepo.findByPlanId(plan.getId()).orElseThrow();
		JsonNode payload = objectMapper.readTree(header.getSnapshotPayload());
		JsonNode commitNode = payload.get("commits").get(0);

		// rcdoPath should contain the full ancestor chain
		assertThat(commitNode.has("rcdoPath")).isTrue();
		JsonNode rcdoPath = commitNode.get("rcdoPath");
		assertThat(rcdoPath.isArray()).isTrue();
		assertThat(rcdoPath.size()).isEqualTo(3); // Rally Cry → DO → Outcome
		assertThat(rcdoPath.get(0).get("nodeType").asText()).isEqualTo("RALLY_CRY");
		assertThat(rcdoPath.get(0).get("title").asText()).isEqualTo("Win Enterprise");
		assertThat(rcdoPath.get(1).get("nodeType").asText()).isEqualTo("DEFINING_OBJECTIVE");
		assertThat(rcdoPath.get(2).get("nodeType").asText()).isEqualTo("OUTCOME");
		assertThat(rcdoPath.get(2).get("title").asText()).isEqualTo("99.9% SLA");
	}

	@Test
	void snapshotIsWriteOnce_backReferenceSetOnPlan() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		lockService.lockPlan(plan.getId(), user.getId());

		// Plan should have the back-reference
		WeeklyPlan updated = planRepo.findById(plan.getId()).orElseThrow();
		assertThat(updated.getLockSnapshotId()).isNotNull();

		LockSnapshotHeader header = lockHeaderRepo.findByPlanId(plan.getId()).orElseThrow();
		assertThat(updated.getLockSnapshotId()).isEqualTo(header.getId());
	}

	@Test
	void commitSnapshotData_containsCommitFields() throws Exception {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		WeeklyCommit c = createCommit(plan, user, "Auth refactor", ChessPiece.QUEEN, 1, outcome, 5,
				"All endpoints migrated");

		lockService.lockPlan(plan.getId(), user.getId());

		LockSnapshotHeader header = lockHeaderRepo.findByPlanId(plan.getId()).orElseThrow();
		List<LockSnapshotCommit> snapshots = lockCommitRepo.findBySnapshotId(header.getId());
		assertThat(snapshots).hasSize(1);

		JsonNode data = objectMapper.readTree(snapshots.get(0).getSnapshotData());
		assertThat(data.get("id").asText()).isEqualTo(c.getId().toString());
		assertThat(data.get("title").asText()).isEqualTo("Auth refactor");
		assertThat(data.get("chessPiece").asText()).isEqualTo("QUEEN");
		assertThat(data.get("successCriteria").asText()).isEqualTo("All endpoints migrated");
		assertThat(data.get("estimatePoints").asInt()).isEqualTo(5);
	}

	@Test
	void autoLock_setsSystemLockedWithErrors() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		// No commits → validation errors expected
		lockService.autoLockPlan(plan.getId());

		WeeklyPlan locked = planRepo.findById(plan.getId()).orElseThrow();
		assertThat(locked.getState()).isEqualTo(PlanState.LOCKED);
		assertThat(locked.isSystemLockedWithErrors()).isTrue();
		assertThat(locked.isCompliant()).isFalse();
	}

	@Test
	void autoLock_isIdempotent() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);

		lockService.autoLockPlan(plan.getId());
		PlanState stateAfterFirst = planRepo.findById(plan.getId()).orElseThrow().getState();
		assertThat(stateAfterFirst).isEqualTo(PlanState.LOCKED);

		// Second call is a no-op (plan is no longer DRAFT)
		lockService.autoLockPlan(plan.getId());
		PlanState stateAfterSecond = planRepo.findById(plan.getId()).orElseThrow().getState();
		assertThat(stateAfterSecond).isEqualTo(PlanState.LOCKED);
	}
}
