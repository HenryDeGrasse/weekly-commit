package com.weeklycommit.reconcile.service;

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
class AutoReconcileJobTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private ReconciliationService reconciliationService;

	@InjectMocks
	private AutoReconcileJob job;

	private WeeklyPlan makePlan(UUID id) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(id);
		p.setState(PlanState.LOCKED);
		return p;
	}

	@Test
	void autoReconcile_noExpiredPlans_doesNothing() {
		when(planRepo.findByStateAndReconcileDeadlineBefore(eq(PlanState.LOCKED), any(Instant.class)))
				.thenReturn(List.of());

		job.openExpiredReconciliations();

		verifyNoInteractions(reconciliationService);
	}

	@Test
	void autoReconcile_expiredPlans_opensReconciliationForEach() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		when(planRepo.findByStateAndReconcileDeadlineBefore(eq(PlanState.LOCKED), any(Instant.class)))
				.thenReturn(List.of(makePlan(id1), makePlan(id2)));

		job.openExpiredReconciliations();

		verify(reconciliationService).openReconciliation(id1);
		verify(reconciliationService).openReconciliation(id2);
	}

	@Test
	void autoReconcile_oneFailsOtherContinues() {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		when(planRepo.findByStateAndReconcileDeadlineBefore(eq(PlanState.LOCKED), any(Instant.class)))
				.thenReturn(List.of(makePlan(id1), makePlan(id2)));
		doThrow(new RuntimeException("failed")).when(reconciliationService).openReconciliation(id1);

		job.openExpiredReconciliations();

		verify(reconciliationService).openReconciliation(id1);
		verify(reconciliationService).openReconciliation(id2);
	}
}
