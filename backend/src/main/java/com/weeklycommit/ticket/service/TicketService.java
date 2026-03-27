package com.weeklycommit.ticket.service;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.entity.WorkItemStatusHistory;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.NotificationEvent;
import com.weeklycommit.domain.enums.TicketPriority;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.domain.repository.WorkItemStatusHistoryRepository;
import com.weeklycommit.notification.service.NotificationService;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.ticket.dto.CreateTicketFromCommitRequest;
import com.weeklycommit.ticket.dto.CreateTicketRequest;
import com.weeklycommit.ticket.dto.LinkedCommitEntry;
import com.weeklycommit.ticket.dto.PagedTicketResponse;
import com.weeklycommit.ticket.dto.TicketDetailResponse;
import com.weeklycommit.ticket.dto.TicketListParams;
import com.weeklycommit.ticket.dto.TicketResponse;
import com.weeklycommit.ticket.dto.TicketStatusHistoryResponse;
import com.weeklycommit.ticket.dto.TicketSummaryResponse;
import com.weeklycommit.ticket.dto.UpdateTicketRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD and status-workflow service for native tickets (work items).
 */
@Service
@Transactional
public class TicketService {

	private static final Logger log = LoggerFactory.getLogger(TicketService.class);

	/** Valid Fibonacci-scale estimate values (mirrors CommitService). */
	private static final Set<Integer> VALID_ESTIMATE_POINTS = Set.of(1, 2, 3, 5, 8);

	/** Key prefix used when auto-generating ticket keys. */
	private static final String KEY_PREFIX = "T-";

	/** Legal status transitions. */
	private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(TicketStatus.TODO,
			EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELED), TicketStatus.IN_PROGRESS,
			EnumSet.of(TicketStatus.DONE, TicketStatus.BLOCKED, TicketStatus.CANCELED, TicketStatus.TODO),
			TicketStatus.BLOCKED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELED, TicketStatus.TODO),
			TicketStatus.DONE, EnumSet.noneOf(TicketStatus.class), TicketStatus.CANCELED,
			EnumSet.of(TicketStatus.TODO));

	private final WorkItemRepository workItemRepo;
	private final WorkItemStatusHistoryRepository historyRepo;
	private final WeeklyCommitRepository commitRepo;
	private final WeeklyPlanRepository planRepo;
	private final NotificationService notificationService;
	private final com.weeklycommit.domain.repository.UserAccountRepository userRepo;
	private final com.weeklycommit.domain.repository.TeamRepository teamRepo;

	/** Optional — injected when the RAG module is active; null-safe throughout. */
	@Autowired(required = false)
	private com.weeklycommit.ai.rag.SemanticIndexService semanticIndexService;

	public TicketService(WorkItemRepository workItemRepo, WorkItemStatusHistoryRepository historyRepo,
			WeeklyCommitRepository commitRepo, WeeklyPlanRepository planRepo, NotificationService notificationService,
			com.weeklycommit.domain.repository.UserAccountRepository userRepo,
			com.weeklycommit.domain.repository.TeamRepository teamRepo) {
		this.workItemRepo = workItemRepo;
		this.historyRepo = historyRepo;
		this.commitRepo = commitRepo;
		this.planRepo = planRepo;
		this.notificationService = notificationService;
		this.userRepo = userRepo;
		this.teamRepo = teamRepo;
	}

	// -------------------------------------------------------------------------
	// Create
	// -------------------------------------------------------------------------

	public TicketDetailResponse createTicket(CreateTicketRequest req) {
		validateEstimatePoints(req.estimatePoints());

		WorkItem item = new WorkItem();
		item.setTeamId(req.teamId());
		item.setKey(generateKey(req.teamId()));
		item.setTitle(req.title());
		item.setDescription(req.description());
		item.setStatus(req.status() != null ? req.status() : TicketStatus.TODO);
		item.setPriority(req.priority() != null ? req.priority() : TicketPriority.MEDIUM);
		item.setAssigneeUserId(req.assigneeUserId());
		item.setReporterUserId(req.reporterUserId());
		item.setEstimatePoints(req.estimatePoints());
		item.setRcdoNodeId(req.rcdoNodeId());
		item.setTargetWeekStartDate(req.targetWeekStartDate());

		WorkItem saved = workItemRepo.save(item);
		if (semanticIndexService != null) {
			semanticIndexService.indexEntity(com.weeklycommit.ai.rag.SemanticIndexService.TYPE_TICKET, saved.getId());
		}

		// Notify: unassigned ticket created (MEDIUM — same-day digest)
		if (saved.getAssigneeUserId() == null && saved.getTargetWeekStartDate() != null) {
			try {
				notificationService.findManagerForTeam(saved.getTeamId())
						.ifPresent(managerId -> notificationService.createNotification(managerId,
								com.weeklycommit.domain.enums.NotificationEvent.UNASSIGNED_TICKET_CREATED,
								"Unassigned ticket: " + saved.getKey(),
								"Ticket " + saved.getKey() + " \"" + saved.getTitle()
										+ "\" was created without an assignee and is targeted to week "
										+ saved.getTargetWeekStartDate() + ".",
								saved.getId(), "TICKET"));
			} catch (Exception ex) {
				// Non-critical
			}
		}

		return getTicketDetail(saved.getId());
	}

	public TicketDetailResponse createTicketFromCommit(CreateTicketFromCommitRequest req) {
		WeeklyPlan plan = requirePlan(req.planId());
		WeeklyCommit commit = requireCommit(req.commitId());
		if (!commit.getPlanId().equals(plan.getId())) {
			throw new PlanValidationException("Commit " + req.commitId() + " does not belong to plan " + req.planId());
		}

		WorkItem item = new WorkItem();
		UUID teamId = req.teamId() != null ? req.teamId() : plan.getTeamId();
		item.setTeamId(teamId);
		item.setKey(generateKey(teamId));
		item.setTitle(req.title() != null && !req.title().isBlank() ? req.title() : commit.getTitle());
		item.setDescription(req.description() != null ? req.description() : commit.getDescription());
		item.setStatus(req.status() != null ? req.status() : TicketStatus.TODO);
		item.setPriority(req.priority() != null ? req.priority() : TicketPriority.MEDIUM);
		item.setAssigneeUserId(req.assigneeUserId());
		item.setReporterUserId(req.reporterUserId() != null ? req.reporterUserId() : commit.getOwnerUserId());
		validateEstimatePoints(req.estimatePoints() != null ? req.estimatePoints() : commit.getEstimatePoints());
		item.setEstimatePoints(req.estimatePoints() != null ? req.estimatePoints() : commit.getEstimatePoints());
		item.setRcdoNodeId(req.rcdoNodeId() != null ? req.rcdoNodeId() : commit.getRcdoNodeId());
		item.setTargetWeekStartDate(
				req.targetWeekStartDate() != null ? req.targetWeekStartDate() : plan.getWeekStartDate());

		WorkItem saved = workItemRepo.save(item);
		if (semanticIndexService != null) {
			semanticIndexService.indexEntity(com.weeklycommit.ai.rag.SemanticIndexService.TYPE_TICKET, saved.getId());
		}
		return getTicketDetail(saved.getId());
	}

	// -------------------------------------------------------------------------
	// Read
	// -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public TicketDetailResponse getTicketDetail(UUID id) {
		WorkItem ticket = requireTicket(id);
		TicketResponse base = TicketResponse.from(ticket);

		List<TicketStatusHistoryResponse> statusHistory = historyRepo.findByWorkItemIdOrderByCreatedAtAsc(id).stream()
				.map(TicketStatusHistoryResponse::from).toList();

		List<LinkedCommitEntry> linkedCommits = commitRepo.findByWorkItemId(id).stream().map(commit -> {
			WeeklyPlan plan = planRepo.findById(commit.getPlanId()).orElse(null);
			if (plan == null) {
				return null;
			}
			return new LinkedCommitEntry(commit.getId(), commit.getPlanId(), commit.getTitle(), commit.getChessPiece(),
					commit.getEstimatePoints(), plan.getWeekStartDate(), commit.getOutcome());
		}).filter(java.util.Objects::nonNull).toList();

		// Resolve display names
		String assigneeName = ticket.getAssigneeUserId() != null
				? userRepo.findById(ticket.getAssigneeUserId())
						.map(com.weeklycommit.domain.entity.UserAccount::getDisplayName).orElse(null)
				: null;
		String reporterName = ticket.getReporterUserId() != null
				? userRepo.findById(ticket.getReporterUserId())
						.map(com.weeklycommit.domain.entity.UserAccount::getDisplayName).orElse(null)
				: null;
		String teamName = ticket.getTeamId() != null
				? teamRepo.findById(ticket.getTeamId()).map(com.weeklycommit.domain.entity.Team::getName).orElse(null)
				: null;

		return TicketDetailResponse.from(base, statusHistory, linkedCommits, assigneeName, reporterName, teamName);
	}

	@Transactional(readOnly = true)
	public PagedTicketResponse listTickets(TicketListParams params) {
		Specification<WorkItem> spec = buildSpecification(params);
		Pageable pageable = PageRequest.of(params.normalizedPage() - 1, params.normalizedPageSize(),
				buildSort(params.normalizedSortBy(), params.normalizedSortDir()));
		Page<WorkItem> page = workItemRepo.findAll(spec, pageable);
		return new PagedTicketResponse(page.getContent().stream().map(this::toSummaryWithNames).toList(),
				page.getTotalElements(), params.normalizedPage(), params.normalizedPageSize());
	}

	@Transactional(readOnly = true)
	public List<TicketSummaryResponse> listTicketSummaries(UUID teamId) {
		Specification<WorkItem> spec = (root, query,
				cb) -> teamId == null ? cb.conjunction() : cb.equal(root.get("teamId"), teamId);
		return workItemRepo.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
				.map(this::toSummaryWithNames).toList();
	}

	// -------------------------------------------------------------------------
	// Update
	// -------------------------------------------------------------------------

	public TicketDetailResponse updateTicket(UUID id, UpdateTicketRequest req, UUID actorUserId) {
		WorkItem item = requireTicket(id);

		if (req.title() != null && !req.title().isBlank()) {
			item.setTitle(req.title());
		}
		if (req.description() != null) {
			item.setDescription(req.description());
		}
		if (req.priority() != null) {
			item.setPriority(req.priority());
		}
		if (req.assigneeUserId() != null) {
			item.setAssigneeUserId(req.assigneeUserId());
		}
		if (req.teamId() != null) {
			item.setTeamId(req.teamId());
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
		if (req.status() != null) {
			transitionStatus(item, req.status(), actorUserId != null ? actorUserId : item.getReporterUserId());
		}

		workItemRepo.save(item);
		if (semanticIndexService != null) {
			semanticIndexService.indexEntity(com.weeklycommit.ai.rag.SemanticIndexService.TYPE_TICKET, item.getId());
		}
		return getTicketDetail(item.getId());
	}

	// -------------------------------------------------------------------------
	// Update status
	// -------------------------------------------------------------------------

	public TicketDetailResponse updateStatus(UUID id, TicketStatus newStatus, UUID changedByUserId) {
		WorkItem item = requireTicket(id);
		transitionStatus(item, newStatus, changedByUserId);
		workItemRepo.save(item);
		if (semanticIndexService != null) {
			semanticIndexService.indexEntity(com.weeklycommit.ai.rag.SemanticIndexService.TYPE_TICKET, item.getId());
		}

		// Notify commit owner(s) when a ticket linked to a King or Queen commit is
		// blocked
		if (newStatus == TicketStatus.BLOCKED) {
			try {
				notifyCriticalTicketBlocked(item);
			} catch (Exception ex) {
				log.warn("Failed to send CRITICAL_TICKET_BLOCKED notification for ticket {}: {}", id, ex.getMessage());
			}
		}

		return getTicketDetail(item.getId());
	}

	/**
	 * Sends a CRITICAL_TICKET_BLOCKED notification to the owner of any King or
	 * Queen commit linked to this ticket.
	 */
	private void notifyCriticalTicketBlocked(WorkItem ticket) {
		List<WeeklyCommit> linkedCommits = commitRepo.findByWorkItemId(ticket.getId());
		for (WeeklyCommit commit : linkedCommits) {
			if (commit.getChessPiece() == ChessPiece.KING || commit.getChessPiece() == ChessPiece.QUEEN) {
				notificationService.createNotification(commit.getOwnerUserId(),
						NotificationEvent.CRITICAL_TICKET_BLOCKED, "Blocked ticket linked to " + commit.getChessPiece(),
						"Ticket \"" + ticket.getTitle() + "\" is now BLOCKED and is linked to your "
								+ commit.getChessPiece() + " commit \"" + commit.getTitle() + "\".",
						ticket.getId(), "TICKET");
			}
		}
	}

	// -------------------------------------------------------------------------
	// Status history
	// -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<TicketStatusHistoryResponse> getStatusHistory(UUID id) {
		requireTicket(id);
		return historyRepo.findByWorkItemIdOrderByCreatedAtAsc(id).stream().map(TicketStatusHistoryResponse::from)
				.toList();
	}

	// -------------------------------------------------------------------------
	// Delete
	// -------------------------------------------------------------------------

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

	private WeeklyCommit requireCommit(UUID id) {
		return commitRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + id));
	}

	private WeeklyPlan requirePlan(UUID id) {
		return planRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + id));
	}

	/**
	 * Converts a WorkItem to a TicketSummaryResponse with resolved display names
	 * for assignee and team.
	 */
	private TicketSummaryResponse toSummaryWithNames(WorkItem item) {
		String assigneeName = item.getAssigneeUserId() != null
				? userRepo.findById(item.getAssigneeUserId())
						.map(com.weeklycommit.domain.entity.UserAccount::getDisplayName).orElse(null)
				: null;
		String teamName = item.getTeamId() != null
				? teamRepo.findById(item.getTeamId()).map(com.weeklycommit.domain.entity.Team::getName).orElse(null)
				: null;
		return TicketSummaryResponse.from(item, assigneeName, teamName);
	}

	private void transitionStatus(WorkItem item, TicketStatus newStatus, UUID changedByUserId) {
		TicketStatus currentStatus = item.getStatus();
		if (currentStatus == newStatus) {
			return;
		}

		Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(TicketStatus.class));
		if (!allowed.contains(newStatus)) {
			throw new PlanValidationException(
					"Illegal ticket status transition: " + currentStatus + " → " + newStatus + ". Allowed: " + allowed);
		}

		WorkItemStatusHistory history = new WorkItemStatusHistory();
		history.setWorkItemId(item.getId());
		history.setFromStatus(currentStatus != null ? currentStatus.name() : null);
		history.setToStatus(newStatus.name());
		history.setChangedByUserId(changedByUserId);
		historyRepo.save(history);

		item.setStatus(newStatus);
	}

	/**
	 * Generates a unique key for a ticket within its team.
	 */
	private String generateKey(UUID teamId) {
		long count = workItemRepo.countByTeamId(teamId);
		return KEY_PREFIX + (count + 1);
	}

	private Specification<WorkItem> buildSpecification(TicketListParams params) {
		return Specification.where(equalsUuid("assigneeUserId", params.assigneeUserId()))
				.and(equalsUuid("teamId", params.teamId())).and(equalsUuid("rcdoNodeId", params.rcdoNodeId()))
				.and(equalsEnum("status", params.status())).and(equalsEnum("priority", params.priority()))
				.and(equalsValue("targetWeekStartDate", params.targetWeek()));
	}

	private static <T> Specification<WorkItem> equalsValue(String field, T value) {
		return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(field), value);
	}

	private static Specification<WorkItem> equalsUuid(String field, UUID value) {
		return equalsValue(field, value);
	}

	private static <T extends Enum<T>> Specification<WorkItem> equalsEnum(String field, T value) {
		return equalsValue(field, value);
	}

	private Sort buildSort(String sortBy, String sortDir) {
		Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
		String property = switch (sortBy) {
			case "key" -> "key";
			case "title" -> "title";
			case "status" -> "status";
			case "priority" -> "priority";
			case "updatedAt" -> "updatedAt";
			default -> "updatedAt";
		};
		return Sort.by(direction, property);
	}

	static void validateEstimatePoints(Integer points) {
		if (points != null && !VALID_ESTIMATE_POINTS.contains(points)) {
			throw new PlanValidationException("Estimate points must be one of {1, 2, 3, 5, 8}, got: " + points);
		}
	}
}
