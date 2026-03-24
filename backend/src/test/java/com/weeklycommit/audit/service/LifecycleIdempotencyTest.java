package com.weeklycommit.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weeklycommit.ai.service.RiskDetectionService;
import com.weeklycommit.domain.entity.LockSnapshotCommit;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.notification.service.NotificationService;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.report.service.ReadModelRefreshService;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that lifecycle operations are idempotent (double-run produces no
 * duplicates) and that snapshot immutability is enforced.
 */
@ExtendWith(MockitoExtension.class)
class LifecycleIdempotencyTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private LockSnapshotHeaderRepository headerRepo;

	@Mock
	private LockSnapshotCommitRepository commitSnapshotRepo;

	@Mock
	private RcdoNodeRepository rcdoNodeRepo;

	@Mock
	private NotificationService notificationService;

	@Mock
	private RiskDetectionService riskDetectionService;

	@Mock
	private ReadModelRefreshService readModelRefreshService;

	@Mock
	private AuditLogService auditLogService;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

	@InjectMocks
	private LockService lockService;

	private final UUID planId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan draftPlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(userId);
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(PlanState.DRAFT);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now().plusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7 * 24 * 3600));
		return p;
	}

	private WeeklyCommit validCommit() {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(userId);
		c.setTitle("Commit 1");
		c.setChessPiece(ChessPiece.ROOK);
		c.setPriorityOrder(1);
		c.setRcdoNodeId(UUID.randomUUID());
		c.setEstimatePoints(3);
		return c;
	}

	@BeforeEach
	void stubDefaults() {
		lenient().when(planRepo.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(headerRepo.save(any(LockSnapshotHeader.class))).thenAnswer(inv -> {
			LockSnapshotHeader h = inv.getArgument(0);
			if (h.getId() == null)
				h.setId(UUID.randomUUID());
			return h;
		});
		lenient().when(commitSnapshotRepo.save(any(LockSnapshotCommit.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(rcdoNodeRepo.findById(any())).thenReturn(Optional.empty());
		// Default: no existing snapshot
		lenient().when(headerRepo.findByPlanId(any())).thenReturn(Optional.empty());
	}

	// =========================================================================
	// Idempotency: autoLockPlan called twice produces exactly one snapshot
	// =========================================================================

	@Test
	void autoLock_calledTwice_secondCallIsNoOp() {
		WeeklyPlan plan = draftPlan();
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit()));

		// First call — transitions to LOCKED
		lockService.autoLockPlan(planId);
		assertThat(plan.getState()).isEqualTo(PlanState.LOCKED);

		// Record the save count after first call (state save + snapshot backref save =
		// 2)
		int savesAfterFirstCall = org.mockito.Mockito.mockingDetails(planRepo).getInvocations().stream()
				.filter(i -> i.getMethod().getName().equals("save")).mapToInt(i -> 1).sum();

		// Second call — plan is already LOCKED, should be a no-op
		lockService.autoLockPlan(planId);

		// Save count should not have increased after second call
		int savesAfterSecondCall = org.mockito.Mockito.mockingDetails(planRepo).getInvocations().stream()
				.filter(i -> i.getMethod().getName().equals("save")).mapToInt(i -> 1).sum();
		assertThat(savesAfterSecondCall).isEqualTo(savesAfterFirstCall);

		// Snapshot created exactly once (first lock only)
		verify(headerRepo, times(1)).save(any(LockSnapshotHeader.class));
	}

	@Test
	void manualLock_calledTwice_secondCallIsIdempotentSuccess() {
		WeeklyPlan plan = draftPlan();
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit()));

		// First call
		LockResponse r1 = lockService.lockPlan(planId, userId);
		assertThat(r1.success()).isTrue();

		// Record save count after first successful lock
		int savesAfterFirst = org.mockito.Mockito.mockingDetails(planRepo).getInvocations().stream()
				.filter(i -> i.getMethod().getName().equals("save")).mapToInt(i -> 1).sum();
		assertThat(savesAfterFirst).isGreaterThan(0);

		// Second call — already LOCKED, must return idempotent success without
		// trying to save again
		LockResponse r2 = lockService.lockPlan(planId, userId);
		assertThat(r2.success()).isTrue();

		// No additional saves after second call
		int savesAfterSecond = org.mockito.Mockito.mockingDetails(planRepo).getInvocations().stream()
				.filter(i -> i.getMethod().getName().equals("save")).mapToInt(i -> 1).sum();
		assertThat(savesAfterSecond).isEqualTo(savesAfterFirst);
	}

	// =========================================================================
	// Snapshot immutability: lockPlan when snapshot already exists
	// =========================================================================

	@Test
	void lockPlan_snapshotAlreadyExists_throwsImmutabilityError() {
		// Plan is DRAFT but somehow a snapshot already exists (data corruption
		// scenario)
		WeeklyPlan plan = draftPlan();
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit()));

		// Stub: snapshot already exists
		LockSnapshotHeader existing = new LockSnapshotHeader();
		existing.setId(UUID.randomUUID());
		existing.setPlanId(planId);
		existing.setSnapshotPayload("{}");
		when(headerRepo.findByPlanId(planId)).thenReturn(Optional.of(existing));

		// Lock should fail with immutability error
		assertThatThrownBy(() -> lockService.lockPlan(planId, userId)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("immutable");
	}

	@Test
	void autoLockPlan_snapshotAlreadyExists_throwsImmutabilityError() {
		WeeklyPlan plan = draftPlan();
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit()));

		LockSnapshotHeader existing = new LockSnapshotHeader();
		existing.setId(UUID.randomUUID());
		existing.setPlanId(planId);
		existing.setSnapshotPayload("{}");
		when(headerRepo.findByPlanId(planId)).thenReturn(Optional.of(existing));

		// autoLockPlan does not bubble exceptions (it's a scheduled job) but
		// in tests the exception propagates because the try-catch is in the job,
		// not the service — so this will throw
		assertThatThrownBy(() -> lockService.autoLockPlan(planId)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("immutable");
	}
}
