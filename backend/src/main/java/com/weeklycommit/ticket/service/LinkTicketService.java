package com.weeklycommit.ticket.service;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.ticket.dto.LinkTicketResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles linking a ticket (work item) to a commit with RCDO defaulting and
 * same-week duplicate-link prevention.
 *
 * <p>
 * RCDO defaulting rules:
 * <ol>
 * <li>If the commit has no RCDO and the ticket has one, the commit inherits the
 * ticket's RCDO.</li>
 * <li>If both have an RCDO and they differ, a warning is included in the
 * response (but the link is still created).</li>
 * <li>If the commit already has an RCDO and the ticket has none, no change is
 * made.</li>
 * </ol>
 */
@Service
@Transactional
public class LinkTicketService {

	private final WeeklyCommitRepository commitRepo;
	private final WeeklyPlanRepository planRepo;
	private final WorkItemRepository workItemRepo;

	public LinkTicketService(WeeklyCommitRepository commitRepo, WeeklyPlanRepository planRepo,
			WorkItemRepository workItemRepo) {
		this.commitRepo = commitRepo;
		this.planRepo = planRepo;
		this.workItemRepo = workItemRepo;
	}

	/**
	 * Links the given ticket to the given commit.
	 *
	 * <p>
	 * The plan must be in DRAFT state (post-lock ticket linking should go through
	 * scope-change flow). Prevents the same assignee from linking the same ticket
	 * to multiple active commits within the same week.
	 *
	 * @param planId
	 *            plan the commit belongs to
	 * @param commitId
	 *            commit to update
	 * @param workItemId
	 *            ticket to link
	 * @return updated commit plus any RCDO mismatch warning
	 */
	public LinkTicketResponse linkTicket(UUID planId, UUID commitId, UUID workItemId) {
		WeeklyCommit commit = requireCommit(commitId);
		validateCommitBelongsToPlan(planId, commit);
		requireDraftPlan(planId);

		WorkItem ticket = requireTicket(workItemId);

		// Duplicate-link prevention: same assignee, same ticket, this week, other
		// active commits
		List<WeeklyCommit> duplicates = commitRepo.findActiveCommitsForTicketByOwnerExcluding(workItemId,
				commit.getOwnerUserId(), commitId);

		// Filter to same-week duplicates
		WeeklyPlan currentPlan = requirePlan(planId);
		List<WeeklyCommit> sameWeekDuplicates = duplicates.stream().filter(c -> {
			WeeklyPlan p = planRepo.findById(c.getPlanId()).orElse(null);
			return p != null && currentPlan.getWeekStartDate().equals(p.getWeekStartDate());
		}).toList();

		if (!sameWeekDuplicates.isEmpty()) {
			throw new PlanValidationException(
					"Ticket " + workItemId + " is already linked to another active commit by the same assignee "
							+ "in week " + currentPlan.getWeekStartDate());
		}

		// RCDO defaulting and mismatch detection
		String rcdoWarning = null;
		UUID commitRcdo = commit.getRcdoNodeId();
		UUID ticketRcdo = ticket.getRcdoNodeId();

		if (commitRcdo == null && ticketRcdo != null) {
			// Default from ticket
			commit.setRcdoNodeId(ticketRcdo);
		} else if (commitRcdo != null && ticketRcdo != null && !commitRcdo.equals(ticketRcdo)) {
			// Mismatch warning — keep commit's existing RCDO
			rcdoWarning = "Commit RCDO (" + commitRcdo + ") differs from ticket RCDO (" + ticketRcdo
					+ "); commit RCDO was not changed";
		}

		commit.setWorkItemId(workItemId);
		WeeklyCommit saved = commitRepo.save(commit);

		return new LinkTicketResponse(CommitResponse.from(saved), rcdoWarning);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit requireCommit(UUID commitId) {
		return commitRepo.findById(commitId)
				.orElseThrow(() -> new ResourceNotFoundException("Commit not found: " + commitId));
	}

	private WeeklyPlan requirePlan(UUID planId) {
		return planRepo.findById(planId)
				.orElseThrow(() -> new ResourceNotFoundException("Weekly plan not found: " + planId));
	}

	private void requireDraftPlan(UUID planId) {
		WeeklyPlan plan = requirePlan(planId);
		if (plan.getState() != PlanState.DRAFT) {
			throw new PlanValidationException(
					"Ticket linking is only allowed on DRAFT plans; current state: " + plan.getState());
		}
	}

	private WorkItem requireTicket(UUID workItemId) {
		return workItemRepo.findById(workItemId)
				.orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + workItemId));
	}

	private void validateCommitBelongsToPlan(UUID planId, WeeklyCommit commit) {
		if (!commit.getPlanId().equals(planId)) {
			throw new PlanValidationException("Commit " + commit.getId() + " does not belong to plan " + planId);
		}
	}
}
