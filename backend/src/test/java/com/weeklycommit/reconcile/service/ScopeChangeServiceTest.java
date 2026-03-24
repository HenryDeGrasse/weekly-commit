package com.weeklycommit.reconcile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.notification.service.NotificationService;
import com.weeklycommit.report.service.ReadModelRefreshService;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.reconcile.dto.AddCommitData;
import com.weeklycommit.reconcile.dto.EditCommitChanges;
import com.weeklycommit.reconcile.dto.ManagerException;
import com.weeklycommit.reconcile.dto.ScopeChangeTimelineResponse;
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
class ScopeChangeServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private ScopeChangeEventRepository eventRepo;

	@Mock
	private LockSnapshotHeaderRepository lockSnapshotRepo;

	@Mock
	private NotificationService notificationService;

	@Mock
	private ReadModelRefreshService readModelRefreshService;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

	@InjectMocks
	private ScopeChangeService service;

	private final UUID planId = UUID.randomUUID();
	private final UUID actorId = UUID.randomUUID();
	private final UUID commitId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan lockedPlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(actorId);
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(PlanState.LOCKED);
		p.setLockDeadline(Instant.now());
		p.setReconcileDeadline(Instant.now().plusSeconds(7 * 24 * 3600));
		return p;
	}

	private WeeklyCommit commit(ChessPiece piece) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(commitId);
		c.setPlanId(planId);
		c.setOwnerUserId(actorId);
		c.setTitle("Test commit");
		c.setChessPiece(piece);
		c.setPriorityOrder(1);
		c.setEstimatePoints(3);
		return c;
	}

	private ScopeChangeEvent scopeEvent(ScopeChangeCategory cat, String prevVal, String newVal) {
		ScopeChangeEvent e = new ScopeChangeEvent();
		e.setId(UUID.randomUUID());
		e.setPlanId(planId);
		e.setCommitId(commitId);
		e.setCategory(cat);
		e.setChangedByUserId(actorId);
		e.setReason("test reason");
		e.setPreviousValue(prevVal);
		e.setNewValue(newVal);
		return e;
	}

	@BeforeEach
	void stubSave() {
		lenient().when(eventRepo.save(any(ScopeChangeEvent.class))).thenAnswer(inv -> {
			ScopeChangeEvent e = inv.getArgument(0);
			if (e.getId() == null)
				e.setId(UUID.randomUUID());
			return e;
		});
		lenient().when(commitRepo.save(any(WeeklyCommit.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(planRepo.findById(planId)).thenReturn(Optional.of(lockedPlan()));
		lenient().when(eventRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of());
		lenient().when(lockSnapshotRepo.findByPlanId(planId)).thenReturn(Optional.empty());
	}

	// =========================================================================
	// addPostLockCommit
	// =========================================================================

	@Test
	void addPostLockCommit_createsCommitAndEvent() {
		AddCommitData data = new AddCommitData("New Commit", ChessPiece.ROOK, null, null, null, 2, null);
		when(commitRepo.countByPlanId(planId)).thenReturn(1L);

		service.addPostLockCommit(planId, data, "Emergency scope", actorId);

		// Commit saved
		verify(commitRepo).save(any(WeeklyCommit.class));

		// COMMIT_ADDED event saved
		ArgumentCaptor<ScopeChangeEvent> captor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(eventRepo, times(1)).save(captor.capture());
		ScopeChangeEvent event = captor.getValue();
		assertThat(event.getCategory()).isEqualTo(ScopeChangeCategory.COMMIT_ADDED);
		assertThat(event.getReason()).isEqualTo("Emergency scope");
		assertThat(event.getNewValue()).isNotBlank();
		assertThat(event.getPreviousValue()).isNull();
	}

	@Test
	void addPostLockCommit_kingCommit_notifiesManager() {
		AddCommitData data = new AddCommitData("New Commit", ChessPiece.KING, null, null, null, 2, null);
		when(commitRepo.countByPlanId(planId)).thenReturn(1L);
		when(notificationService.findManagerForTeam(any())).thenReturn(java.util.Optional.of(UUID.randomUUID()));

		service.addPostLockCommit(planId, data, "Emergency scope", actorId);

		verify(notificationService).createNotification(any(), any(), any(), any(), eq(planId), eq("PLAN"));
	}

	@Test
	void addPostLockCommit_emptyReason_throwsValidation() {
		AddCommitData data = new AddCommitData("New Commit", ChessPiece.ROOK, null, null, null, 2, null);

		assertThatThrownBy(() -> service.addPostLockCommit(planId, data, "  ", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("reason");
	}

	@Test
	void addPostLockCommit_missingCommitData_throwsValidation() {
		assertThatThrownBy(() -> service.addPostLockCommit(planId, null, "Emergency scope", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Commit data");
	}

	@Test
	void addPostLockCommit_planNotLocked_throwsValidation() {
		WeeklyPlan draft = lockedPlan();
		draft.setState(PlanState.DRAFT);
		when(planRepo.findById(planId)).thenReturn(Optional.of(draft));

		AddCommitData data = new AddCommitData("New Commit", ChessPiece.ROOK, null, null, null, 2, null);

		assertThatThrownBy(() -> service.addPostLockCommit(planId, data, "reason", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("LOCKED");
	}

	// =========================================================================
	// removePostLockCommit
	// =========================================================================

	@Test
	void removePostLockCommit_setsOutcomeCanceledAndRecordsEvent() {
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		service.removePostLockCommit(planId, commitId, "No longer needed", actorId);

		assertThat(c.getOutcome()).isEqualTo(CommitOutcome.CANCELED);
		assertThat(c.getOutcomeNotes()).isEqualTo("No longer needed");

		ArgumentCaptor<ScopeChangeEvent> captor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(eventRepo, times(1)).save(captor.capture());
		assertThat(captor.getValue().getCategory()).isEqualTo(ScopeChangeCategory.COMMIT_REMOVED);
	}

	@Test
	void removePostLockCommit_emptyReason_throwsValidation() {
		// Validation throws before the repo is consulted — no stub needed.
		assertThatThrownBy(() -> service.removePostLockCommit(planId, commitId, "", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("reason");
	}

	@Test
	void removePostLockCommit_rejectsCommitFromDifferentPlan() {
		UUID otherPlanId = UUID.randomUUID();
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.removePostLockCommit(otherPlanId, commitId, "No longer needed", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("does not belong to plan");
	}

	// =========================================================================
	// editPostLockCommit — per-field scope change events with before/after values
	// =========================================================================

	@Test
	void editPostLockCommit_estimateChanged_recordsEventWithBeforeAfter() {
		WeeklyCommit c = commit(ChessPiece.ROOK); // estimatePoints = 3
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		service.editPostLockCommit(planId, commitId, new EditCommitChanges(5, null, null, null), "Re-estimated",
				actorId);

		ArgumentCaptor<ScopeChangeEvent> captor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(eventRepo, times(1)).save(captor.capture());
		ScopeChangeEvent event = captor.getValue();
		assertThat(event.getCategory()).isEqualTo(ScopeChangeCategory.ESTIMATE_CHANGED);
		assertThat(event.getPreviousValue()).isEqualTo("3");
		assertThat(event.getNewValue()).isEqualTo("5");
		assertThat(c.getEstimatePoints()).isEqualTo(5);
	}

	@Test
	void editPostLockCommit_chessPieceChanged_recordsEventWithBeforeAfter() {
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		service.editPostLockCommit(planId, commitId, new EditCommitChanges(null, ChessPiece.BISHOP, null, null),
				"Reprioritized", actorId);

		ArgumentCaptor<ScopeChangeEvent> captor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(eventRepo, times(1)).save(captor.capture());
		ScopeChangeEvent event = captor.getValue();
		assertThat(event.getCategory()).isEqualTo(ScopeChangeCategory.CHESS_PIECE_CHANGED);
		assertThat(event.getPreviousValue()).isEqualTo("ROOK");
		assertThat(event.getNewValue()).isEqualTo("BISHOP");
	}

	@Test
	void editPostLockCommit_rcdoChanged_recordsRcdoChangedEvent() {
		UUID oldRcdo = UUID.randomUUID();
		UUID newRcdo = UUID.randomUUID();
		WeeklyCommit c = commit(ChessPiece.ROOK);
		c.setRcdoNodeId(oldRcdo);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		service.editPostLockCommit(planId, commitId, new EditCommitChanges(null, null, newRcdo, null),
				"Strategy realigned", actorId);

		ArgumentCaptor<ScopeChangeEvent> captor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(eventRepo, times(1)).save(captor.capture());
		assertThat(captor.getValue().getCategory()).isEqualTo(ScopeChangeCategory.RCDO_CHANGED);
		assertThat(captor.getValue().getPreviousValue()).isEqualTo(oldRcdo.toString());
		assertThat(captor.getValue().getNewValue()).isEqualTo(newRcdo.toString());
	}

	@Test
	void editPostLockCommit_priorityChanged_recordsPriorityChangedEvent() {
		WeeklyCommit c = commit(ChessPiece.ROOK); // priorityOrder = 1
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		service.editPostLockCommit(planId, commitId, new EditCommitChanges(null, null, null, 3), "Reordered", actorId);

		ArgumentCaptor<ScopeChangeEvent> captor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(eventRepo, times(1)).save(captor.capture());
		assertThat(captor.getValue().getCategory()).isEqualTo(ScopeChangeCategory.PRIORITY_CHANGED);
		assertThat(captor.getValue().getPreviousValue()).isEqualTo("1");
		assertThat(captor.getValue().getNewValue()).isEqualTo("3");
	}

	@Test
	void editPostLockCommit_multipleFieldsChanged_recordsOneEventPerField() {
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		service.editPostLockCommit(planId, commitId, new EditCommitChanges(5, ChessPiece.BISHOP, null, null),
				"Dual change", actorId);

		// Two events: ESTIMATE_CHANGED + CHESS_PIECE_CHANGED
		verify(eventRepo, times(2)).save(any(ScopeChangeEvent.class));
	}

	@Test
	void editPostLockCommit_noFieldsChange_noEventsRecorded() {
		WeeklyCommit c = commit(ChessPiece.ROOK); // estimatePoints = 3
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		// Same value as current — no change
		service.editPostLockCommit(planId, commitId, new EditCommitChanges(3, null, null, null), "Same value", actorId);

		verify(eventRepo, times(0)).save(any(ScopeChangeEvent.class));
	}

	@Test
	void editPostLockCommit_emptyReason_throwsValidation() {
		// Validation throws before the repo is consulted — no stub needed.
		assertThatThrownBy(() -> service.editPostLockCommit(planId, commitId,
				new EditCommitChanges(5, null, null, null), null, actorId)).isInstanceOf(PlanValidationException.class);
	}

	@Test
	void editPostLockCommit_missingChangesPayload_throwsValidation() {
		assertThatThrownBy(() -> service.editPostLockCommit(planId, commitId, null, "Re-estimated", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Changes payload");
	}

	@Test
	void editPostLockCommit_rejectsCommitFromDifferentPlan() {
		UUID otherPlanId = UUID.randomUUID();
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.editPostLockCommit(otherPlanId, commitId,
				new EditCommitChanges(5, null, null, null), "Re-estimated", actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("does not belong to plan");
	}

	// =========================================================================
	// Manager exception detection
	// =========================================================================

	@Test
	void detectManagerExceptions_kingAdded_flagsKingException() throws Exception {
		String kingJson = objectMapper
				.writeValueAsString(java.util.Map.of("chessPiece", "KING", "title", "Critical item"));
		ScopeChangeEvent e = scopeEvent(ScopeChangeCategory.COMMIT_ADDED, null, kingJson);

		List<ManagerException> exceptions = service.detectManagerExceptions(planId, List.of(e));

		assertThat(exceptions).anyMatch(ex -> ex.type().equals(ManagerException.TYPE_KING_CHANGE));
	}

	@Test
	void detectManagerExceptions_chessPieceChangedToKing_flagsKingException() {
		ScopeChangeEvent e = scopeEvent(ScopeChangeCategory.CHESS_PIECE_CHANGED, "ROOK", "KING");

		List<ManagerException> exceptions = service.detectManagerExceptions(planId, List.of(e));

		assertThat(exceptions).anyMatch(ex -> ex.type().equals(ManagerException.TYPE_KING_CHANGE));
	}

	@Test
	void detectManagerExceptions_chessPieceChangedFromKing_flagsKingException() {
		ScopeChangeEvent e = scopeEvent(ScopeChangeCategory.CHESS_PIECE_CHANGED, "KING", "ROOK");

		List<ManagerException> exceptions = service.detectManagerExceptions(planId, List.of(e));

		assertThat(exceptions).anyMatch(ex -> ex.type().equals(ManagerException.TYPE_KING_CHANGE));
	}

	@Test
	void detectManagerExceptions_rookAdded_noKingException() throws Exception {
		String rookJson = objectMapper.writeValueAsString(java.util.Map.of("chessPiece", "ROOK", "title", "Item"));
		ScopeChangeEvent e = scopeEvent(ScopeChangeCategory.COMMIT_ADDED, null, rookJson);

		List<ManagerException> exceptions = service.detectManagerExceptions(planId, List.of(e));

		assertThat(exceptions).noneMatch(ex -> ex.type().equals(ManagerException.TYPE_KING_CHANGE));
	}

	@Test
	void detectManagerExceptions_pointsIncreasedOver20Pct_flagsPointsException() throws Exception {
		// Baseline: 10 points
		String snapshotPayload = objectMapper.writeValueAsString(java.util.Map.of("commits",
				List.of(java.util.Map.of("estimatePoints", 5), java.util.Map.of("estimatePoints", 5))));
		LockSnapshotHeader header = new LockSnapshotHeader();
		header.setSnapshotPayload(snapshotPayload);
		when(lockSnapshotRepo.findByPlanId(planId)).thenReturn(Optional.of(header));

		// Current: 13 points (30% increase)
		WeeklyCommit c1 = commit(ChessPiece.ROOK);
		c1.setEstimatePoints(8);
		WeeklyCommit c2 = commit(ChessPiece.ROOK);
		c2.setId(UUID.randomUUID());
		c2.setEstimatePoints(5);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1, c2));

		List<ManagerException> exceptions = service.detectManagerExceptions(planId, List.of());

		assertThat(exceptions).anyMatch(ex -> ex.type().equals(ManagerException.TYPE_POINTS_INCREASE_20PCT));
	}

	@Test
	void detectManagerExceptions_pointsIncreasedUnder20Pct_noPointsException() throws Exception {
		// Baseline: 10 points
		String snapshotPayload = objectMapper.writeValueAsString(java.util.Map.of("commits",
				List.of(java.util.Map.of("estimatePoints", 5), java.util.Map.of("estimatePoints", 5))));
		LockSnapshotHeader header = new LockSnapshotHeader();
		header.setSnapshotPayload(snapshotPayload);
		when(lockSnapshotRepo.findByPlanId(planId)).thenReturn(Optional.of(header));

		// Current: 11 points (10% increase — below threshold)
		WeeklyCommit c1 = commit(ChessPiece.ROOK);
		c1.setEstimatePoints(6);
		WeeklyCommit c2 = commit(ChessPiece.ROOK);
		c2.setId(UUID.randomUUID());
		c2.setEstimatePoints(5);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1, c2));

		List<ManagerException> exceptions = service.detectManagerExceptions(planId, List.of());

		assertThat(exceptions).noneMatch(ex -> ex.type().equals(ManagerException.TYPE_POINTS_INCREASE_20PCT));
	}

	// =========================================================================
	// getChangeTimeline
	// =========================================================================

	@Test
	void getChangeTimeline_returnsOrderedEvents() {
		ScopeChangeEvent e1 = scopeEvent(ScopeChangeCategory.COMMIT_ADDED, null, "{}");
		ScopeChangeEvent e2 = scopeEvent(ScopeChangeCategory.ESTIMATE_CHANGED, "3", "5");
		when(eventRepo.findByPlanIdOrderByCreatedAtAsc(planId)).thenReturn(List.of(e1, e2));

		ScopeChangeTimelineResponse result = service.getChangeTimeline(planId);

		assertThat(result.events()).hasSize(2);
		assertThat(result.events().get(0).category()).isEqualTo(ScopeChangeCategory.COMMIT_ADDED);
	}
}
