package com.weeklycommit.ticket.controller;

import com.weeklycommit.ticket.dto.CreateTicketRequest;
import com.weeklycommit.ticket.dto.LinkTicketRequest;
import com.weeklycommit.ticket.dto.LinkTicketResponse;
import com.weeklycommit.ticket.dto.TicketResponse;
import com.weeklycommit.ticket.dto.TicketStatusHistoryResponse;
import com.weeklycommit.ticket.dto.UpdateTicketRequest;
import com.weeklycommit.ticket.dto.UpdateTicketStatusRequest;
import com.weeklycommit.ticket.service.LinkTicketService;
import com.weeklycommit.ticket.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for native ticket (work item) management.
 *
 * <ul>
 * <li>{@code GET  /api/tickets?teamId=…} — list tickets for a team</li>
 * <li>{@code POST /api/tickets} — create a ticket</li>
 * <li>{@code GET /api/tickets/{id}} — get a ticket</li>
 * <li>{@code PUT /api/tickets/{id}} — update a ticket</li>
 * <li>{@code DELETE /api/tickets/{id}} — delete a ticket</li>
 * <li>{@code PUT /api/tickets/{id}/status} — transition ticket status</li>
 * <li>{@code GET /api/tickets/{id}/history} — status history</li>
 * <li>{@code PUT /api/plans/{planId}/commits/{commitId}/link-ticket} — link
 * ticket to commit</li>
 * </ul>
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

	/** List all tickets for a team. */
	@GetMapping("/api/tickets")
	public ResponseEntity<List<TicketResponse>> listTickets(@RequestParam UUID teamId) {
		return ResponseEntity.ok(ticketService.listTicketsByTeam(teamId));
	}

	/** Create a new ticket. */
	@PostMapping("/api/tickets")
	public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request));
	}

	/** Get a single ticket. */
	@GetMapping("/api/tickets/{id}")
	public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id) {
		return ResponseEntity.ok(ticketService.getTicket(id));
	}

	/** Partially update a ticket. */
	@PutMapping("/api/tickets/{id}")
	public ResponseEntity<TicketResponse> updateTicket(@PathVariable UUID id,
			@RequestBody UpdateTicketRequest request) {
		return ResponseEntity.ok(ticketService.updateTicket(id, request));
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
	public ResponseEntity<TicketResponse> updateStatus(@PathVariable UUID id,
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
