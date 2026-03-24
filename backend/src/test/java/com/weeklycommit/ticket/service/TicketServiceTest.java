package com.weeklycommit.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.ticket.dto.CreateTicketFromCommitRequest;
import com.weeklycommit.ticket.dto.CreateTicketRequest;
import com.weeklycommit.ticket.dto.PagedTicketResponse;
import com.weeklycommit.ticket.dto.TicketDetailResponse;
import com.weeklycommit.ticket.dto.TicketListParams;
import com.weeklycommit.ticket.dto.TicketStatusHistoryResponse;
import com.weeklycommit.ticket.dto.UpdateTicketRequest;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private WorkItemStatusHistoryRepository historyRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@InjectMocks
	private TicketService service;

	private final UUID ticketId = UUID.randomUUID();
	private final UUID teamId = UUID.randomUUID();
	private final UUID reporterId = UUID.randomUUID();
	private final UUID actorId = UUID.randomUUID();
	private final UUID commitId = UUID.randomUUID();
	private final UUID planId = UUID.randomUUID();
	private WorkItem persistedTicket;

	private WorkItem ticket(TicketStatus status, TicketPriority priority) {
		WorkItem w = new WorkItem();
		w.setId(ticketId);
		w.setTeamId(teamId);
		w.setKey("T-1");
		w.setTitle("Sample ticket");
		w.setDescription("Details");
		w.setStatus(status);
		w.setPriority(priority);
		w.setReporterUserId(reporterId);
		w.setEstimatePoints(3);
		w.setTargetWeekStartDate(LocalDate.of(2026, 3, 23));
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

	private WeeklyCommit linkedCommit() {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(commitId);
		c.setPlanId(planId);
		c.setOwnerUserId(reporterId);
		c.setTitle("Linked commit");
		c.setChessPiece(ChessPiece.ROOK);
		c.setEstimatePoints(5);
		c.setOutcome(CommitOutcome.ACHIEVED);
		return c;
	}

	private WeeklyPlan plan() {
		WeeklyPlan plan = new WeeklyPlan();
		plan.setId(planId);
		plan.setOwnerUserId(reporterId);
		plan.setTeamId(teamId);
		plan.setWeekStartDate(LocalDate.of(2026, 3, 23));
		plan.setState(PlanState.DRAFT);
		plan.setLockDeadline(Instant.now());
		plan.setReconcileDeadline(Instant.now());
		return plan;
	}

	@BeforeEach
	void stubSaves() {
		persistedTicket = ticket(TicketStatus.TODO, TicketPriority.MEDIUM);
		lenient().when(workItemRepo.save(any(WorkItem.class))).thenAnswer(inv -> {
			WorkItem w = inv.getArgument(0);
			if (w.getId() == null) {
				w.setId(ticketId);
			}
			persistedTicket = w;
			return w;
		});
		lenient().when(historyRepo.save(any(WorkItemStatusHistory.class))).thenAnswer(inv -> {
			WorkItemStatusHistory h = inv.getArgument(0);
			if (h.getId() == null) {
				h.setId(UUID.randomUUID());
			}
			return h;
		});
		lenient().when(workItemRepo.findById(ticketId)).thenAnswer(inv -> Optional.ofNullable(persistedTicket));
		lenient().when(historyRepo.findByWorkItemIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of());
		lenient().when(commitRepo.findByWorkItemId(ticketId)).thenReturn(List.of());
	}

	@Test
	void createTicket_defaultsToTodoAndPersistsPriority() {
		when(workItemRepo.countByTeamId(teamId)).thenReturn(0L);
		CreateTicketRequest req = new CreateTicketRequest(teamId, "New task", null, null, TicketPriority.HIGH, null,
				reporterId, 3, null, null);

		TicketDetailResponse resp = service.createTicket(req);

		assertThat(resp.status()).isEqualTo(TicketStatus.TODO);
		assertThat(resp.priority()).isEqualTo(TicketPriority.HIGH);

		ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
		verify(workItemRepo).save(captor.capture());
		assertThat(captor.getValue().getKey()).isEqualTo("T-1");
		assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.HIGH);
	}

	@Test
	void createTicket_invalidEstimatePoints_throwsValidation() {
		CreateTicketRequest req = new CreateTicketRequest(teamId, "Bad estimate", null, null, TicketPriority.MEDIUM,
				null, reporterId, 7, null, null);

		assertThatThrownBy(() -> service.createTicket(req)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("Estimate points");
	}

	@Test
	void updateTicket_updatesPriorityAndStatusTransition() {
		WorkItem existing = ticket(TicketStatus.TODO, TicketPriority.MEDIUM);
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(existing));
		UpdateTicketRequest req = new UpdateTicketRequest("Updated", "Desc", TicketStatus.IN_PROGRESS,
				TicketPriority.CRITICAL, null, null, null, null, null);

		TicketDetailResponse resp = service.updateTicket(ticketId, req, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.IN_PROGRESS);
		assertThat(resp.priority()).isEqualTo(TicketPriority.CRITICAL);
		verify(historyRepo).save(any(WorkItemStatusHistory.class));
	}

	@Test
	void updateStatus_validTransition_recordsHistory() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.TODO, TicketPriority.MEDIUM)));

		TicketDetailResponse resp = service.updateStatus(ticketId, TicketStatus.IN_PROGRESS, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.IN_PROGRESS);
		ArgumentCaptor<WorkItemStatusHistory> captor = ArgumentCaptor.forClass(WorkItemStatusHistory.class);
		verify(historyRepo).save(captor.capture());
		assertThat(captor.getValue().getFromStatus()).isEqualTo("TODO");
		assertThat(captor.getValue().getToStatus()).isEqualTo("IN_PROGRESS");
	}

	@Test
	void updateStatus_invalidTransition_throwsValidation() {
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(ticket(TicketStatus.DONE, TicketPriority.MEDIUM)));

		assertThatThrownBy(() -> service.updateStatus(ticketId, TicketStatus.TODO, actorId))
				.isInstanceOf(PlanValidationException.class).hasMessageContaining("Illegal ticket status transition");
	}

	@Test
	void updateStatus_sameStatus_isIdempotent() {
		when(workItemRepo.findById(ticketId))
				.thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM)));

		TicketDetailResponse resp = service.updateStatus(ticketId, TicketStatus.IN_PROGRESS, actorId);

		assertThat(resp.status()).isEqualTo(TicketStatus.IN_PROGRESS);
		verify(historyRepo, times(0)).save(any());
	}

	@Test
	void getTicketDetail_populatesStatusHistoryAndLinkedCommits() {
		WorkItem existing = ticket(TicketStatus.IN_PROGRESS, TicketPriority.HIGH);
		WeeklyCommit commit = linkedCommit();
		WeeklyPlan plan = plan();
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(existing));
		when(historyRepo.findByWorkItemIdOrderByCreatedAtAsc(ticketId))
				.thenReturn(List.of(historyEntry("TODO", "IN_PROGRESS")));
		when(commitRepo.findByWorkItemId(ticketId)).thenReturn(List.of(commit));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		TicketDetailResponse detail = service.getTicketDetail(ticketId);

		assertThat(detail.statusHistory()).hasSize(1);
		assertThat(detail.statusHistory().get(0).ticketId()).isEqualTo(ticketId);
		assertThat(detail.linkedCommits()).hasSize(1);
		assertThat(detail.linkedCommits().get(0).commitId()).isEqualTo(commitId);
		assertThat(detail.linkedCommits().get(0).weekStartDate()).isEqualTo(plan.getWeekStartDate());
	}

	@Test
	void getStatusHistory_mapsLegacyStatusesToTodo() {
		when(historyRepo.findByWorkItemIdOrderByCreatedAtAsc(ticketId))
				.thenReturn(List.of(historyEntry("BACKLOG", "READY")));

		List<TicketStatusHistoryResponse> history = service.getStatusHistory(ticketId);

		assertThat(history).hasSize(1);
		assertThat(history.get(0).fromStatus()).isEqualTo(TicketStatus.TODO);
		assertThat(history.get(0).toStatus()).isEqualTo(TicketStatus.TODO);
	}

	@Test
	void listTickets_returnsPagedResponseWithRequestedPage() {
		WorkItem first = ticket(TicketStatus.TODO, TicketPriority.MEDIUM);
		WorkItem second = ticket(TicketStatus.IN_PROGRESS, TicketPriority.HIGH);
		when(workItemRepo.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 5));

		PagedTicketResponse page = service.listTickets(new TicketListParams(TicketStatus.TODO, null, teamId, null, null,
				TicketPriority.MEDIUM, 2, 2, "priority", "asc"));

		assertThat(page.total()).isEqualTo(5);
		assertThat(page.page()).isEqualTo(2);
		assertThat(page.pageSize()).isEqualTo(2);
		assertThat(page.items()).hasSize(2);
	}

	@Test
	void listTicketSummaries_filtersByOptionalTeam() {
		when(workItemRepo.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
				.thenReturn(List.of(ticket(TicketStatus.TODO, TicketPriority.LOW)));

		assertThat(service.listTicketSummaries(teamId)).hasSize(1);
	}

	@Test
	void createTicketFromCommit_copiesFieldsAndAppliesOverrides() {
		WeeklyCommit commit = linkedCommit();
		commit.setDescription("Copied description");
		commit.setRcdoNodeId(UUID.randomUUID());
		WeeklyPlan plan = plan();
		when(commitRepo.findById(commitId)).thenReturn(Optional.of(commit));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(workItemRepo.countByTeamId(teamId)).thenReturn(0L);

		CreateTicketFromCommitRequest req = new CreateTicketFromCommitRequest(commitId, planId, null, null,
				TicketStatus.BLOCKED, TicketPriority.CRITICAL, UUID.randomUUID(), null, null, null, null, null);

		TicketDetailResponse created = service.createTicketFromCommit(req);

		assertThat(created.title()).isEqualTo("Linked commit");
		assertThat(created.status()).isEqualTo(TicketStatus.BLOCKED);
		assertThat(created.priority()).isEqualTo(TicketPriority.CRITICAL);
		assertThat(created.teamId()).isEqualTo(teamId);
	}

	@Test
	void createTicketFromCommit_missingCommit_throwsNotFound() {
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan()));
		when(commitRepo.findById(commitId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.createTicketFromCommit(new CreateTicketFromCommitRequest(commitId, planId,
				null, null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Commit not found");
	}

	@Test
	void deleteTicket_callsRepositoryDelete() {
		WorkItem existing = ticket(TicketStatus.TODO, TicketPriority.MEDIUM);
		when(workItemRepo.findById(ticketId)).thenReturn(Optional.of(existing));

		service.deleteTicket(ticketId);

		verify(workItemRepo).delete(existing);
	}
}
