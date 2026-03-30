package com.weeklycommit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.domain.entity.*;
import com.weeklycommit.domain.enums.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test: verify custom repository query methods work against a real
 * database — especially native queries, date ranges, and derived finders.
 */
class RepositoryQueryIntegrationTest extends IntegrationTestBase {

	private Organization org;
	private Team team;
	private UserAccount user;
	private RcdoNode outcome;

	@BeforeEach
	void setUp() {
		org = createOrg("Test Org");
		team = createTeam(org, "Test Team");
		user = createUser(org, team, "query@example.com", "Query User", "IC");
		RcdoNode rally = createRallyCry(team, "Rally");
		RcdoNode defObj = createDefiningObjective(rally, team, "DO");
		outcome = createOutcome(defObj, team, "Outcome");
	}

	@Test
	void findByOwnerUserIdAndWeekStartDate_returnsCorrectPlan() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		createDraftPlan(user, team, week1);
		createDraftPlan(user, team, week2);

		Optional<WeeklyPlan> found = planRepo.findByOwnerUserIdAndWeekStartDate(user.getId(), week1);
		assertThat(found).isPresent();
		assertThat(found.get().getWeekStartDate()).isEqualTo(week1);

		Optional<WeeklyPlan> notFound = planRepo.findByOwnerUserIdAndWeekStartDate(user.getId(), week2.plusWeeks(10));
		assertThat(notFound).isEmpty();
	}

	@Test
	void findByStateAndLockDeadlineBefore_findsExpiredDrafts() {
		LocalDate week = thisMonday();
		WeeklyPlan plan = createDraftPlan(user, team, week);
		// Set lock deadline to the past
		plan.setLockDeadline(Instant.now().minusSeconds(3600));
		planRepo.save(plan);

		List<WeeklyPlan> expired = planRepo.findByStateAndLockDeadlineBefore(PlanState.DRAFT, Instant.now());
		assertThat(expired).anyMatch(p -> p.getId().equals(plan.getId()));
	}

	@Test
	void findByStateAndLockDeadlineBefore_excludesFutureDrafts() {
		LocalDate week = thisMonday();
		WeeklyPlan plan = createDraftPlan(user, team, week);
		// Lock deadline in the future
		plan.setLockDeadline(Instant.now().plusSeconds(86400));
		planRepo.save(plan);

		List<WeeklyPlan> expired = planRepo.findByStateAndLockDeadlineBefore(PlanState.DRAFT, Instant.now());
		assertThat(expired).noneMatch(p -> p.getId().equals(plan.getId()));
	}

	@Test
	void findByPlanIdOrderByPriorityOrder_returnsOrderedCommits() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Third", ChessPiece.PAWN, 3, outcome, 1);
		createCommit(plan, user, "First", ChessPiece.KING, 1, outcome, 5, "Criteria");
		createCommit(plan, user, "Second", ChessPiece.ROOK, 2, outcome, 3);

		List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
		assertThat(commits).hasSize(3);
		assertThat(commits.get(0).getTitle()).isEqualTo("First");
		assertThat(commits.get(1).getTitle()).isEqualTo("Second");
		assertThat(commits.get(2).getTitle()).isEqualTo("Third");
	}

	@Test
	void countByPlanId_returnsCorrectCount() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		assertThat(commitRepo.countByPlanId(plan.getId())).isZero();

		createCommit(plan, user, "Commit 1", ChessPiece.PAWN, 1, outcome, 2);
		createCommit(plan, user, "Commit 2", ChessPiece.ROOK, 2, outcome, 3);
		assertThat(commitRepo.countByPlanId(plan.getId())).isEqualTo(2);
	}

	@Test
	void findByOwnerUserIdAndPlanWeekStartBetween_nativeQuery() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		LocalDate week3 = week2.plusWeeks(1);

		WeeklyPlan plan1 = createDraftPlan(user, team, week1);
		WeeklyPlan plan2 = createDraftPlan(user, team, week2);
		WeeklyPlan plan3 = createDraftPlan(user, team, week3);

		createCommit(plan1, user, "Week 1 commit", ChessPiece.PAWN, 1, outcome, 2);
		createCommit(plan2, user, "Week 2 commit", ChessPiece.ROOK, 1, outcome, 3);
		createCommit(plan3, user, "Week 3 commit", ChessPiece.BISHOP, 1, outcome, 1);

		// Query weeks 1 through 2 — should exclude week 3
		List<WeeklyCommit> results = commitRepo.findByOwnerUserIdAndPlanWeekStartBetween(user.getId(), week1, week2);
		assertThat(results).hasSize(2);
		assertThat(results.stream().map(WeeklyCommit::getTitle)).containsExactlyInAnyOrder("Week 1 commit",
				"Week 2 commit");
	}

	@Test
	void findByTeamIdAndWeekStartDate_returnsTeamPlans() {
		UserAccount user2 = createUser(org, team, "user2@example.com", "User Two", "IC");

		LocalDate week = thisMonday();
		createDraftPlan(user, team, week);
		createDraftPlan(user2, team, week);

		List<WeeklyPlan> teamPlans = planRepo.findByTeamIdAndWeekStartDate(team.getId(), week);
		assertThat(teamPlans).hasSize(2);
	}

	@Test
	void findByOwnerUserIdOrderByWeekStartDateDesc_returnsDescending() {
		LocalDate week1 = thisMonday();
		LocalDate week2 = nextMonday();
		LocalDate week3 = week2.plusWeeks(1);

		createDraftPlan(user, team, week1);
		createDraftPlan(user, team, week3);
		createDraftPlan(user, team, week2);

		List<WeeklyPlan> plans = planRepo.findByOwnerUserIdOrderByWeekStartDateDesc(user.getId());
		assertThat(plans).hasSize(3);
		assertThat(plans.get(0).getWeekStartDate()).isEqualTo(week3);
		assertThat(plans.get(1).getWeekStartDate()).isEqualTo(week2);
		assertThat(plans.get(2).getWeekStartDate()).isEqualTo(week1);
	}

	@Test
	void findByRcdoNodeId_findsLinkedCommits() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Linked commit", ChessPiece.PAWN, 1, outcome, 2);
		createCommit(plan, user, "No link", ChessPiece.PAWN, 2, null, 1);

		List<WeeklyCommit> linked = commitRepo.findByRcdoNodeId(outcome.getId());
		assertThat(linked).hasSize(1);
		assertThat(linked.get(0).getTitle()).isEqualTo("Linked commit");
	}

	@Test
	void existsByOwnerUserIdAndWeekStartDate_works() {
		LocalDate week = thisMonday();
		assertThat(planRepo.existsByOwnerUserIdAndWeekStartDate(user.getId(), week)).isFalse();
		createDraftPlan(user, team, week);
		assertThat(planRepo.existsByOwnerUserIdAndWeekStartDate(user.getId(), week)).isTrue();
	}

	@Test
	void findByWeekStartDate_findsAllPlansForWeek() {
		UserAccount user2 = createUser(org, team, "user2@example.com", "User Two", "IC");
		LocalDate week = thisMonday();
		createDraftPlan(user, team, week);
		createDraftPlan(user2, team, week);

		List<WeeklyPlan> allForWeek = planRepo.findByWeekStartDate(week);
		assertThat(allForWeek).hasSize(2);
	}

	@Test
	void findByStateAndReconcileDeadlineBefore_findsExpiredLocked() {
		WeeklyPlan plan = createDraftPlan(user, team, thisMonday());
		createCommit(plan, user, "Commit", ChessPiece.PAWN, 1, outcome, 2);
		plan.setState(PlanState.LOCKED);
		plan.setReconcileDeadline(Instant.now().minusSeconds(3600));
		planRepo.save(plan);

		List<WeeklyPlan> expired = planRepo.findByStateAndReconcileDeadlineBefore(PlanState.LOCKED, Instant.now());
		assertThat(expired).anyMatch(p -> p.getId().equals(plan.getId()));
	}
}
