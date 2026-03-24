package com.weeklycommit.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.ticket.dto.CreateTicketRequest;
import com.weeklycommit.ticket.dto.TicketResponse;
import com.weeklycommit.ticket.dto.TicketStatusHistoryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private WorkItemStatusHistoryRepository historyRepo;

	@InjectMocks
	private TicketService service;

	private final UUID ticketId = UUID.randomUUID();
	private final UUID teamId = UUID.randomUUID();
	private final UUID reporterId = UUID.randomUUID();
	private final UUID actorId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WorkItem ticket(TicketStatus status) {
		WorkItem w = new WorkItem();
		w.setId(ticketId);
		w.setTeamId(teamId);
		w.setKey("T-1");
		w.setTitle("Sample ticket");
		w.setStatus(status);
		w.setReporterUserId(reporterId);
		return w;
	}

	private WorkItemStatusHistory historyEntry(String from, String to) {
		WorkItemStatusHistory h = new WorkItemStatusHistory();
		h.setId(UUID.randomUUID());
		h.setWorkItemId(ticketId);
		h.setFromStatus(from);
		h.setToStatus(to);
		h.setChangedByUserId(actorId);
		return h;
	}

	@BeforeEach
	void stubSave() {
		lenient().when(workItemRepo.save(any(WorkItem.class))).thenAnswer(inv -> {
			WorkItem w = inv.getArgument(0);
			if (w.getId() == null) {
				w.setId(UUID.randomUUID());
			}
			return w;
		});
		lenient().when(historyRepo.save(any(WorkItemStatusHistory.class))).thenAnswer(inv -> {
			WorkItemStatusHistory h = inv.getArgument(0);
			if (h.getId() == null) {
				h.setId(UUID.randomUUID());
			}
			return h;
		});
	}

	// =========================================================================
	// createTicket
	// =========================================================================

	@Test
	void createTicket_setsBacklogStatusAndGeneratesKey() {
		when(workItemRepo.countByTeamId(teamId)).thenReturn(0L);

		CreateTicketRequest req = new CreateTicketRequest(teamId, "New task", null, null, reporterId, 3, null, null);
		TicketResponse resp = service.createTicket(req);

		assertThat(resp.status()).isEqualTo(TicketStatus.BACKLOG);

		ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
		verify(workItemRepo).save(captor.capture());
		WorkItem saved = captor.getValue();
		assertThat(saved.getKey()).isEqualTo("T-1"); // count=0 → T-1
		assertThat(saved.getEstimatePoints()).isEqualTo(3);
	}

	@Test
	void createTicket_invalidEstimatePoints_throwsValidation() {
		CreateTicketRequest req = new CreateTicketRequest(teamId, "Bad estimate", null, null, reporterId, 7, null,
				null);

		assertThatThrownBy(() -> service.createTicket(req)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("Estimate points");
	}

	// =========================================================================
	// updateStatus — valid transitions
	// =========================================================================

	@Test
	void updateStatus_backlogToReady_succeeds() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.BACKLOG)));

		TicketResponse resp = service.updateStatus(ticketId, TicketStatus.READY, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.READY);
	}

	@Test
	void updateStatus_readyToInProgress_succeeds() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.READY)));

		TicketResponse resp = service.updateStatus(ticketId, TicketStatus.IN_PROGRESS, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.IN_PROGRESS);
	}

	@Test
	void updateStatus_inProgressToBlocked_succeeds() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));

		TicketResponse resp = service.updateStatus(ticketId, TicketStatus.BLOCKED, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.BLOCKED);
	}

	@Test
	void updateStatus_inProgressToDone_succeeds() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));

		TicketResponse resp = service.updateStatus(ticketId, TicketStatus.DONE, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.DONE);
	}

	@Test
	void updateStatus_blockedToInProgress_succeeds() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.BLOCKED)));

		TicketResponse resp = service.updateStatus(ticketId, TicketStatus.IN_PROGRESS, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.IN_PROGRESS);
	}

	// =========================================================================
	// updateStatus — invalid transitions
	// =========================================================================

	@Test
	void updateStatus_doneToAnyStatus_throwsValidation() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.DONE)));

		assertThatThrownBy(() -> service.updateStatus(ticketId, TicketStatus.BACKLOG, actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Illegal ticket status transition")
				.hasMessageContaining("DONE");
	}

	@Test
	void updateStatus_canceledToAnyStatus_throwsValidation() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.CANCELED)));

		assertThatThrownBy(() -> service.updateStatus(ticketId, TicketStatus.BACKLOG, actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Illegal ticket status transition")
				.hasMessageContaining("CANCELED");
	}

	@Test
	void updateStatus_backlogToBlocked_throwsValidation() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.BACKLOG)));

		assertThatThrownBy(() -> service.updateStatus(ticketId, TicketStatus.BLOCKED, actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Illegal ticket status transition");
	}

	@Test
	void updateStatus_sameStatus_returnsIdempotently() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));

		TicketResponse resp = service.updateStatus(ticketId, TicketStatus.IN_PROGRESS, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.IN_PROGRESS);
		// No history entry should be saved for a no-op transition
		verify(historyRepo, times(0)).save(any());
	}

	// =========================================================================
	// updateStatus — status history recording
	// =========================================================================

	@Test
	void updateStatus_recordsHistoryWithFromAndToStatus() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.BACKLOG)));

		service.updateStatus(ticketId, TicketStatus.READY, actorId);

		ArgumentCaptor<WorkItemStatusHistory> captor = ArgumentCaptor.forClass(WorkItemStatusHistory.class);
		verify(historyRepo).save(captor.capture());
		WorkItemStatusHistory h = captor.getValue();
		assertThat(h.getFromStatus()).isEqualTo("BACKLOG");
		assertThat(h.getToStatus()).isEqualTo("READY");
		assertThat(h.getChangedByUserId()).isEqualTo(actorId);
		assertThat(h.getWorkItemId()).isEqualTo(ticketId);
	}

	@Test
	void getStatusHistory_returnsOrderedEntries() {
		WorkItemStatusHistory h1 = historyEntry("BACKLOG", "READY");
		WorkItemStatusHistory h2 = historyEntry("READY", "IN_PROGRESS");
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));
		when(historyRepo.findByWorkItemIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of(h1, h2));

		List<TicketStatusHistoryResponse> history = service.getStatusHistory(ticketId);

		assertThat(history).hasSize(2);
		assertThat(history.get(0).fromStatus()).isEqualTo("BACKLOG");
		assertThat(history.get(0).toStatus()).isEqualTo("READY");
		assertThat(history.get(1).toStatus()).isEqualTo("IN_PROGRESS");
	}

	@Test
	void getStatusHistory_ticketNotFound_throwsNotFound() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getStatusHistory(ticketId)).isInstanceOf(ResourceNotFoundException.class);
	}

	// =========================================================================
	// deleteTicket
	// =========================================================================

	@Test
	void deleteTicket_callsRepositoryDelete() {
		WorkItem t = ticket(TicketStatus.BACKLOG);
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(t));

		service.deleteTicket(ticketId);

		verify(workItemRepo).delete(t);
	}

	@Test
	void deleteTicket_notFound_throwsNotFound() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteTicket(ticketId)).isInstanceOf(ResourceNotFoundException.class);
	}
}
