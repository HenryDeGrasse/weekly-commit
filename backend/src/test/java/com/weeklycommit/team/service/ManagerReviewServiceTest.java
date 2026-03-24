package com.weeklycommit.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.CapacityOverride;
import com.weeklycommit.domain.entity.LockSnapshotCommit;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ManagerComment;
import com.weeklycommit.domain.entity.ManagerReviewException;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.CapacityOverrideRepository;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ManagerCommentRepository;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.dto.CapacityOverrideResponse;
import com.weeklycommit.team.dto.CommentResponse;
import com.weeklycommit.team.dto.ExceptionResponse;
import com.weeklycommit.team.exception.AccessDeniedException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerReviewServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private ScopeChangeEventRepository scopeChangeRepo;

	@Mock
	private LockSnapshotHeaderRepository lockHeaderRepo;

	@Mock
	private LockSnapshotCommitRepository lockCommitRepo;

	@Mock
	private ManagerReviewExceptionRepository exceptionRepo;

	@Mock
	private ManagerCommentRepository commentRepo;

	@Mock
	private CapacityOverrideRepository capacityOverrideRepo;

	@Mock
	private UserAccountRepository userRepo;

	@Mock
	private TeamMembershipRepository membershipRepo;

	@Mock
	private AuthorizationService authService;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private ManagerReviewService service;

	private final UUID teamId = UUID.randomUUID();
	private final UUID managerId = UUID.randomUUID();
	private final UUID icId = UUID.randomUUID();
	private final UUID planId = UUID.randomUUID();
	private final LocalDate weekStart = LocalDate.of(2025, 6, 9);

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan plan(PlanState state, int capacityPoints, boolean systemLocked) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		p.setTeamId(teamId);
		p.setWeekStartDate(weekStart);
		p.setState(state);
		p.setCapacityBudgetPoints(capacityPoints);
		p.setLockDeadline(Instant.now().minusSeconds(3600)); // past deadline
		p.setReconcileDeadline(Instant.now().minusSeconds(1800)); // past deadline
		p.setSystemLockedWithErrors(systemLocked);
		return p;
	}

	private WeeklyPlan planWithFutureDeadlines(PlanState state, int capacityPoints) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		p.setTeamId(teamId);
		p.setWeekStartDate(weekStart);
		p.setState(state);
		p.setCapacityBudgetPoints(capacityPoints);
		p.setLockDeadline(Instant.now().plusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7200));
		p.setSystemLockedWithErrors(false);
		return p;
	}

	private WeeklyCommit commit(ChessPiece piece, int points, int streak) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(icId);
		c.setTitle("Test Commit");
		c.setChessPiece(piece);
		c.setPriorityOrder(1);
		c.setEstimatePoints(points);
		c.setCarryForwardStreak(streak);
		return c;
	}

	private ScopeChangeEvent scopeChangeEvent(ScopeChangeCategory category, String prevVal, String newVal) {
		ScopeChangeEvent e = new ScopeChangeEvent();
		e.setId(UUID.randomUUID());
		e.setPlanId(planId);
		e.setCategory(category);
		e.setChangedByUserId(managerId);
		e.setReason("Test reason");
		e.setPreviousValue(prevVal);
		e.setNewValue(newVal);
		return e;
	}

	private ManagerReviewException exception(ExceptionType type, ExceptionSeverity severity) {
		ManagerReviewException e = new ManagerReviewException();
		e.setId(UUID.randomUUID());
		e.setTeamId(teamId);
		e.setPlanId(planId);
		e.setUserId(icId);
		e.setExceptionType(type);
		e.setSeverity(severity);
		e.setDescription("Test exception");
		e.setWeekStartDate(weekStart);
		e.setResolved(false);
		return e;
	}

	@BeforeEach
	void stubExceptionSave() {
		lenient().when(exceptionRepo.save(any(ManagerReviewException.class))).thenAnswer(inv -> {
			ManagerReviewException e = inv.getArgument(0);
			if (e.getId() == null)
				e.setId(UUID.randomUUID());
			return e;
		});
		lenient().when(
				exceptionRepo.findByPlanIdAndExceptionTypeAndResolved(any(), any(ExceptionType.class), anyBoolean()))
				.thenReturn(Optional.empty());
	}

	// =========================================================================
	// Exception queue — IC cannot call
	// =========================================================================

	@Test
	void getExceptionQueue_icThrowsAccessDenied() {
		when(authService.getCallerRole(icId)).thenReturn(UserRole.IC);

		assertThatThrownBy(() -> service.getExceptionQueue(teamId, weekStart, icId))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("MANAGER or ADMIN");
	}

	// =========================================================================
	// Exception detection — MISSED_LOCK
	// =========================================================================

	@Test
	void detectException_missedLock_whenDraftPastDeadline() {
		// Plan is DRAFT, past lock deadline, but reconcile deadline is in the future
		// so only MISSED_LOCK is triggered.
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		p.setTeamId(teamId);
		p.setWeekStartDate(weekStart);
		p.setState(PlanState.DRAFT);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now().minusSeconds(3600)); // past deadline
		p.setReconcileDeadline(Instant.now().plusSeconds(7200)); // future deadline
		p.setSystemLockedWithErrors(false);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.MISSED_LOCK);
	}

	@Test
	void detectException_noMissedLock_whenFutureDeadline() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.DRAFT, 10);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// Exception detection — AUTO_LOCKED
	// =========================================================================

	@Test
	void detectException_autoLocked_whenSystemLockedWithErrors() {
		// Plan is LOCKED with errors, reconcile deadline is in the future
		// so only AUTO_LOCKED is triggered (MISSED_RECONCILE not yet applicable).
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		p.setTeamId(teamId);
		p.setWeekStartDate(weekStart);
		p.setState(PlanState.LOCKED);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now().minusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7200)); // future deadline
		p.setSystemLockedWithErrors(true);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.AUTO_LOCKED);
		assertThat(captor.getValue().getSeverity()).isEqualTo(ExceptionSeverity.HIGH);
	}

	// =========================================================================
	// Exception detection — MISSED_RECONCILE
	// =========================================================================

	@Test
	void detectException_missedReconcile_whenLockedPastReconcileDeadline() {
		WeeklyPlan p = plan(PlanState.LOCKED, 10, false);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.MISSED_RECONCILE);
	}

	// =========================================================================
	// Exception detection — OVER_BUDGET
	// =========================================================================

	@Test
	void detectException_overBudget_whenPointsExceedCapacity() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 5); // capacity = 5

		// Total points = 8, exceeds capacity 5
		WeeklyCommit c1 = commit(ChessPiece.KING, 5, 0);
		WeeklyCommit c2 = commit(ChessPiece.ROOK, 3, 0);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1, c2));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.OVER_BUDGET);
		assertThat(captor.getValue().getSeverity()).isEqualTo(ExceptionSeverity.MEDIUM);
	}

	@Test
	void detectException_noOverBudget_whenPointsWithinCapacity() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		WeeklyCommit c = commit(ChessPiece.ROOK, 5, 0); // 5 <= 10

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// Exception detection — REPEATED_CARRY_FORWARD
	// =========================================================================

	@Test
	void detectException_repeatedCarryForward_whenStreakAtLeast2() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		WeeklyCommit c = commit(ChessPiece.ROOK, 3, 2); // streak = 2

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.REPEATED_CARRY_FORWARD);
		assertThat(captor.getValue().getSeverity()).isEqualTo(ExceptionSeverity.LOW);
	}

	@Test
	void detectException_noRepeatedCarryForward_whenStreakBelow2() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		WeeklyCommit c = commit(ChessPiece.ROOK, 3, 1); // streak = 1

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// Exception detection — HIGH_SCOPE_VOLATILITY
	// =========================================================================

	@Test
	void detectException_highScopeVolatility_whenMoreThan3Events() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		// 4 scope change events (> threshold 3)
		List<ScopeChangeEvent> events = List.of(scopeChangeEvent(ScopeChangeCategory.ESTIMATE_CHANGED, "3", "5"),
				scopeChangeEvent(ScopeChangeCategory.ESTIMATE_CHANGED, "5", "3"),
				scopeChangeEvent(ScopeChangeCategory.COMMIT_ADDED, null, "{}"),
				scopeChangeEvent(ScopeChangeCategory.COMMIT_REMOVED, null, null));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(events);
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.HIGH_SCOPE_VOLATILITY);
	}

	@Test
	void detectException_noHighVolatility_whenAtMost3Events() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		List<ScopeChangeEvent> events = List.of(scopeChangeEvent(ScopeChangeCategory.ESTIMATE_CHANGED, "3", "5"),
				scopeChangeEvent(ScopeChangeCategory.ESTIMATE_CHANGED, "5", "3"),
				scopeChangeEvent(ScopeChangeCategory.COMMIT_ADDED, null, "{}"));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(events);
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// Exception detection — KING_CHANGED_POST_LOCK
	// =========================================================================

	@Test
	void detectKingChange_whenCommitAddedWithKing() throws Exception {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		UUID kingCommitId = UUID.randomUUID();
		WeeklyCommit kingCommit = commit(ChessPiece.KING, 5, 0);
		kingCommit.setId(kingCommitId);

		String newValue = objectMapper.writeValueAsString(java.util.Map.of("chessPiece", "KING", "title", "King task"));
		ScopeChangeEvent addKing = scopeChangeEvent(ScopeChangeCategory.COMMIT_ADDED, null, newValue);
		addKing.setCommitId(kingCommitId);

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(kingCommit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of(addKing));
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.KING_CHANGED_POST_LOCK);
		assertThat(captor.getValue().getSeverity()).isEqualTo(ExceptionSeverity.HIGH);
	}

	@Test
	void detectKingChange_whenChessPieceChangedToKing() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		ScopeChangeEvent changeToKing = scopeChangeEvent(ScopeChangeCategory.CHESS_PIECE_CHANGED, "ROOK", "KING");

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of(changeToKing));
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.KING_CHANGED_POST_LOCK);
	}

	@Test
	void detectKingChange_noExceptionWhenNoKingEvents() {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		WeeklyCommit rook = commit(ChessPiece.ROOK, 3, 0);
		ScopeChangeEvent addRook = scopeChangeEvent(ScopeChangeCategory.COMMIT_ADDED, null,
				"{\"chessPiece\":\"ROOK\"}");

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(rook));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of(addRook));
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.empty());

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// Exception detection — POST_LOCK_SCOPE_INCREASE
	// =========================================================================

	@Test
	void detectException_postLockScopeIncrease_whenPointsGrowMoreThan20Pct() throws Exception {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		// Baseline: 5 points, current: 7 points (40% increase > 20%)
		LockSnapshotHeader header = new LockSnapshotHeader();
		header.setId(UUID.randomUUID());
		header.setPlanId(planId);
		header.setSnapshotPayload("{}");

		String snapshotData = objectMapper.writeValueAsString(java.util.Map.of("estimatePoints", 5));
		LockSnapshotCommit sc = new LockSnapshotCommit();
		sc.setId(UUID.randomUUID());
		sc.setSnapshotId(header.getId());
		sc.setCommitId(UUID.randomUUID());
		sc.setSnapshotData(snapshotData);

		WeeklyCommit currentCommit = commit(ChessPiece.KING, 7, 0); // current = 7 pts

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(currentCommit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.of(header));
		when(lockCommitRepo.findBySnapshotId(header.getId())).thenReturn(List.of(sc));

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		ArgumentCaptor<ManagerReviewException> captor = ArgumentCaptor.forClass(ManagerReviewException.class);
		verify(exceptionRepo).save(captor.capture());
		assertThat(captor.getValue().getExceptionType()).isEqualTo(ExceptionType.POST_LOCK_SCOPE_INCREASE);
		assertThat(captor.getValue().getSeverity()).isEqualTo(ExceptionSeverity.HIGH);
	}

	@Test
	void detectException_noPostLockScopeIncrease_whenPointsGrow20PctOrLess() throws Exception {
		WeeklyPlan p = planWithFutureDeadlines(PlanState.LOCKED, 10);

		LockSnapshotHeader header = new LockSnapshotHeader();
		header.setId(UUID.randomUUID());
		header.setPlanId(planId);
		header.setSnapshotPayload("{}");

		String snapshotData = objectMapper.writeValueAsString(java.util.Map.of("estimatePoints", 5));
		LockSnapshotCommit sc = new LockSnapshotCommit();
		sc.setId(UUID.randomUUID());
		sc.setSnapshotId(header.getId());
		sc.setCommitId(UUID.randomUUID());
		sc.setSnapshotData(snapshotData);

		WeeklyCommit currentCommit = commit(ChessPiece.KING, 6, 0); // 20% increase (not > 20%)

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(currentCommit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.of(header));
		when(lockCommitRepo.findBySnapshotId(header.getId())).thenReturn(List.of(sc));

		service.detectAndPersistExceptions(p, teamId, weekStart, Instant.now());

		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// getExceptionQueue — idempotency (no duplicates)
	// =========================================================================

	@Test
	void getExceptionQueue_doesNotCreateDuplicateExceptions() {
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);

		// Plan is DRAFT, past lock deadline, future reconcile deadline → only
		// MISSED_LOCK
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		p.setTeamId(teamId);
		p.setWeekStartDate(weekStart);
		p.setState(PlanState.DRAFT);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now().minusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7200));
		p.setSystemLockedWithErrors(false);

		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		// MISSED_LOCK exception already exists (unresolved) — stub to return existing
		when(exceptionRepo.findByPlanIdAndExceptionTypeAndResolved(planId, ExceptionType.MISSED_LOCK, false))
				.thenReturn(Optional.of(exception(ExceptionType.MISSED_LOCK, ExceptionSeverity.MEDIUM)));
		when(exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false))
				.thenReturn(List.of(exception(ExceptionType.MISSED_LOCK, ExceptionSeverity.MEDIUM)));

		service.getExceptionQueue(teamId, weekStart, managerId);

		// Should NOT create a new MISSED_LOCK exception since one already exists
		verify(exceptionRepo, never()).save(any());
	}

	// =========================================================================
	// addComment — authorization
	// =========================================================================

	@Test
	void addComment_onlyDirectManagerCanComment() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		doNothing().when(authService).checkIsDirectManager(managerId, icId);
		when(commentRepo.save(any(ManagerComment.class))).thenAnswer(inv -> {
			ManagerComment c = inv.getArgument(0);
			c.setId(UUID.randomUUID());
			return c;
		});

		CommentResponse response = service.addComment("PLAN", planId, managerId, "Good work!");

		assertThat(response).isNotNull();
		assertThat(response.content()).isEqualTo("Good work!");
		assertThat(response.planId()).isEqualTo(planId);
	}

	@Test
	void addComment_nonManagerThrowsForbidden() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(icId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		doThrow(new AccessDeniedException("Only MANAGER or ADMIN users may add manager comments")).when(authService)
				.checkIsDirectManager(icId, icId);

		assertThatThrownBy(() -> service.addComment("PLAN", planId, icId, "Comment"))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("MANAGER or ADMIN");
	}

	@Test
	void addComment_unknownTargetTypeThrowsValidation() {
		assertThatThrownBy(() -> service.addComment("INVALID", planId, managerId, "Text"))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Unknown target type");
	}

	@Test
	void addComment_blankTextThrowsValidation() {
		assertThatThrownBy(() -> service.addComment("PLAN", planId, managerId, ""))
				.isInstanceOf(PlanValidationException.class);
	}

	// =========================================================================
	// addComment — commit-level
	// =========================================================================

	@Test
	void addComment_onCommitTargetType() {
		UUID commitId = UUID.randomUUID();
		WeeklyCommit c = new WeeklyCommit();
		c.setId(commitId);
		c.setOwnerUserId(icId);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));
		doNothing().when(authService).checkIsDirectManager(managerId, icId);
		when(commentRepo.save(any(ManagerComment.class))).thenAnswer(inv -> {
			ManagerComment mc = inv.getArgument(0);
			mc.setId(UUID.randomUUID());
			return mc;
		});

		CommentResponse response = service.addComment("COMMIT", commitId, managerId, "Review note");

		assertThat(response.commitId()).isEqualTo(commitId);
		assertThat(response.planId()).isNull();
	}

	// =========================================================================
	// setCapacityOverride — application
	// =========================================================================

	@Test
	void setCapacityOverride_managerCanOverride() {
		doNothing().when(authService).checkIsDirectManager(managerId, icId);
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(icId, weekStart)).thenReturn(Optional.empty());
		when(capacityOverrideRepo.save(any(CapacityOverride.class))).thenAnswer(inv -> {
			CapacityOverride co = inv.getArgument(0);
			co.setId(UUID.randomUUID());
			return co;
		});
		when(planRepo.findByOwnerUserIdAndWeekStartDate(icId, weekStart)).thenReturn(Optional.empty());

		CapacityOverrideResponse response = service.setCapacityOverride(managerId, icId, weekStart, 8, "Holiday week");

		assertThat(response).isNotNull();
		assertThat(response.budgetPoints()).isEqualTo(8);
		assertThat(response.userId()).isEqualTo(icId);
		assertThat(response.setByManagerId()).isEqualTo(managerId);
		assertThat(response.reason()).isEqualTo("Holiday week");
	}

	@Test
	void setCapacityOverride_nonManagerThrowsForbidden() {
		UUID targetUserId = UUID.randomUUID();
		doThrow(new AccessDeniedException("Only MANAGER or ADMIN users may set capacity overrides")).when(authService)
				.checkIsDirectManager(icId, targetUserId);

		assertThatThrownBy(() -> service.setCapacityOverride(icId, targetUserId, weekStart, 5, null))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void setCapacityOverride_updatesExistingPlanCapacity() {
		doNothing().when(authService).checkIsDirectManager(managerId, icId);

		CapacityOverride existing = new CapacityOverride();
		existing.setId(UUID.randomUUID());
		existing.setUserId(icId);
		existing.setWeekStartDate(weekStart);
		existing.setBudgetPoints(10);
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(icId, weekStart)).thenReturn(Optional.of(existing));
		when(capacityOverrideRepo.save(any(CapacityOverride.class))).thenAnswer(inv -> inv.getArgument(0));

		WeeklyPlan existingPlan = planWithFutureDeadlines(PlanState.DRAFT, 10);
		when(planRepo.findByOwnerUserIdAndWeekStartDate(icId, weekStart)).thenReturn(Optional.of(existingPlan));
		when(planRepo.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

		service.setCapacityOverride(managerId, icId, weekStart, 7, "Sick day");

		assertThat(existingPlan.getCapacityBudgetPoints()).isEqualTo(7);
	}

	// =========================================================================
	// resolveException
	// =========================================================================

	@Test
	void resolveException_marksResolved() {
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);

		ManagerReviewException exc = exception(ExceptionType.OVER_BUDGET, ExceptionSeverity.MEDIUM);
		UUID excId = exc.getId();
		when(exceptionRepo.findById(excId)).thenReturn(Optional.of(exc));
		when(exceptionRepo.save(any(ManagerReviewException.class))).thenAnswer(inv -> inv.getArgument(0));

		ExceptionResponse response = service.resolveException(excId, "Acknowledged and accepted.", managerId);

		assertThat(response.resolved()).isTrue();
		assertThat(response.resolution()).isEqualTo("Acknowledged and accepted.");
		assertThat(response.resolvedById()).isEqualTo(managerId);
	}

	@Test
	void resolveException_icCannotResolve() {
		when(authService.getCallerRole(icId)).thenReturn(UserRole.IC);

		assertThatThrownBy(() -> service.resolveException(UUID.randomUUID(), "Resolution", icId))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("MANAGER or ADMIN");
	}

	@Test
	void resolveException_alreadyResolvedThrowsValidation() {
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);

		ManagerReviewException exc = exception(ExceptionType.OVER_BUDGET, ExceptionSeverity.MEDIUM);
		exc.setResolved(true);
		UUID excId = exc.getId();
		when(exceptionRepo.findById(excId)).thenReturn(Optional.of(exc));

		assertThatThrownBy(() -> service.resolveException(excId, "Already handled", managerId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("already resolved");
	}

	@Test
	void resolveException_blankResolutionThrowsValidation() {
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);

		ManagerReviewException exc = exception(ExceptionType.OVER_BUDGET, ExceptionSeverity.MEDIUM);
		UUID excId = exc.getId();
		when(exceptionRepo.findById(excId)).thenReturn(Optional.of(exc));

		assertThatThrownBy(() -> service.resolveException(excId, "", managerId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("required");
	}

	@Test
	void resolveException_notFoundThrowsNotFound() {
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		UUID nonExistent = UUID.randomUUID();
		when(exceptionRepo.findById(nonExistent)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.resolveException(nonExistent, "Resolution", managerId))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// =========================================================================
	// getExceptionQueue — ordering (King exceptions first within HIGH)
	// =========================================================================

	@Test
	void getExceptionQueue_kingExceptionOrderedFirstAmongHighSeverity() {
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		ManagerReviewException autoLocked = exception(ExceptionType.AUTO_LOCKED, ExceptionSeverity.HIGH);
		ManagerReviewException kingChange = exception(ExceptionType.KING_CHANGED_POST_LOCK, ExceptionSeverity.HIGH);
		ManagerReviewException overBudget = exception(ExceptionType.OVER_BUDGET, ExceptionSeverity.MEDIUM);

		when(exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false))
				.thenReturn(List.of(overBudget, autoLocked, kingChange));

		List<ExceptionResponse> queue = service.getExceptionQueue(teamId, weekStart, managerId);

		assertThat(queue).hasSize(3);
		assertThat(queue.get(0).exceptionType()).isEqualTo(ExceptionType.KING_CHANGED_POST_LOCK);
		assertThat(queue.get(1).exceptionType()).isEqualTo(ExceptionType.AUTO_LOCKED);
		assertThat(queue.get(2).exceptionType()).isEqualTo(ExceptionType.OVER_BUDGET);
	}
}
