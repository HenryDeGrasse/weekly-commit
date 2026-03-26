package com.weeklycommit.lock.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoLockJobTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private LockService lockService;

	@InjectMocks
	private AutoLockJob job;

	private WeeklyPlan makePlan(UUID id) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(id);
		p.setState(PlanState.DRAFT);
		return p;
	}

	@Test
	void autoLock_noExpiredPlans_doesNothing() {
		when(planRepo.findByStateAndLockDeadlineBefore(eq(PlanState.DRAFT), any(Instant.class))).thenReturn(List.of());

		job.autoLockExpiredDrafts();

		verifyNoInteractions(lockService);
	}

	@Test
	void autoLock_expiredPlans_locksEach() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		when(planRepo.findByStateAndLockDeadlineBefore(eq(PlanState.DRAFT), any(Instant.class)))
				.thenReturn(List.of(makePlan(id1), makePlan(id2)));

		job.autoLockExpiredDrafts();

		verify(lockService).autoLockPlan(id1);
		verify(lockService).autoLockPlan(id2);
	}

	@Test
	void autoLock_oneFailsOtherContinues() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		when(planRepo.findByStateAndLockDeadlineBefore(eq(PlanState.DRAFT), any(Instant.class)))
				.thenReturn(List.of(makePlan(id1), makePlan(id2)));
		doThrow(new RuntimeException("lock failed")).when(lockService).autoLockPlan(id1);

		job.autoLockExpiredDrafts();

		// Second plan should still be processed despite first failing
		verify(lockService).autoLockPlan(id1);
		verify(lockService).autoLockPlan(id2);
	}
}
