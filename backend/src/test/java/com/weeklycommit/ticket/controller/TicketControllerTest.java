package com.weeklycommit.ticket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.ticket.dto.CreateTicketFromCommitRequest;
import com.weeklycommit.ticket.dto.CreateTicketRequest;
import com.weeklycommit.ticket.dto.LinkedCommitEntry;
import com.weeklycommit.ticket.dto.PagedTicketResponse;
import com.weeklycommit.ticket.dto.TicketDetailResponse;
import com.weeklycommit.ticket.dto.TicketListParams;
import com.weeklycommit.ticket.dto.TicketStatusHistoryResponse;
import com.weeklycommit.ticket.dto.TicketSummaryResponse;
import com.weeklycommit.ticket.dto.UpdateTicketRequest;
import com.weeklycommit.ticket.dto.UpdateTicketStatusRequest;
import com.weeklycommit.ticket.service.LinkTicketService;
import com.weeklycommit.ticket.service.TicketService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketController.class)
class TicketControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private TicketService ticketService;

	@MockBean
	private LinkTicketService linkTicketService;

	private TicketSummaryResponse summary() {
		return new TicketSummaryResponse(UUID.randomUUID(), "T-1", "Alpha", TicketStatus.TODO, TicketPriority.MEDIUM,
				UUID.randomUUID(), "Alice Smith", UUID.randomUUID(), "Engineering", UUID.randomUUID(), 3,
				LocalDate.of(2026, 3, 23), Instant.now(), Instant.now());
	}

	private TicketDetailResponse detail() {
		UUID ticketId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		return new TicketDetailResponse(ticketId, "T-1", "Alpha", "Desc", TicketStatus.IN_PROGRESS, TicketPriority.HIGH,
				UUID.randomUUID(), "Bob Jones", UUID.randomUUID(), "Carol Chen", UUID.randomUUID(), "Engineering",
				UUID.randomUUID(), 3, LocalDate.of(2026, 3, 23),
				List.of(new TicketStatusHistoryResponse(UUID.randomUUID(), ticketId, TicketStatus.TODO,
						TicketStatus.IN_PROGRESS, UUID.randomUUID(), Instant.now(), null)),
				List.of(new LinkedCommitEntry(UUID.randomUUID(), planId, "Commit", ChessPiece.ROOK, 3,
						LocalDate.of(2026, 3, 23), CommitOutcome.ACHIEVED)),
				Instant.now(), Instant.now());
	}

	@Test
	void listTickets_bindsFiltersAndReturnsPagedResponse() throws Exception {
		when(ticketService.listTickets(any(TicketListParams.class)))
				.thenReturn(new PagedTicketResponse(List.of(summary()), 1, 2, 10));

		mockMvc.perform(
				get("/api/tickets").param("status", "TODO").param("assigneeUserId", UUID.randomUUID().toString())
						.param("teamId", UUID.randomUUID().toString()).param("priority", "HIGH").param("page", "2")
						.param("pageSize", "10").param("sortBy", "priority").param("sortDir", "asc"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].key").value("T-1"))
				.andExpect(jsonPath("$.page").value(2));
	}

	@Test
	void getTicket_returnsEnrichedDetail() throws Exception {
		when(ticketService.getTicketDetail(any())).thenReturn(detail());

		mockMvc.perform(get("/api/tickets/" + UUID.randomUUID())).andExpect(status().isOk())
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.statusHistory[0].toStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.linkedCommits[0].commitTitle").value("Commit"));
	}

	@Test
	void listTicketSummaries_returnsCompactList() throws Exception {
		when(ticketService.listTicketSummaries(any())).thenReturn(List.of(summary()));

		mockMvc.perform(get("/api/tickets/summaries").param("teamId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Alpha"));
	}

	@Test
	void createTicket_returnsCreatedDetail() throws Exception {
		CreateTicketRequest request = new CreateTicketRequest(UUID.randomUUID(), "Alpha", "Desc", TicketStatus.TODO,
				TicketPriority.MEDIUM, null, UUID.randomUUID(), 3, null, null);
		when(ticketService.createTicket(any())).thenReturn(detail());

		mockMvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Alpha"));
	}

	@Test
	void createTicketFromCommit_returnsCreatedDetail() throws Exception {
		CreateTicketFromCommitRequest request = new CreateTicketFromCommitRequest(UUID.randomUUID(), UUID.randomUUID(),
				null, null, null, null, null, null, null, null, null, null);
		when(ticketService.createTicketFromCommit(any())).thenReturn(detail());

		mockMvc.perform(post("/api/tickets/from-commit").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.linkedCommits[0].commitTitle").value("Commit"));
	}

	@Test
	void updateTicket_usesActorHeader() throws Exception {
		UpdateTicketRequest request = new UpdateTicketRequest("Updated", null, TicketStatus.IN_PROGRESS,
				TicketPriority.CRITICAL, null, null, null, null, null);
		when(ticketService.updateTicket(any(), any(), any())).thenReturn(detail());

		mockMvc.perform(put("/api/tickets/" + UUID.randomUUID()).header("X-Actor-User-Id", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("HIGH"));
	}

	@Test
	void updateStatus_returnsUpdatedDetail() throws Exception {
		UUID ticketId = UUID.randomUUID();
		UpdateTicketStatusRequest request = new UpdateTicketStatusRequest(TicketStatus.DONE, UUID.randomUUID());
		when(ticketService.updateStatus(eq(ticketId), eq(TicketStatus.DONE), any())).thenReturn(detail());

		mockMvc.perform(put("/api/tickets/" + ticketId + "/status").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isOk())
				.andExpect(jsonPath("$.statusHistory").isArray());
	}
}
