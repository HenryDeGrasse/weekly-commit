package com.weeklycommit.ticket.service;

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
import com.weeklycommit.ticket.dto.UpdateTicketRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD and status-workflow service for native tickets (work items).
 *
 * <p>
 * Status transitions are enforced via a static transition map. Every status
 * change is appended to {@code work_item_status_history} for duration-in-status
 * reporting.
 */
@Service
@Transactional
public class TicketService {

	/** Valid Fibonacci-scale estimate values (mirrors CommitService). */
	private static final Set<Integer> VALID_ESTIMATE_POINTS = Set.of(1, 2, 3, 5, 8);

	/** Key prefix used when auto-generating ticket keys. */
	private static final String KEY_PREFIX = "T-";

	/**
	 * Legal status transitions.
	 *
	 * <pre>
	 * BACKLOG     → READY, IN_PROGRESS, CANCELED
	 * READY       → IN_PROGRESS, BACKLOG, CANCELED
	 * IN_PROGRESS → DONE, BLOCKED, READY, CANCELED
	 * BLOCKED     → IN_PROGRESS, CANCELED
	 * DONE        → (terminal)
	 * CANCELED    → (terminal)
	 * </pre>
	 */
	private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(TicketStatus.BACKLOG,
			EnumSet.of(TicketStatus.READY, TicketStatus.IN_PROGRESS, TicketStatus.CANCELED), TicketStatus.READY,
			EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.BACKLOG, TicketStatus.CANCELED), TicketStatus.IN_PROGRESS,
			EnumSet.of(TicketStatus.DONE, TicketStatus.BLOCKED, TicketStatus.READY, TicketStatus.CANCELED),
			TicketStatus.BLOCKED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELED), TicketStatus.DONE,
			EnumSet.noneOf(TicketStatus.class), TicketStatus.CANCELED, EnumSet.noneOf(TicketStatus.class));

	private final WorkItemRepository workItemRepo;
	private final WorkItemStatusHistoryRepository historyRepo;

	public TicketService(WorkItemRepository workItemRepo, WorkItemStatusHistoryRepository historyRepo) {
		this.workItemRepo = workItemRepo;
		this.historyRepo = historyRepo;
	}

	// -------------------------------------------------------------------------
	// Create
	// -------------------------------------------------------------------------

	/**
	 * Creates a new ticket with an auto-generated key of the form {@code T-N}.
	 *
	 * @param req
	 *            creation request
	 * @return saved ticket
	 */
	public TicketResponse createTicket(CreateTicketRequest req) {
		validateEstimatePoints(req.estimatePoints());

		WorkItem item = new WorkItem();
		item.setTeamId(req.teamId());
		item.setKey(generateKey(req.teamId()));
		item.setTitle(req.title());
		item.setDescription(req.description());
		item.setStatus(TicketStatus.BACKLOG);
		item.setAssigneeUserId(req.assigneeUserId());
		item.setReporterUserId(req.reporterUserId());
		item.setEstimatePoints(req.estimatePoints());
		item.setRcdoNodeId(req.rcdoNodeId());
		item.setTargetWeekStartDate(req.targetWeekStartDate());

		return TicketResponse.from(workItemRepo.save(item));
	}

	// -------------------------------------------------------------------------
	// Read
	// -------------------------------------------------------------------------

	/**
	 * Fetches a single ticket by its ID.
	 *
	 * @param id
	 *            ticket identifier
	 * @return ticket view
	 * @throws ResourceNotFoundException
	 *             if not found
	 */
	@Transactional(readOnly = true)
	public TicketResponse getTicket(UUID id) {
		return TicketResponse.from(requireTicket(id));
	}

	/**
	 * Lists all tickets for a team.
	 *
	 * @param teamId
	 *            team identifier
	 * @return list of ticket views
	 */
	@Transactional(readOnly = true)
	public List<TicketResponse> listTicketsByTeam(UUID teamId) {
		return workItemRepo.findByTeamId(teamId).stream().map(TicketResponse::from).toList();
	}

	// -------------------------------------------------------------------------
	// Update
	// -------------------------------------------------------------------------

	/**
	 * Partially updates a ticket. {@code null} fields in the request are ignored.
	 *
	 * @param id
	 *            ticket identifier
	 * @param req
	 *            partial update
	 * @return updated ticket view
	 */
	public TicketResponse updateTicket(UUID id, UpdateTicketRequest req) {
		WorkItem item = requireTicket(id);

		if (req.title() != null && !req.title().isBlank()) {
			item.setTitle(req.title());
		}
		if (req.description() != null) {
			item.setDescription(req.description());
		}
		if (req.assigneeUserId() != null) {
			item.setAssigneeUserId(req.assigneeUserId());
		}
		if (req.estimatePoints() != null) {
			validateEstimatePoints(req.estimatePoints());
			item.setEstimatePoints(req.estimatePoints());
		}
		if (req.rcdoNodeId() != null) {
			item.setRcdoNodeId(req.rcdoNodeId());
		}
		if (req.targetWeekStartDate() != null) {
			item.setTargetWeekStartDate(req.targetWeekStartDate());
		}

		return TicketResponse.from(workItemRepo.save(item));
	}

	// -------------------------------------------------------------------------
	// Update status
	// -------------------------------------------------------------------------

	/**
	 * Transitions the ticket to a new status, validating the transition and
	 * recording history.
	 *
	 * @param id
	 *            ticket identifier
	 * @param newStatus
	 *            target status
	 * @param changedByUserId
	 *            user performing the transition
	 * @return updated ticket view
	 * @throws PlanValidationException
	 *             if the transition is not allowed
	 */
	public TicketResponse updateStatus(UUID id, TicketStatus newStatus, UUID changedByUserId) {
		WorkItem item = requireTicket(id);
		TicketStatus currentStatus = item.getStatus();

		if (currentStatus == newStatus) {
			return TicketResponse.from(item); // idempotent — no change
		}

		Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(TicketStatus.class));
		if (!allowed.contains(newStatus)) {
			throw new PlanValidationException(
					"Illegal ticket status transition: " + currentStatus + " → " + newStatus + ". Allowed: " + allowed);
		}

		// Record history before updating the entity
		WorkItemStatusHistory history = new WorkItemStatusHistory();
		history.setWorkItemId(id);
		history.setFromStatus(currentStatus.name());
		history.setToStatus(newStatus.name());
		history.setChangedByUserId(changedByUserId);
		historyRepo.save(history);

		item.setStatus(newStatus);
		return TicketResponse.from(workItemRepo.save(item));
	}

	// -------------------------------------------------------------------------
	// Status history
	// -------------------------------------------------------------------------

	/**
	 * Returns the full status-change history for a ticket, ordered chronologically.
	 *
	 * @param id
	 *            ticket identifier
	 * @return ordered list of status history entries
	 */
	@Transactional(readOnly = true)
	public List<TicketStatusHistoryResponse> getStatusHistory(UUID id) {
		requireTicket(id); // ensure ticket exists
		return historyRepo.findByWorkItemIdOrderByCreatedAtAsc(id).stream().map(TicketStatusHistoryResponse::from)
				.toList();
	}

	// -------------------------------------------------------------------------
	// Delete
	// -------------------------------------------------------------------------

	/**
	 * Hard-deletes a ticket.
	 *
	 * @param id
	 *            ticket identifier
	 * @throws ResourceNotFoundException
	 *             if not found
	 */
	public void deleteTicket(UUID id) {
		WorkItem item = requireTicket(id);
		workItemRepo.delete(item);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	WorkItem requireTicket(UUID id) {
		return workItemRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
	}

	/**
	 * Generates a unique key for a ticket within its team. Uses a simple sequential
	 * counter based on the total number of tickets in the team.
	 */
	private String generateKey(UUID teamId) {
		long count = workItemRepo.countByTeamId(teamId);
		return KEY_PREFIX + (count + 1);
	}

	static void validateEstimatePoints(Integer points) {
		if (points != null && !VALID_ESTIMATE_POINTS.contains(points)) {
			throw new PlanValidationException("Estimate points must be one of {1, 2, 3, 5, 8}, got: " + points);
		}
	}
}
