package com.weeklycommit.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.audit.service.AuditLogService;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.dto.CreateCommitRequest;
import com.weeklycommit.plan.dto.UpdateCommitRequest;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.service.RcdoLinkageValidator;
import java.time.LocalDate;
import java.util.ArrayList;
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
class CommitServiceTest {

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private RcdoLinkageValidator rcdoLinkageValidator;

	@Mock
	private AuditLogService auditLogService;

	@InjectMocks
	private CommitService service;

	private final UUID planId = UUID.randomUUID();
	private final UUID actorId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan draftPlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(actorId);
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(PlanState.DRAFT);
		p.setCapacityBudgetPoints(10);
		return p;
	}

	private WeeklyPlan lockedPlan() {
		WeeklyPlan p = draftPlan();
		p.setState(PlanState.LOCKED);
		return p;
	}

	private WeeklyCommit commit(ChessPiece piece) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(actorId);
		c.setTitle("Test commit");
		c.setChessPiece(piece);
		c.setPriorityOrder(1);
		return c;
	}

	private CreateCommitRequest req(ChessPiece piece, String criteria) {
		return new CreateCommitRequest("Ship feature", piece, null, null, null, null, criteria);
	}

	@BeforeEach
	void stubSave() {
		lenient().when(commitRepo.save(any(WeeklyCommit.class))).thenAnswer(inv -> {
			WeeklyCommit c = inv.getArgument(0);
			if (c.getId() == null)
				c.setId(UUID.randomUUID());
			return c;
		});
	}

	// -------------------------------------------------------------------------
	// createCommit — plan state
	// -------------------------------------------------------------------------

	@Test
	void createCommit_draftPlan_succeeds() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(commitRepo.countByPlanId(planId)).thenReturn(0L);

		WeeklyCommit result = service.createCommit(planId, req(ChessPiece.ROOK, null), actorId);
		assertThat(result.getChessPiece()).isEqualTo(ChessPiece.ROOK);
		assertThat(result.getOwnerUserId()).isEqualTo(actorId);
	}

	@Test
	void createCommit_usesPlanOwnerNotRequestActor() {
		WeeklyPlan plan = draftPlan();
		UUID differentActorId = UUID.randomUUID();
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(commitRepo.countByPlanId(planId)).thenReturn(0L);

		WeeklyCommit result = service.createCommit(planId, req(ChessPiece.ROOK, null), differentActorId);
		assertThat(result.getOwnerUserId()).isEqualTo(plan.getOwnerUserId());
	}

	@Test
	void createCommit_lockedPlan_throws() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(lockedPlan()));

		assertThatThrownBy(() -> service.createCommit(planId, req(ChessPiece.ROOK, null), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("DRAFT");
	}

	// -------------------------------------------------------------------------
	// createCommit — chess piece limits
	// -------------------------------------------------------------------------

	@Test
	void createCommit_firstKing_succeeds() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(commitRepo.countByPlanId(planId)).thenReturn(0L);

		WeeklyCommit result = service.createCommit(planId, req(ChessPiece.KING, "Must succeed"), actorId);
		assertThat(result.getChessPiece()).isEqualTo(ChessPiece.KING);
	}

	@Test
	void createCommit_secondKing_throws() {
		WeeklyCommit existingKing = commit(ChessPiece.KING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(existingKing));

		assertThatThrownBy(() -> service.createCommit(planId, req(ChessPiece.KING, "Another king"), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("King");
	}

	@Test
	void createCommit_twoQueens_secondQueenSucceeds() {
		WeeklyCommit q1 = commit(ChessPiece.QUEEN);
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(q1));
		when(commitRepo.countByPlanId(planId)).thenReturn(1L);

		WeeklyCommit result = service.createCommit(planId, req(ChessPiece.QUEEN, "Q2 criteria"), actorId);
		assertThat(result.getChessPiece()).isEqualTo(ChessPiece.QUEEN);
	}

	@Test
	void createCommit_thirdQueen_throws() {
		WeeklyCommit q1 = commit(ChessPiece.QUEEN);
		WeeklyCommit q2 = commit(ChessPiece.QUEEN);
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(q1, q2));

		assertThatThrownBy(() -> service.createCommit(planId, req(ChessPiece.QUEEN, "Q3 criteria"), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Queen");
	}

	// -------------------------------------------------------------------------
	// createCommit — estimate validation
	// -------------------------------------------------------------------------

	@Test
	void createCommit_validEstimatePoints_succeeds() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(commitRepo.countByPlanId(planId)).thenReturn(0L);

		CreateCommitRequest req = new CreateCommitRequest("Task", ChessPiece.ROOK, null, null, null, 5, null);
		WeeklyCommit result = service.createCommit(planId, req, actorId);
		assertThat(result.getEstimatePoints()).isEqualTo(5);
	}

	@Test
	void createCommit_invalidEstimatePoints_throws() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		CreateCommitRequest req = new CreateCommitRequest("Task", ChessPiece.ROOK, null, null, null, 4, null);
		assertThatThrownBy(() -> service.createCommit(planId, req, actorId)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("Estimate points");
	}

	// -------------------------------------------------------------------------
	// createCommit — success criteria
	// -------------------------------------------------------------------------

	@Test
	void createCommit_kingWithoutSuccessCriteria_throws() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		assertThatThrownBy(() -> service.createCommit(planId, req(ChessPiece.KING, null), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Success criteria");
	}

	@Test
	void createCommit_queenWithoutSuccessCriteria_throws() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		assertThatThrownBy(() -> service.createCommit(planId, req(ChessPiece.QUEEN, null), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Success criteria");
	}

	@Test
	void createCommit_rookWithoutSuccessCriteria_succeeds() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(commitRepo.countByPlanId(planId)).thenReturn(0L);

		WeeklyCommit result = service.createCommit(planId, req(ChessPiece.ROOK, null), actorId);
		assertThat(result.getChessPiece()).isEqualTo(ChessPiece.ROOK);
	}

	// -------------------------------------------------------------------------
	// createCommit — RCDO linkage
	// -------------------------------------------------------------------------

	@Test
	void createCommit_invalidRcdo_throws() {
		UUID rcdoId = UUID.randomUUID();
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		doThrow(new RcdoValidationException("Not a valid target")).when(rcdoLinkageValidator)
				.validateCommitLinkage(rcdoId);

		CreateCommitRequest req = new CreateCommitRequest("Task", ChessPiece.ROOK, null, rcdoId, null, null, null);
		assertThatThrownBy(() -> service.createCommit(planId, req, actorId))
				.isInstanceOf(RcdoValidationException.class);
	}

	@Test
	void createCommit_noRcdo_doesNotValidateLinkage() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());
		when(commitRepo.countByPlanId(planId)).thenReturn(0L);

		service.createCommit(planId, req(ChessPiece.ROOK, null), actorId);
		verify(rcdoLinkageValidator, never()).validateCommitLinkage(any());
	}

	// -------------------------------------------------------------------------
	// updateCommit
	// -------------------------------------------------------------------------

	@Test
	void updateCommit_draftPlan_changesTitle_and_auditsUpdate() {
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(c.getId())).thenReturn(Optional.of(c));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));

		UpdateCommitRequest req = new UpdateCommitRequest("New Title", null, null, null, null, null, null);
		WeeklyCommit result = service.updateCommit(planId, c.getId(), req, actorId);
		assertThat(result.getTitle()).isEqualTo("New Title");
		verify(auditLogService).record(eq(AuditLogService.COMMIT_UPDATED), eq(actorId), eq("IC"),
				eq(AuditLogService.TARGET_COMMIT), eq(c.getId()), any(), any());
	}

	@Test
	void updateCommit_rejectsCommitFromDifferentPlan() {
		UUID otherPlanId = UUID.randomUUID();
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(c.getId())).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.updateCommit(otherPlanId, c.getId(),
				new UpdateCommitRequest("New Title", null, null, null, null, null, null), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("does not belong to plan");
	}

	@Test
	void updateCommit_lockedPlan_throws() {
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(c.getId())).thenReturn(Optional.of(c));
		when(planRepo.findById(planId)).thenReturn(Optional.of(lockedPlan()));

		assertThatThrownBy(() -> service.updateCommit(planId, c.getId(),
				new UpdateCommitRequest("T", null, null, null, null, null, null), actorId))
				.isInstanceOf(PlanValidationException.class);
	}

	@Test
	void updateCommit_changingChessToKing_revalidatesLimits() {
		WeeklyCommit existingKing = commit(ChessPiece.KING);
		WeeklyCommit rookToChange = commit(ChessPiece.ROOK);
		rookToChange.setId(UUID.randomUUID());

		when(commitRepo.findById(rookToChange.getId())).thenReturn(Optional.of(rookToChange));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(existingKing, rookToChange));

		UpdateCommitRequest req = new UpdateCommitRequest(null, ChessPiece.KING, null, null, null, null, "Criteria");
		assertThatThrownBy(() -> service.updateCommit(planId, rookToChange.getId(), req, actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("King");
	}

	// -------------------------------------------------------------------------
	// deleteCommit
	// -------------------------------------------------------------------------

	@Test
	void deleteCommit_draftPlan_removesCommit() {
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(c.getId())).thenReturn(Optional.of(c));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		service.deleteCommit(planId, c.getId(), actorId);
		verify(commitRepo).delete(c);
	}

	@Test
	void deleteCommit_rejectsCommitFromDifferentPlan() {
		UUID otherPlanId = UUID.randomUUID();
		WeeklyCommit c = commit(ChessPiece.ROOK);
		when(commitRepo.findById(c.getId())).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.deleteCommit(otherPlanId, c.getId(), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("does not belong to plan");
	}

	@Test
	void deleteCommit_renumbersRemaining() {
		WeeklyCommit c1 = commit(ChessPiece.ROOK);
		c1.setPriorityOrder(1);
		WeeklyCommit c2 = commit(ChessPiece.BISHOP);
		c2.setPriorityOrder(2);
		WeeklyCommit c3 = commit(ChessPiece.PAWN);
		c3.setPriorityOrder(3);

		// Deleting c2; remaining = [c1, c3]
		when(commitRepo.findById(c2.getId())).thenReturn(Optional.of(c2));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1, c3));

		service.deleteCommit(planId, c2.getId(), actorId);

		assertThat(c1.getPriorityOrder()).isEqualTo(1);
		assertThat(c3.getPriorityOrder()).isEqualTo(2);
	}

	// -------------------------------------------------------------------------
	// reorderCommits
	// -------------------------------------------------------------------------

	@Test
	void reorderCommits_validOrder_appliesNewOrder() {
		WeeklyCommit c1 = commit(ChessPiece.ROOK);
		c1.setPriorityOrder(1);
		WeeklyCommit c2 = commit(ChessPiece.BISHOP);
		c2.setPriorityOrder(2);

		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(new ArrayList<>(List.of(c1, c2)));

		// Reverse order: c2 first
		service.reorderCommits(planId, List.of(c2.getId(), c1.getId()), actorId);

		assertThat(c2.getPriorityOrder()).isEqualTo(1);
		assertThat(c1.getPriorityOrder()).isEqualTo(2);
	}

	@Test
	void reorderCommits_missingCommitId_throws() {
		WeeklyCommit c1 = commit(ChessPiece.ROOK);
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c1));

		// Providing a random UUID not in the plan
		assertThatThrownBy(() -> service.reorderCommits(planId, List.of(c1.getId(), UUID.randomUUID()), actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Reorder list");
	}

	// -------------------------------------------------------------------------
	// Direct validation helper tests
	// -------------------------------------------------------------------------

	@Test
	void validateEstimatePoints_nullValue_passes() {
		service.validateEstimatePoints(null); // no exception
	}

	@Test
	void validateSuccessCriteria_bishopWithoutCriteria_passes() {
		service.validateSuccessCriteria(ChessPiece.BISHOP, null); // no exception
	}
}
