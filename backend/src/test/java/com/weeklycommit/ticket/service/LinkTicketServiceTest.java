package com.weeklycommit.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.ticket.dto.LinkTicketResponse;
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
class LinkTicketServiceTest {

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@InjectMocks
	private LinkTicketService service;

	private final UUID planId = UUID.randomUUID();
	private final UUID commitId = UUID.randomUUID();
	private final UUID workItemId = UUID.randomUUID();
	private final UUID ownerId = UUID.randomUUID();
	private final LocalDate weekStart = LocalDate.of(2025, 6, 2);

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan draftPlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(ownerId);
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(weekStart);
		p.setState(PlanState.DRAFT);
		p.setLockDeadline(Instant.now().plusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7 * 24 * 3600));
		return p;
	}

	private WeeklyCommit commit(UUID rcdoNodeId) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(commitId);
		c.setPlanId(planId);
		c.setOwnerUserId(ownerId);
		c.setTitle("Test commit");
		c.setChessPiece(ChessPiece.ROOK);
		c.setPriorityOrder(1);
		c.setRcdoNodeId(rcdoNodeId);
		return c;
	}

	private WorkItem ticket(UUID rcdoNodeId) {
		WorkItem w = new WorkItem();
		w.setId(workItemId);
		w.setTeamId(UUID.randomUUID());
		w.setKey("T-1");
		w.setTitle("Sample ticket");
		w.setStatus(TicketStatus.TODO);
		w.setReporterUserId(UUID.randomUUID());
		w.setRcdoNodeId(rcdoNodeId);
		return w;
	}

	@BeforeEach
	void stubSave() {
		lenient().when(commitRepo.save(any(WeeklyCommit.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	// =========================================================================
	// linkTicket — happy path
	// =========================================================================

	@Test
	void linkTicket_noRcdo_linksSuccessfully() {
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(commit(null)));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(ticket(null)));
		when(commitRepo.findActiveCommitsForTicketByOwnerExcluding(workItemId, ownerId, commitId))
				.thenReturn(List.of());

		LinkTicketResponse resp = service.linkTicket(planId, commitId, workItemId);

		assertThat(resp.commit().workItemId()).isEqualTo(workItemId);
		assertThat(resp.rcdoWarning()).isNull();
	}

	// =========================================================================
	// linkTicket — RCDO defaulting
	// =========================================================================

	@Test
	void linkTicket_commitHasNoRcdo_ticketHasRcdo_defaultsFromTicket() {
		UUID ticketRcdo = UUID.randomUUID();
		WeeklyCommit c = commit(null); // no RCDO on commit
		WorkItem t = ticket(ticketRcdo);

		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(t));
		when(commitRepo.findActiveCommitsForTicketByOwnerExcluding(workItemId, ownerId, commitId))
				.thenReturn(List.of());

		LinkTicketResponse resp = service.linkTicket(planId, commitId, workItemId);

		// Commit RCDO should have been set to the ticket's RCDO
		assertThat(resp.commit().rcdoNodeId()).isEqualTo(ticketRcdo);
		assertThat(resp.rcdoWarning()).isNull();
	}

	@Test
	void linkTicket_bothHaveSameRcdo_noWarning() {
		UUID sharedRcdo = UUID.randomUUID();
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(commit(sharedRcdo)));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(ticket(sharedRcdo)));
		when(commitRepo.findActiveCommitsForTicketByOwnerExcluding(workItemId, ownerId, commitId))
				.thenReturn(List.of());

		LinkTicketResponse resp = service.linkTicket(planId, commitId, workItemId);

		assertThat(resp.rcdoWarning()).isNull();
	}

	@Test
	void linkTicket_bothHaveDifferentRcdo_emitsWarningButLinks() {
		UUID commitRcdo = UUID.randomUUID();
		UUID ticketRcdo = UUID.randomUUID();
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(commit(commitRcdo)));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(ticket(ticketRcdo)));
		when(commitRepo.findActiveCommitsForTicketByOwnerExcluding(workItemId, ownerId, commitId))
				.thenReturn(List.of());

		LinkTicketResponse resp = service.linkTicket(planId, commitId, workItemId);

		// The link should be created
		assertThat(resp.commit().workItemId()).isEqualTo(workItemId);
		// But a warning should be returned
		assertThat(resp.rcdoWarning()).isNotNull().contains("differs from ticket RCDO");
		// Commit's original RCDO should be preserved (not overwritten)
		assertThat(resp.commit().rcdoNodeId()).isEqualTo(commitRcdo);
	}

	// =========================================================================
	// linkTicket — duplicate-link prevention
	// =========================================================================

	@Test
	void linkTicket_sameTicketAlreadyLinkedByOwnerSameWeek_throwsValidation() {
		WeeklyCommit otherCommit = commit(null);
		otherCommit.setId(UUID.randomUUID());

		// Other commit (same week, same owner) is already linked to the same ticket
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(commit(null)));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.of(ticket(null)));
		when(commitRepo.findActiveCommitsForTicketByOwnerExcluding(workItemId, ownerId, commitId))
				.thenReturn(List.of(otherCommit));
		// The other commit belongs to the same week
		WeeklyPlan samePlan = draftPlan();
		samePlan.setId(otherCommit.getPlanId());
		when(planRepo.findById(otherCommit.getPlanId())).thenReturn(Optional.of(samePlan));

		assertThatThrownBy(() -> service.linkTicket(planId, commitId, workItemId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("already linked");
	}

	// =========================================================================
	// linkTicket — state guard
	// =========================================================================

	@Test
	void linkTicket_lockedPlan_throwsValidation() {
		WeeklyCommit c = commit(null);
		WeeklyPlan locked = draftPlan();
		locked.setState(PlanState.LOCKED);

		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));
		when(planRepo.findById(planId)).thenReturn(Optional.of(locked));

		assertThatThrownBy(() -> service.linkTicket(planId, commitId, workItemId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("DRAFT");
	}

	// =========================================================================
	// linkTicket — error cases
	// =========================================================================

	@Test
	void linkTicket_commitNotFound_throwsNotFound() {
		when(commitRepo.findById(commitId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.linkTicket(planId, commitId, workItemId))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void linkTicket_ticketNotFound_throwsNotFound() {
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(commit(null)));
		when(planRepo.findById(planId)).thenReturn(Optional.of(draftPlan()));
		when(workItemRepo.findById(workItemId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.linkTicket(planId, commitId, workItemId))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void linkTicket_commitFromDifferentPlan_throwsValidation() {
		WeeklyCommit c = commit(null);
		c.setPlanId(UUID.randomUUID()); // different plan — validation throws before planRepo is queried

		when(commitRepo.findById(commitId)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.linkTicket(planId, commitId, workItemId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("does not belong to plan");
	}
}
