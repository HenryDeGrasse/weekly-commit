package com.weeklycommit.integration;

import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import com.weeklycommit.domain.repository.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared base for integration tests that hit a real H2 database.
 *
 * <p>
 * Uses {@code @SpringBootTest} with {@code @Transactional} so each test runs in
 * its own transaction that is rolled back after the test completes.
 * {@code @DirtiesContext} ensures a fresh application context per test class.
 */
@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class IntegrationTestBase {

	@Autowired
	protected OrganizationRepository orgRepo;
	@Autowired
	protected TeamRepository teamRepo;
	@Autowired
	protected UserAccountRepository userRepo;
	@Autowired
	protected WeeklyPlanRepository planRepo;
	@Autowired
	protected WeeklyCommitRepository commitRepo;
	@Autowired
	protected RcdoNodeRepository rcdoNodeRepo;
	@Autowired
	protected LockSnapshotHeaderRepository lockHeaderRepo;
	@Autowired
	protected LockSnapshotCommitRepository lockCommitRepo;
	@Autowired
	protected ReconcileSnapshotHeaderRepository reconcileHeaderRepo;
	@Autowired
	protected ReconcileSnapshotCommitRepository reconcileCommitRepo;
	@Autowired
	protected ScopeChangeEventRepository scopeChangeRepo;
	@Autowired
	protected CarryForwardLinkRepository cfLinkRepo;

	// ── Factories ───────────────────────────────────────────────────────────

	protected Organization createOrg(String name) {
		Organization org = new Organization();
		org.setName(name);
		return orgRepo.save(org);
	}

	protected Team createTeam(Organization org, String name) {
		Team team = new Team();
		team.setOrganizationId(org.getId());
		team.setName(name);
		return teamRepo.save(team);
	}

	protected UserAccount createUser(Organization org, Team team, String email, String displayName, String role) {
		UserAccount u = new UserAccount();
		u.setOrganizationId(org.getId());
		u.setHomeTeamId(team.getId());
		u.setEmail(email);
		u.setDisplayName(displayName);
		u.setRole(role);
		u.setWeeklyCapacityPoints(10);
		return userRepo.save(u);
	}

	protected WeeklyPlan createDraftPlan(UserAccount user, Team team, LocalDate weekStart) {
		WeeklyPlan plan = new WeeklyPlan();
		plan.setOwnerUserId(user.getId());
		plan.setTeamId(team.getId());
		plan.setWeekStartDate(weekStart);
		plan.setState(PlanState.DRAFT);
		plan.setCapacityBudgetPoints(user.getWeeklyCapacityPoints());
		plan.setLockDeadline(weekStart.atTime(12, 0).toInstant(ZoneOffset.UTC));
		plan.setReconcileDeadline(weekStart.plusDays(7).atTime(10, 0).toInstant(ZoneOffset.UTC));
		return planRepo.save(plan);
	}

	protected RcdoNode createRallyCry(Team team, String title) {
		return createRcdoNode(RcdoNodeType.RALLY_CRY, null, team, title, RcdoNodeStatus.ACTIVE);
	}

	protected RcdoNode createDefiningObjective(RcdoNode parent, Team team, String title) {
		return createRcdoNode(RcdoNodeType.DEFINING_OBJECTIVE, parent.getId(), team, title, RcdoNodeStatus.ACTIVE);
	}

	protected RcdoNode createOutcome(RcdoNode parent, Team team, String title) {
		return createRcdoNode(RcdoNodeType.OUTCOME, parent.getId(), team, title, RcdoNodeStatus.ACTIVE);
	}

	protected RcdoNode createRcdoNode(RcdoNodeType type, UUID parentId, Team team, String title,
			RcdoNodeStatus status) {
		RcdoNode node = new RcdoNode();
		node.setNodeType(type);
		node.setParentId(parentId);
		node.setOwnerTeamId(team.getId());
		node.setTitle(title);
		node.setStatus(status);
		return rcdoNodeRepo.save(node);
	}

	protected WeeklyCommit createCommit(WeeklyPlan plan, UserAccount user, String title, ChessPiece piece, int priority,
			RcdoNode rcdo, int estimatePoints) {
		return createCommit(plan, user, title, piece, priority, rcdo, estimatePoints, null);
	}

	protected WeeklyCommit createCommit(WeeklyPlan plan, UserAccount user, String title, ChessPiece piece, int priority,
			RcdoNode rcdo, int estimatePoints, String successCriteria) {
		WeeklyCommit c = new WeeklyCommit();
		c.setPlanId(plan.getId());
		c.setOwnerUserId(user.getId());
		c.setTitle(title);
		c.setChessPiece(piece);
		c.setPriorityOrder(priority);
		c.setRcdoNodeId(rcdo != null ? rcdo.getId() : null);
		c.setEstimatePoints(estimatePoints);
		c.setSuccessCriteria(successCriteria);
		return commitRepo.save(c);
	}

	protected LocalDate thisMonday() {
		LocalDate now = LocalDate.now();
		return now.minusDays(now.getDayOfWeek().getValue() - 1);
	}

	protected LocalDate nextMonday() {
		return thisMonday().plusWeeks(1);
	}
}
