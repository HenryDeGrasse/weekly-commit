package com.weeklycommit.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.team.dto.TeamWeekViewResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamWeeklyViewServiceTest {

	@Mock
	private TeamRepository teamRepo;

	@Mock
	private TeamMembershipRepository membershipRepo;

	@Mock
	private UserAccountRepository userRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private AuthorizationService authService;

	@InjectMocks
	private TeamWeeklyViewService service;

	private final UUID teamId = UUID.randomUUID();
	private final UUID managerId = UUID.randomUUID();
	private final UUID ic1Id = UUID.randomUUID();
	private final UUID ic2Id = UUID.randomUUID();
	private final LocalDate weekStart = LocalDate.of(2025, 6, 9);

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private Team team() {
		Team t = new Team();
		t.setId(teamId);
		t.setOrganizationId(UUID.randomUUID());
		t.setName("Engineering Alpha");
		return t;
	}

	private TeamMembership memberOf(UUID userId) {
		TeamMembership m = new TeamMembership();
		m.setId(UUID.randomUUID());
		m.setTeamId(teamId);
		m.setUserId(userId);
		m.setRole("MEMBER");
		return m;
	}

	private UserAccount user(UUID id, String name) {
		UserAccount u = new UserAccount();
		u.setId(id);
		u.setDisplayName(name);
		u.setRole("IC");
		u.setEmail(name.toLowerCase() + "@test.com");
		u.setOrganizationId(UUID.randomUUID());
		u.setHomeTeamId(teamId);
		u.setWeeklyCapacityPoints(10);
		return u;
	}

	private UserAccount manager(UUID id) {
		UserAccount u = user(id, "Manager");
		u.setRole("MANAGER");
		return u;
	}

	private WeeklyPlan plan(UUID id, UUID ownerId, PlanState state) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(id);
		p.setOwnerUserId(ownerId);
		p.setTeamId(teamId);
		p.setWeekStartDate(weekStart);
		p.setState(state);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now().plusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7 * 24 * 3600));
		return p;
	}

	private WeeklyCommit commit(UUID planId, UUID ownerId, ChessPiece piece, int points, UUID workItemId,
			UUID rcdoNodeId) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(ownerId);
		c.setTitle("Commit by " + ownerId);
		c.setChessPiece(piece);
		c.setPriorityOrder(1);
		c.setEstimatePoints(points);
		c.setWorkItemId(workItemId);
		c.setRcdoNodeId(rcdoNodeId);
		return c;
	}

	private WorkItem ticket(UUID id, UUID teamId, UUID assignee, LocalDate targetWeek) {
		WorkItem wi = new WorkItem();
		wi.setId(id);
		wi.setTeamId(teamId);
		wi.setKey("T-" + id.toString().substring(0, 4));
		wi.setTitle("Ticket " + id);
		wi.setStatus(TicketStatus.IN_PROGRESS);
		wi.setAssigneeUserId(assignee);
		wi.setReporterUserId(assignee != null ? assignee : UUID.randomUUID());
		wi.setTargetWeekStartDate(targetWeek);
		return wi;
	}

	@BeforeEach
	void stubCommonAuth() {
		// Manager caller can access all members' full detail
		lenient().when(authService.getCallerRole(managerId)).thenReturn(com.weeklycommit.domain.enums.UserRole.MANAGER);
		lenient().doNothing().when(authService).checkCanAccessTeam(any(), any());
		lenient().when(authService.canAccessFullDetail(eq(managerId), any())).thenReturn(true);
		lenient().when(authService.arePeers(eq(managerId), any())).thenReturn(false);
	}

	// =========================================================================
	// Aggregation: all member commits are collected
	// =========================================================================

	@Test
	void getTeamWeekView_aggregatesCommitsForAllMembers() {
		UUID plan1Id = UUID.randomUUID();
		UUID plan2Id = UUID.randomUUID();
		UUID rcdoId = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id), memberOf(ic2Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.LOCKED), plan(plan2Id, ic2Id, PlanState.DRAFT)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(userRepo.findById(ic2Id)).thenReturn(Optional.of(user(ic2Id, "Bob")));

		WeeklyCommit c1 = commit(plan1Id, ic1Id, ChessPiece.KING, 5, null, rcdoId);
		WeeklyCommit c2 = commit(plan2Id, ic2Id, ChessPiece.ROOK, 3, null, rcdoId);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of(c1));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan2Id)).thenReturn(List.of(c2));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());
		when(workItemRepo.findByAssigneeUserId(ic2Id)).thenReturn(List.of());
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.teamId()).isEqualTo(teamId);
		assertThat(response.memberViews()).hasSize(2);
		assertThat(response.memberViews().stream().anyMatch(m -> m.userId().equals(ic1Id))).isTrue();
		assertThat(response.memberViews().stream().anyMatch(m -> m.userId().equals(ic2Id))).isTrue();
	}

	// =========================================================================
	// De-duplication: tickets linked to commits are NOT in uncommitted sections
	// =========================================================================

	@Test
	void getTeamWeekView_deduplicatesLinkedTickets() {
		UUID plan1Id = UUID.randomUUID();
		UUID workItemId = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.LOCKED)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));

		// Commit is linked to workItemId
		WeeklyCommit c1 = commit(plan1Id, ic1Id, ChessPiece.ROOK, 3, workItemId, null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of(c1));

		// Work item is assigned to ic1 — but linked to commit, so should NOT appear as
		// uncommitted
		WorkItem linked = ticket(workItemId, teamId, ic1Id, weekStart);
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of(linked));
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.uncommittedAssignedTickets()).isEmpty();
	}

	@Test
	void getTeamWeekView_unlinkedAssignedTicketAppearsInUncommitted() {
		UUID plan1Id = UUID.randomUUID();
		UUID unlinkedTicketId = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.DRAFT)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of());

		// Ticket is assigned to ic1 but NOT linked to any commit
		WorkItem unlinked = ticket(unlinkedTicketId, teamId, ic1Id, weekStart);
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of(unlinked));
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.uncommittedAssignedTickets()).hasSize(1);
		assertThat(response.uncommittedAssignedTickets().get(0).id()).isEqualTo(unlinkedTicketId);
	}

	@Test
	void getTeamWeekView_unassignedTargetedTicketAppearsInUncommitted() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of());
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());

		UUID unassignedId = UUID.randomUUID();
		WorkItem unassigned = ticket(unassignedId, teamId, null, weekStart);
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of(unassigned));

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.uncommittedUnassignedTickets()).hasSize(1);
		assertThat(response.uncommittedUnassignedTickets().get(0).id()).isEqualTo(unassignedId);
	}

	@Test
	void getTeamWeekView_linkedTicketNotInUnassignedSection() {
		UUID plan1Id = UUID.randomUUID();
		UUID linkedTicketId = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.LOCKED)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));

		// Commit links the ticket
		WeeklyCommit c = commit(plan1Id, ic1Id, ChessPiece.ROOK, 2, linkedTicketId, null);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of(c));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());

		// The same ticket is also in the unassigned targeted list — should be excluded
		WorkItem linkedAsUnassigned = ticket(linkedTicketId, teamId, null, weekStart);
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(linkedAsUnassigned));

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.uncommittedUnassignedTickets()).isEmpty();
	}

	// =========================================================================
	// RCDO rollup accuracy
	// =========================================================================

	@Test
	void getTeamWeekView_rcdoRollupAggregatesPointsAndCounts() {
		UUID plan1Id = UUID.randomUUID();
		UUID plan2Id = UUID.randomUUID();
		UUID rcdoA = UUID.randomUUID();
		UUID rcdoB = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id), memberOf(ic2Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.LOCKED), plan(plan2Id, ic2Id, PlanState.LOCKED)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(userRepo.findById(ic2Id)).thenReturn(Optional.of(user(ic2Id, "Bob")));

		// ic1 has 2 commits for rcdoA (5+3 = 8 pts)
		WeeklyCommit c1 = commit(plan1Id, ic1Id, ChessPiece.KING, 5, null, rcdoA);
		WeeklyCommit c2 = commit(plan1Id, ic1Id, ChessPiece.ROOK, 3, null, rcdoA);
		// ic2 has 1 commit for rcdoB (2 pts)
		WeeklyCommit c3 = commit(plan2Id, ic2Id, ChessPiece.PAWN, 2, null, rcdoB);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of(c1, c2));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan2Id)).thenReturn(List.of(c3));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());
		when(workItemRepo.findByAssigneeUserId(ic2Id)).thenReturn(List.of());
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.rcdoRollup()).hasSize(2);

		var rcdoAEntry = response.rcdoRollup().stream().filter(e -> e.rcdoNodeId().equals(rcdoA)).findFirst();
		assertThat(rcdoAEntry).isPresent();
		assertThat(rcdoAEntry.get().commitCount()).isEqualTo(2);
		assertThat(rcdoAEntry.get().totalPoints()).isEqualTo(8);

		var rcdoBEntry = response.rcdoRollup().stream().filter(e -> e.rcdoNodeId().equals(rcdoB)).findFirst();
		assertThat(rcdoBEntry).isPresent();
		assertThat(rcdoBEntry.get().commitCount()).isEqualTo(1);
		assertThat(rcdoBEntry.get().totalPoints()).isEqualTo(2);
	}

	// =========================================================================
	// Chess distribution
	// =========================================================================

	@Test
	void getTeamWeekView_chessDistributionCountsCorrectly() {
		UUID plan1Id = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.LOCKED)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));

		UUID rcdoId = UUID.randomUUID();
		WeeklyCommit king = commit(plan1Id, ic1Id, ChessPiece.KING, 5, null, rcdoId);
		WeeklyCommit rook1 = commit(plan1Id, ic1Id, ChessPiece.ROOK, 3, null, rcdoId);
		WeeklyCommit rook2 = commit(plan1Id, ic1Id, ChessPiece.ROOK, 2, null, rcdoId);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of(king, rook1, rook2));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		var kingEntry = response.chessDistribution().stream().filter(e -> e.chessPiece() == ChessPiece.KING)
				.findFirst();
		assertThat(kingEntry).isPresent();
		assertThat(kingEntry.get().commitCount()).isEqualTo(1);
		assertThat(kingEntry.get().totalPoints()).isEqualTo(5);

		var rookEntry = response.chessDistribution().stream().filter(e -> e.chessPiece() == ChessPiece.ROOK)
				.findFirst();
		assertThat(rookEntry).isPresent();
		assertThat(rookEntry.get().commitCount()).isEqualTo(2);
		assertThat(rookEntry.get().totalPoints()).isEqualTo(5);
	}

	// =========================================================================
	// Privacy: IC peer sees peerViews, not memberViews
	// =========================================================================

	@Test
	void getTeamWeekView_peerCallerReceivesPeerViews() {
		UUID plan1Id = UUID.randomUUID();
		UUID peerId = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id), memberOf(peerId)));

		// Peer caller gets IC-level role
		when(authService.getCallerRole(peerId)).thenReturn(com.weeklycommit.domain.enums.UserRole.IC);
		// For IC callers, seesFullDetail = callerId.equals(memberId) — no call to
		// canAccessFullDetail
		// For ic1Id: seesFullDetail=false, arePeers=true → peerViews
		when(authService.arePeers(peerId, ic1Id)).thenReturn(true);
		// For peerId (own data): seesFullDetail=true → memberViews (discarded in
		// response for IC)
		// arePeers(peerId, peerId) is never called since seesFullDetail=true for own
		// data

		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart))
				.thenReturn(List.of(plan(plan1Id, ic1Id, PlanState.LOCKED)));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(userRepo.findById(peerId)).thenReturn(Optional.of(user(peerId, "Peer")));

		WeeklyCommit c = commit(plan1Id, ic1Id, ChessPiece.ROOK, 3, null, UUID.randomUUID());
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of(c));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());
		when(workItemRepo.findByAssigneeUserId(peerId)).thenReturn(List.of());
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, peerId);

		// IC caller: memberViews is empty, peerViews has ic1's data (stripped)
		assertThat(response.memberViews()).isEmpty();
		assertThat(response.peerViews()).hasSize(1);
		assertThat(response.peerViews().get(0).userId()).isEqualTo(ic1Id);
		// Peer view has stripped fields (no outcome, no streak)
		assertThat(response.peerViews().get(0).commits()).hasSize(1);

		// Compliance and uncommitted sections not populated for IC callers
		assertThat(response.complianceSummary()).isEmpty();
		assertThat(response.uncommittedAssignedTickets()).isEmpty();
	}

	// =========================================================================
	// Compliance summary
	// =========================================================================

	@Test
	void getTeamWeekView_complianceSummaryIncludedForManager() {
		UUID plan1Id = UUID.randomUUID();

		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		WeeklyPlan lockedPlan = plan(plan1Id, ic1Id, PlanState.LOCKED);
		lockedPlan.setCompliant(true);
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of(lockedPlan));
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan1Id)).thenReturn(List.of());
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.complianceSummary()).hasSize(1);
		assertThat(response.complianceSummary().get(0).userId()).isEqualTo(ic1Id);
		assertThat(response.complianceSummary().get(0).hasPlan()).isTrue();
	}

	@Test
	void getTeamWeekView_memberWithNoPlanHasNullPlanState() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(memberOf(ic1Id)));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of());
		when(userRepo.findById(ic1Id)).thenReturn(Optional.of(user(ic1Id, "Alice")));
		when(workItemRepo.findByAssigneeUserId(ic1Id)).thenReturn(List.of());
		when(workItemRepo.findByTeamIdAndTargetWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		TeamWeekViewResponse response = service.getTeamWeekView(teamId, weekStart, managerId);

		assertThat(response.memberViews()).hasSize(1);
		assertThat(response.memberViews().get(0).planId()).isNull();
		assertThat(response.memberViews().get(0).planState()).isNull();
		assertThat(response.complianceSummary().get(0).hasPlan()).isFalse();
	}
}
