package com.weeklycommit.reconcile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ReconcileSnapshotCommit;
import com.weeklycommit.domain.entity.ReconcileSnapshotHeader;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ReconcileSnapshotCommitRepository;
import com.weeklycommit.domain.repository.ReconcileSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
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
class ReconciliationServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private ScopeChangeEventRepository eventRepo;

	@Mock
	private LockSnapshotHeaderRepository lockHeaderRepo;

	@Mock
	private LockSnapshotCommitRepository lockCommitRepo;

	@Mock
	private ReconcileSnapshotHeaderRepository reconcileHeaderRepo;

	@Mock
	private ReconcileSnapshotCommitRepository reconcileCommitRepo;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

	@InjectMocks
	private ReconciliationService service;

	private final UUID planId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan plan(PlanState state) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(userId);
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(state);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now());
		p.setReconcileDeadline(Instant.now());
		p.setCompliant(true);
		return p;
	}

	private WeeklyCommit commit(CommitOutcome outcome) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(userId);
		c.setTitle("Test commit");
		c.setChessPiece(ChessPiece.ROOK);
		c.setPriorityOrder(1);
		c.setEstimatePoints(3);
		c.setOutcome(outcome);
		if (outcome == CommitOutcome.PARTIALLY_ACHIEVED || outcome == CommitOutcome.NOT_ACHIEVED) {
			c.setOutcomeNotes("Some notes");
		}
		return c;
	}

	private LockSnapshotHeader lockHeader() {
		LockSnapshotHeader h = new LockSnapshotHeader();
		h.setId(UUID.randomUUID());
		h.setPlanId(planId);
		// Use a literal — avoids calling the @Spy objectMapper inside a when() chain.
		h.setSnapshotPayload("{\"commits\":[]}");
		return h;
	}

	@BeforeEach
	void stubDefaults() {
		lenient().when(planRepo.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(commitRepo.save(any(WeeklyCommit.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(reconcileHeaderRepo.save(any(ReconcileSnapshotHeader.class))).thenAnswer(inv -> {
			ReconcileSnapshotHeader h = inv.getArgument(0);
			if (h.getId() == null)
				h.setId(UUID.randomUUID());
			return h;
		});
		lenient().when(reconcileCommitRepo.save(any(ReconcileSnapshotCommit.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		lenient().when(eventRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		lenient().when(lockHeaderRepo.findByPlanId(planId)).thenReturn(Optional.of(lockHeader()));
		lenient().when(lockCommitRepo.findBySnapshotId(any())).thenReturn(List.of());
	}

	// =========================================================================
	// openReconciliation — state transitions
	// =========================================================================

	@Test
	void openReconciliation_lockedPlan_transitionsToReconciling() {
		WeeklyPlan p = plan(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		service.openReconciliation(planId);

		assertThat(p.getState()).isEqualTo(PlanState.RECONCILING);
		verify(planRepo).save(p);
	}

	@Test
	void openReconciliation_alreadyReconciling_isIdempotent() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));

		service.openReconciliation(planId);

		// No save — already in correct state
		verify(planRepo, never()).save(any());
	}

	@Test
	void openReconciliation_draftPlan_throwsValidation() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan(PlanState.DRAFT)));

		assertThatThrownBy(() -> service.openReconciliation(planId)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("LOCKED");
	}

	// =========================================================================
	// Auto-achieve from DONE ticket
	// =========================================================================

	@Test
	void openReconciliation_linkedTicketDone_autoAchievesCommit() {
		WeeklyPlan p = plan(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));

		UUID workItemId = UUID.randomUUID();
		WeeklyCommit c = commit(null); // no outcome yet
		c.setWorkItemId(workItemId);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		WorkItem wi = new WorkItem();
		wi.setId(workItemId);
		wi.setStatus(TicketStatus.DONE);
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(wi));

		service.openReconciliation(planId);

		assertThat(c.getOutcome()).isEqualTo(CommitOutcome.ACHIEVED);
		verify(commitRepo).save(c);
	}

	@Test
	void openReconciliation_linkedTicketNotDone_doesNotAutoAchieve() {
		WeeklyPlan p = plan(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));

		UUID workItemId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setWorkItemId(workItemId);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		WorkItem wi = new WorkItem();
		wi.setId(workItemId);
		wi.setStatus(TicketStatus.IN_PROGRESS);
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(wi));

		service.openReconciliation(planId);

		assertThat(c.getOutcome()).isNull();
	}

	@Test
	void openReconciliation_commitAlreadyHasOutcome_notOverwritten() {
		WeeklyPlan p = plan(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));

		UUID workItemId = UUID.randomUUID();
		WeeklyCommit c = commit(CommitOutcome.NOT_ACHIEVED); // already set
		c.setWorkItemId(workItemId);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		// workItemRepo is not consulted because outcome is already set — no stub
		// needed.
		service.openReconciliation(planId);

		// Existing outcome is preserved
		assertThat(c.getOutcome()).isEqualTo(CommitOutcome.NOT_ACHIEVED);
		verify(commitRepo, never()).save(c);
	}

	// =========================================================================
	// setCommitOutcome — notes validation
	// =========================================================================

	@Test
	void setCommitOutcome_achieved_noNotesRequired() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		UUID cId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setId(cId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findById(cId)).thenReturn(Optional.of(c));

		CommitResponse result = service.setCommitOutcome(planId, cId, CommitOutcome.ACHIEVED, null);

		assertThat(result.outcome()).isEqualTo(CommitOutcome.ACHIEVED);
	}

	@Test
	void setCommitOutcome_partiallyAchievedWithoutNotes_throwsValidation() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		UUID cId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setId(cId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findById(cId)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.setCommitOutcome(planId, cId, CommitOutcome.PARTIALLY_ACHIEVED, ""))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("notes");
	}

	@Test
	void setCommitOutcome_notAchievedWithoutNotes_throwsValidation() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		UUID cId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setId(cId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findById(cId)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.setCommitOutcome(planId, cId, CommitOutcome.NOT_ACHIEVED, null))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("notes");
	}

	@Test
	void setCommitOutcome_canceledWithoutNotes_throwsValidation() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		UUID cId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setId(cId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findById(cId)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.setCommitOutcome(planId, cId, CommitOutcome.CANCELED, ""))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("notes");
	}

	@Test
	void setCommitOutcome_partiallyAchievedWithNotes_succeeds() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		UUID cId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setId(cId);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findById(cId)).thenReturn(Optional.of(c));

		CommitResponse result = service.setCommitOutcome(planId, cId, CommitOutcome.PARTIALLY_ACHIEVED,
				"Only half done");

		assertThat(result.outcome()).isEqualTo(CommitOutcome.PARTIALLY_ACHIEVED);
		assertThat(result.outcomeNotes()).isEqualTo("Only half done");
	}

	@Test
	void setCommitOutcome_planNotReconciling_throwsValidation() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan(PlanState.LOCKED)));

		assertThatThrownBy(() -> service.setCommitOutcome(planId, UUID.randomUUID(), CommitOutcome.ACHIEVED, null))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("RECONCILING");
	}

	// =========================================================================
	// submitReconciliation — snapshot creation and state transitions
	// =========================================================================

	@Test
	void submitReconciliation_allOutcomesSet_transitionsToReconciled() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit(CommitOutcome.ACHIEVED)));

		service.submitReconciliation(planId);

		assertThat(p.getState()).isEqualTo(PlanState.RECONCILED);
		assertThat(p.getReconcileSnapshotId()).isNotNull();
	}

	@Test
	void submitReconciliation_createsSnapshotHeaderAndCommitRows() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit(CommitOutcome.ACHIEVED)));

		service.submitReconciliation(planId);

		verify(reconcileHeaderRepo).save(any(ReconcileSnapshotHeader.class));
		verify(reconcileCommitRepo).save(any(ReconcileSnapshotCommit.class));
	}

	@Test
	void submitReconciliation_snapshotContainsOutcomeOnCommitRow() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(commit(CommitOutcome.NOT_ACHIEVED)));

		service.submitReconciliation(planId);

		ArgumentCaptor<ReconcileSnapshotCommit> captor = ArgumentCaptor.forClass(ReconcileSnapshotCommit.class);
		verify(reconcileCommitRepo).save(captor.capture());
		assertThat(captor.getValue().getOutcome()).isEqualTo(CommitOutcome.NOT_ACHIEVED);
	}

	@Test
	void submitReconciliation_missingOutcome_throwsValidation() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit(null))); // no outcome

		assertThatThrownBy(() -> service.submitReconciliation(planId)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("outcome");
	}

	@Test
	void submitReconciliation_planNotReconciling_throwsValidation() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan(PlanState.LOCKED)));

		assertThatThrownBy(() -> service.submitReconciliation(planId)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("RECONCILING");
	}

	@Test
	void submitReconciliation_canceledCommitsCountAsHavingOutcome() {
		WeeklyPlan p = plan(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		// One ACHIEVED and one CANCELED — both have outcomes, so should succeed
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(commit(CommitOutcome.ACHIEVED), commit(CommitOutcome.CANCELED)));

		service.submitReconciliation(planId);

		assertThat(p.getState()).isEqualTo(PlanState.RECONCILED);
	}

	// =========================================================================
	// State transition: LOCKED → RECONCILING → RECONCILED
	// =========================================================================

	@Test
	void fullLifecycle_lockedToReconciledViaThreeSteps() {
		// Step 1: open reconciliation
		WeeklyPlan p = plan(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(p));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		service.openReconciliation(planId);
		assertThat(p.getState()).isEqualTo(PlanState.RECONCILING);

		// Step 2: set outcome
		UUID cId = UUID.randomUUID();
		WeeklyCommit c = commit(null);
		c.setId(cId);
		when(commitRepo.findById(cId)).thenReturn(Optional.of(c));

		service.setCommitOutcome(planId, cId, CommitOutcome.ACHIEVED, null);
		assertThat(c.getOutcome()).isEqualTo(CommitOutcome.ACHIEVED);

		// Step 3: submit
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));
		service.submitReconciliation(planId);
		assertThat(p.getState()).isEqualTo(PlanState.RECONCILED);
	}
}
