package com.weeklycommit.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.CarryForwardFact;
import com.weeklycommit.domain.entity.ComplianceFact;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.TeamWeekRollup;
import com.weeklycommit.domain.entity.UserWeekFact;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.CarryForwardFactRepository;
import com.weeklycommit.domain.repository.ComplianceFactRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.RcdoWeekRollupRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamWeekRollupRepository;
import com.weeklycommit.domain.repository.UserWeekFactRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReadModelRefreshService}.
 *
 * <p>
 * Verifies:
 * <ul>
 * <li>Correct aggregate computation for {@code user_week_fact}</li>
 * <li>Correct aggregate computation for {@code compliance_fact}</li>
 * <li>Correct aggregate computation for {@code carry_forward_fact}</li>
 * <li>Correct aggregate computation for {@code team_week_rollup}</li>
 * <li>Idempotency: re-running produces same results (find-then-update)</li>
 * <li>Event-driven hook: {@code refreshForPlan} is called after lock</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReadModelRefreshServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;
	@Mock
	private WeeklyCommitRepository commitRepo;
	@Mock
	private TeamMembershipRepository membershipRepo;
	@Mock
	private ScopeChangeEventRepository scopeChangeRepo;
	@Mock
	private ManagerReviewExceptionRepository exceptionRepo;
	@Mock
	private LockSnapshotHeaderRepository lockSnapshotRepo;
	@Mock
	private UserWeekFactRepository userWeekFactRepo;
	@Mock
	private TeamWeekRollupRepository teamWeekRollupRepo;
	@Mock
	private RcdoWeekRollupRepository rcdoWeekRollupRepo;
	@Mock
	private ComplianceFactRepository complianceFactRepo;
	@Mock
	private CarryForwardFactRepository carryForwardFactRepo;

	private ReadModelRefreshService service;

	private UUID planId;
	private UUID userId;
	private UUID teamId;
	private WeeklyPlan plan;

	@BeforeEach
	void setUp() {
		service = new ReadModelRefreshService(planRepo, commitRepo, membershipRepo, scopeChangeRepo, exceptionRepo,
				lockSnapshotRepo, userWeekFactRepo, teamWeekRollupRepo, rcdoWeekRollupRepo, complianceFactRepo,
				carryForwardFactRepo, new ObjectMapper());

		planId = UUID.randomUUID();
		userId = UUID.randomUUID();
		teamId = UUID.randomUUID();

		plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(userId);
		plan.setTeamId(teamId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 24));
		plan.setState(PlanState.LOCKED);
		plan.setLockDeadline(Instant.now().plusSeconds(3600));
		plan.setReconcileDeadline(Instant.now().plusSeconds(7 * 24 * 3600));
		plan.setCompliant(true);
		plan.setSystemLockedWithErrors(false);

		lenient().when(lockSnapshotRepo.findByPlanId(planId)).thenReturn(Optional.empty());
	}

	// -------------------------------------------------------------------------
	// refreshUserWeekFact — aggregate computation
	// -------------------------------------------------------------------------

	@Test
	void refreshUserWeekFact_computesAggregatesCorrectly() {
		WeeklyCommit king = commit(ChessPiece.KING, 5, 0, null);
		WeeklyCommit rook = commit(ChessPiece.ROOK, 3, 1, CommitOutcome.ACHIEVED);
		WeeklyCommit queen = commit(ChessPiece.QUEEN, 2, 2, null);

		when(userWeekFactRepo.findByUserIdAndWeekStart(userId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(userWeekFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshUserWeekFact(plan, List.of(king, rook, queen), 3);

		ArgumentCaptor<UserWeekFact> captor = ArgumentCaptor.forClass(UserWeekFact.class);
		verify(userWeekFactRepo).save(captor.capture());

		UserWeekFact fact = captor.getValue();
		assertThat(fact.getUserId()).isEqualTo(userId);
		assertThat(fact.getWeekStart()).isEqualTo(plan.getWeekStartDate());
		assertThat(fact.getPlanState()).isEqualTo("LOCKED");
		assertThat(fact.getTotalPlannedPoints()).isEqualTo(10); // 5 + 3 + 2
		assertThat(fact.getTotalAchievedPoints()).isEqualTo(3); // only rook achieved
		assertThat(fact.getCommitCount()).isEqualTo(3);
		assertThat(fact.getCarryForwardCount()).isEqualTo(2); // rook streak=1, queen streak=2
		assertThat(fact.getScopeChangeCount()).isEqualTo(3);
		assertThat(fact.getKingCount()).isEqualTo(1);
		assertThat(fact.getQueenCount()).isEqualTo(1);
		assertThat(fact.isLockCompliance()).isTrue();
		assertThat(fact.isReconcileCompliance()).isFalse();
	}

	// -------------------------------------------------------------------------
	// refreshUserWeekFact — idempotency
	// -------------------------------------------------------------------------

	@Test
	void refreshUserWeekFact_isIdempotent_updatesExistingRecord() {
		UserWeekFact existing = new UserWeekFact();
		existing.setUserId(userId);
		existing.setWeekStart(plan.getWeekStartDate());
		existing.setTotalPlannedPoints(5); // stale

		when(userWeekFactRepo.findByUserIdAndWeekStart(userId, plan.getWeekStartDate()))
				.thenReturn(Optional.of(existing));
		when(userWeekFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		WeeklyCommit c = commit(ChessPiece.ROOK, 8, 0, null);
		service.refreshUserWeekFact(plan, List.of(c), 0);

		ArgumentCaptor<UserWeekFact> captor = ArgumentCaptor.forClass(UserWeekFact.class);
		verify(userWeekFactRepo).save(captor.capture());

		// Should update the existing record (same object) with new totals
		assertThat(captor.getValue().getTotalPlannedPoints()).isEqualTo(8);
	}

	// -------------------------------------------------------------------------
	// refreshComplianceFact — lock_on_time
	// -------------------------------------------------------------------------

	@Test
	void refreshComplianceFact_lockOnTime_whenCompliantAndManualLock() {
		plan.setCompliant(true);
		plan.setSystemLockedWithErrors(false);
		plan.setState(PlanState.LOCKED);

		when(complianceFactRepo.findByUserIdAndWeekStart(userId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(complianceFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshComplianceFact(plan);

		ArgumentCaptor<ComplianceFact> captor = ArgumentCaptor.forClass(ComplianceFact.class);
		verify(complianceFactRepo).save(captor.capture());

		ComplianceFact fact = captor.getValue();
		assertThat(fact.isLockOnTime()).isTrue();
		assertThat(fact.isLockLate()).isFalse();
		assertThat(fact.isAutoLocked()).isFalse();
		assertThat(fact.isReconcileOnTime()).isFalse();
		assertThat(fact.isReconcileMissed()).isFalse();
	}

	@Test
	void refreshComplianceFact_autoLocked_whenLockSnapshotWasSystemCreated() {
		plan.setState(PlanState.LOCKED);
		plan.setSystemLockedWithErrors(false);
		plan.setCompliant(false);
		LockSnapshotHeader snapshotHeader = new LockSnapshotHeader();
		snapshotHeader.setPlanId(planId);
		snapshotHeader.setLockedBySystem(true);
		when(lockSnapshotRepo.findByPlanId(planId)).thenReturn(Optional.of(snapshotHeader));
		when(complianceFactRepo.findByUserIdAndWeekStart(userId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(complianceFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshComplianceFact(plan);

		ArgumentCaptor<ComplianceFact> captor = ArgumentCaptor.forClass(ComplianceFact.class);
		verify(complianceFactRepo).save(captor.capture());

		ComplianceFact fact = captor.getValue();
		assertThat(fact.isAutoLocked()).isTrue();
		assertThat(fact.isLockOnTime()).isFalse();
		assertThat(fact.isLockLate()).isTrue();
	}

	@Test
	void refreshComplianceFact_reconcileMissed_whenPastDeadlineAndNotReconciled() {
		plan.setState(PlanState.LOCKED);
		plan.setReconcileDeadline(Instant.now().minusSeconds(3600)); // past deadline
		plan.setCompliant(true);
		plan.setSystemLockedWithErrors(false);

		when(complianceFactRepo.findByUserIdAndWeekStart(userId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(complianceFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshComplianceFact(plan);

		ArgumentCaptor<ComplianceFact> captor = ArgumentCaptor.forClass(ComplianceFact.class);
		verify(complianceFactRepo).save(captor.capture());

		assertThat(captor.getValue().isReconcileMissed()).isTrue();
		assertThat(captor.getValue().isReconcileOnTime()).isFalse();
	}

	@Test
	void refreshComplianceFact_reconcileOnTime_whenStateIsReconciled() {
		plan.setState(PlanState.RECONCILED);
		plan.setCompliant(true);

		when(complianceFactRepo.findByUserIdAndWeekStart(userId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(complianceFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshComplianceFact(plan);

		ArgumentCaptor<ComplianceFact> captor = ArgumentCaptor.forClass(ComplianceFact.class);
		verify(complianceFactRepo).save(captor.capture());

		assertThat(captor.getValue().isReconcileOnTime()).isTrue();
		assertThat(captor.getValue().isReconcileMissed()).isFalse();
	}

	// -------------------------------------------------------------------------
	// refreshCarryForwardFacts
	// -------------------------------------------------------------------------

	@Test
	void refreshCarryForwardFacts_storesFactForCarryForwardCommit() {
		UUID nodeId = UUID.randomUUID();
		WeeklyCommit cfCommit = commit(ChessPiece.ROOK, 3, 2, null);
		cfCommit.setRcdoNodeId(nodeId);

		when(carryForwardFactRepo.findByCommitId(cfCommit.getId())).thenReturn(Optional.empty());
		when(carryForwardFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshCarryForwardFacts(plan, List.of(cfCommit));

		ArgumentCaptor<CarryForwardFact> captor = ArgumentCaptor.forClass(CarryForwardFact.class);
		verify(carryForwardFactRepo).save(captor.capture());

		CarryForwardFact fact = captor.getValue();
		assertThat(fact.getCommitId()).isEqualTo(cfCommit.getId());
		assertThat(fact.getStreakLength()).isEqualTo(2);
		assertThat(fact.getCurrentWeek()).isEqualTo(plan.getWeekStartDate());
		assertThat(fact.getSourceWeek()).isEqualTo(plan.getWeekStartDate().minusWeeks(2));
		assertThat(fact.getRcdoNodeId()).isEqualTo(nodeId);
		assertThat(fact.getChessPiece()).isEqualTo("ROOK");
	}

	@Test
	void refreshCarryForwardFacts_skipsNonCarryForwardCommits() {
		WeeklyCommit normalCommit = commit(ChessPiece.KING, 5, 0, null);

		service.refreshCarryForwardFacts(plan, List.of(normalCommit));

		// carryForwardFactRepo should NOT be called for non-carry-forward commits
		verify(carryForwardFactRepo, org.mockito.Mockito.never()).save(any());
	}

	// -------------------------------------------------------------------------
	// refreshRcdoWeekRollup — includes previously linked nodes from scope change
	// -------------------------------------------------------------------------

	@Test
	void refreshRcdoWeekRollup_recomputesOldAndNewNodesAfterRcdoChange() {
		UUID oldNodeId = UUID.randomUUID();
		UUID newNodeId = UUID.randomUUID();
		WeeklyCommit commit = commit(ChessPiece.ROOK, 3, 0, null);
		commit.setRcdoNodeId(newNodeId);
		ScopeChangeEvent event = new ScopeChangeEvent();
		event.setCategory(ScopeChangeCategory.RCDO_CHANGED);
		event.setPreviousValue(oldNodeId.toString());
		event.setNewValue(newNodeId.toString());

		when(commitRepo.findByRcdoNodeId(oldNodeId)).thenReturn(List.of());
		when(commitRepo.findByRcdoNodeId(newNodeId)).thenReturn(List.of(commit));
		when(planRepo.findById(commit.getPlanId())).thenReturn(Optional.of(plan));
		when(rcdoWeekRollupRepo.findByRcdoNodeIdAndWeekStart(oldNodeId, plan.getWeekStartDate()))
				.thenReturn(Optional.empty());
		when(rcdoWeekRollupRepo.findByRcdoNodeIdAndWeekStart(newNodeId, plan.getWeekStartDate()))
				.thenReturn(Optional.empty());
		when(rcdoWeekRollupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshRcdoWeekRollup(plan, List.of(commit), List.of(event));

		ArgumentCaptor<com.weeklycommit.domain.entity.RcdoWeekRollup> captor = ArgumentCaptor
				.forClass(com.weeklycommit.domain.entity.RcdoWeekRollup.class);
		verify(rcdoWeekRollupRepo, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues()).anyMatch(r -> r.getRcdoNodeId().equals(oldNodeId) && r.getCommitCount() == 0);
		assertThat(captor.getAllValues()).anyMatch(r -> r.getRcdoNodeId().equals(newNodeId) && r.getCommitCount() == 1);
	}

	// -------------------------------------------------------------------------
	// refreshTeamWeekRollup — aggregate computation
	// -------------------------------------------------------------------------

	@Test
	void refreshTeamWeekRollup_computesAggregatesCorrectly() {
		WeeklyPlan memberPlan = new WeeklyPlan();
		memberPlan.setId(planId);
		memberPlan.setTeamId(teamId);
		memberPlan.setOwnerUserId(userId);
		memberPlan.setWeekStartDate(plan.getWeekStartDate());
		memberPlan.setState(PlanState.RECONCILED);

		TeamMembership membership = new TeamMembership();
		membership.setTeamId(teamId);
		membership.setUserId(userId);

		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, plan.getWeekStartDate())).thenReturn(List.of(memberPlan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(commit(ChessPiece.KING, 5, 0, CommitOutcome.ACHIEVED),
						commit(ChessPiece.ROOK, 3, 1, CommitOutcome.NOT_ACHIEVED)));
		when(exceptionRepo.countByTeamIdAndWeekStartDate(teamId, plan.getWeekStartDate())).thenReturn(2L);
		when(teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(teamWeekRollupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshTeamWeekRollup(teamId, plan.getWeekStartDate());

		ArgumentCaptor<TeamWeekRollup> captor = ArgumentCaptor.forClass(TeamWeekRollup.class);
		verify(teamWeekRollupRepo).save(captor.capture());

		TeamWeekRollup rollup = captor.getValue();
		assertThat(rollup.getTeamId()).isEqualTo(teamId);
		assertThat(rollup.getMemberCount()).isEqualTo(1);
		assertThat(rollup.getLockedCount()).isEqualTo(1); // RECONCILED counts as locked
		assertThat(rollup.getReconciledCount()).isEqualTo(1);
		assertThat(rollup.getTotalPlannedPoints()).isEqualTo(8); // 5 + 3
		assertThat(rollup.getTotalAchievedPoints()).isEqualTo(5); // only KING achieved
		assertThat(rollup.getExceptionCount()).isEqualTo(2);
		// carryForwardRate = 1/2 = 0.5 (rook has streak=1)
		assertThat(rollup.getAvgCarryForwardRate()).isEqualTo(0.5);
		assertThat(rollup.getChessDistribution()).contains("KING");
		assertThat(rollup.getChessDistribution()).contains("ROOK");
	}

	// -------------------------------------------------------------------------
	// refreshTeamWeekRollup — idempotency
	// -------------------------------------------------------------------------

	@Test
	void refreshTeamWeekRollup_isIdempotent_runningTwiceGivesSameResult() {
		TeamMembership membership = new TeamMembership();
		membership.setTeamId(teamId);
		membership.setUserId(userId);

		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership));
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, plan.getWeekStartDate())).thenReturn(List.of());
		when(exceptionRepo.countByTeamIdAndWeekStartDate(teamId, plan.getWeekStartDate())).thenReturn(0L);
		when(teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(teamWeekRollupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshTeamWeekRollup(teamId, plan.getWeekStartDate());
		service.refreshTeamWeekRollup(teamId, plan.getWeekStartDate());

		// Both runs should produce the same result — verified by save being called
		// twice with the same values
		ArgumentCaptor<TeamWeekRollup> captor = ArgumentCaptor.forClass(TeamWeekRollup.class);
		verify(teamWeekRollupRepo, org.mockito.Mockito.times(2)).save(captor.capture());

		List<TeamWeekRollup> allValues = captor.getAllValues();
		assertThat(allValues.get(0).getTotalPlannedPoints()).isEqualTo(allValues.get(1).getTotalPlannedPoints());
	}

	// -------------------------------------------------------------------------
	// refreshForPlan — event-driven hook
	// -------------------------------------------------------------------------

	@Test
	void refreshForPlan_orchestratesAllDerivedTableRefreshes() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(userWeekFactRepo.findByUserIdAndWeekStart(any(), any())).thenReturn(Optional.empty());
		when(userWeekFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(complianceFactRepo.findByUserIdAndWeekStart(any(), any())).thenReturn(Optional.empty());
		when(complianceFactRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, plan.getWeekStartDate())).thenReturn(List.of(plan));
		when(exceptionRepo.countByTeamIdAndWeekStartDate(teamId, plan.getWeekStartDate())).thenReturn(0L);
		when(teamWeekRollupRepo.findByTeamIdAndWeekStart(teamId, plan.getWeekStartDate())).thenReturn(Optional.empty());
		when(teamWeekRollupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.refreshForPlan(planId);

		// All three derived tables must be written
		verify(userWeekFactRepo).save(any(UserWeekFact.class));
		verify(complianceFactRepo).save(any(ComplianceFact.class));
		verify(teamWeekRollupRepo).save(any(TeamWeekRollup.class));
	}

	@Test
	void refreshForPlan_gracefullyHandlesMissingPlan() {
		when(planRepo.findById(planId)).thenReturn(Optional.empty());

		// Should not throw — just log a warning
		service.refreshForPlan(planId);

		verify(userWeekFactRepo, org.mockito.Mockito.never()).save(any());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit commit(ChessPiece piece, int pts, int cfStreak, CommitOutcome outcome) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(userId);
		c.setTitle("Commit");
		c.setChessPiece(piece);
		c.setEstimatePoints(pts);
		c.setCarryForwardStreak(cfStreak);
		c.setOutcome(outcome);
		c.setPriorityOrder(1);
		return c;
	}
}
