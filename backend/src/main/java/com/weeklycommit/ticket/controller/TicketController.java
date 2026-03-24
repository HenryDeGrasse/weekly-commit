package com.weeklycommit.ticket.controller;

import com.weeklycommit.ticket.dto.CreateTicketFromCommitRequest;
import com.weeklycommit.ticket.dto.CreateTicketRequest;
import com.weeklycommit.ticket.dto.LinkTicketRequest;
import com.weeklycommit.ticket.dto.LinkTicketResponse;
import com.weeklycommit.ticket.dto.PagedTicketResponse;
import com.weeklycommit.ticket.dto.TicketDetailResponse;
import com.weeklycommit.ticket.dto.TicketListParams;
import com.weeklycommit.ticket.dto.TicketStatusHistoryResponse;
import com.weeklycommit.ticket.dto.TicketSummaryResponse;
import com.weeklycommit.ticket.dto.UpdateTicketRequest;
import com.weeklycommit.ticket.dto.UpdateTicketStatusRequest;
import com.weeklycommit.ticket.service.LinkTicketService;
import com.weeklycommit.ticket.service.TicketService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for native ticket (work item) management.
 */
@RestController
public class TicketController {

	private final TicketService ticketService;
	private final LinkTicketService linkTicketService;

	public TicketController(TicketService ticketService, LinkTicketService linkTicketService) {
		this.ticketService = ticketService;
		this.linkTicketService = linkTicketService;
	}

	// -------------------------------------------------------------------------
	// Ticket CRUD
	// -------------------------------------------------------------------------

	/** List tickets with optional filters, pagination, and sorting. */
	@GetMapping("/api/tickets")
	public ResponseEntity<PagedTicketResponse> listTickets(
			@RequestParam(required = false) com.weeklycommit.domain.enums.TicketStatus status,
			@RequestParam(required = false) UUID assigneeUserId, @RequestParam(required = false) UUID teamId,
			@RequestParam(required = false) UUID rcdoNodeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetWeek,
			@RequestParam(required = false) com.weeklycommit.domain.enums.TicketPriority priority,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "updatedAt") String sortBy,
			@RequestParam(defaultValue = "desc") String sortDir) {
		TicketListParams params = new TicketListParams(status, assigneeUserId, teamId, rcdoNodeId, targetWeek, priority,
				page, pageSize, sortBy, sortDir);
		return ResponseEntity.ok(ticketService.listTickets(params));
	}

	/** Return compact ticket summaries for quick-select dropdowns. */
	@GetMapping("/api/tickets/summaries")
	public ResponseEntity<List<TicketSummaryResponse>> listTicketSummaries(
			@RequestParam(required = false) UUID teamId) {
		return ResponseEntity.ok(ticketService.listTicketSummaries(teamId));
	}

	/** Create a new ticket. */
	@PostMapping("/api/tickets")
	public ResponseEntity<TicketDetailResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request));
	}

	/** Create a new ticket pre-populated from a commit. */
	@PostMapping("/api/tickets/from-commit")
	public ResponseEntity<TicketDetailResponse> createTicketFromCommit(
			@Valid @RequestBody CreateTicketFromCommitRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicketFromCommit(request));
	}

	/** Get a single ticket detail view. */
	@GetMapping("/api/tickets/{id}")
	public ResponseEntity<TicketDetailResponse> getTicket(@PathVariable UUID id) {
		return ResponseEntity.ok(ticketService.getTicketDetail(id));
	}

	/** Partially update a ticket. */
	@PutMapping("/api/tickets/{id}")
	public ResponseEntity<TicketDetailResponse> updateTicket(@PathVariable UUID id,
			@RequestBody UpdateTicketRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		return ResponseEntity.ok(ticketService.updateTicket(id, request, actorUserId));
	}

	/** Delete a ticket. */
	@DeleteMapping("/api/tickets/{id}")
	public ResponseEntity<Void> deleteTicket(@PathVariable UUID id) {
		ticketService.deleteTicket(id);
		return ResponseEntity.noContent().build();
	}

	// -------------------------------------------------------------------------
	// Status transition
	// -------------------------------------------------------------------------

	/** Transition a ticket's status. */
	@PutMapping("/api/tickets/{id}/status")
	public ResponseEntity<TicketDetailResponse> updateStatus(@PathVariable UUID id,
			@Valid @RequestBody UpdateTicketStatusRequest request) {
		return ResponseEntity.ok(ticketService.updateStatus(id, request.status(), request.changedByUserId()));
	}

	// -------------------------------------------------------------------------
	// Status history
	// -------------------------------------------------------------------------

	/** Retrieve the full status-change history for a ticket. */
	@GetMapping("/api/tickets/{id}/history")
	public ResponseEntity<List<TicketStatusHistoryResponse>> getStatusHistory(@PathVariable UUID id) {
		return ResponseEntity.ok(ticketService.getStatusHistory(id));
	}

	// -------------------------------------------------------------------------
	// Link ticket to commit
	// -------------------------------------------------------------------------

	/**
	 * Links a ticket to a commit. Enforces RCDO defaulting and duplicate-link
	 * prevention.
	 */
	@PutMapping("/api/plans/{planId}/commits/{commitId}/link-ticket")
	public ResponseEntity<LinkTicketResponse> linkTicket(@PathVariable UUID planId, @PathVariable UUID commitId,
			@Valid @RequestBody LinkTicketRequest request) {
		return ResponseEntity.ok(linkTicketService.linkTicket(planId, commitId, request.workItemId()));
	}
}
