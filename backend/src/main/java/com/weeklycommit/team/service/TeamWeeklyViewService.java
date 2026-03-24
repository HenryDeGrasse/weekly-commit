package com.weeklycommit.team.service;

import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.dto.ChessDistributionEntry;
import com.weeklycommit.team.dto.MemberComplianceSummary;
import com.weeklycommit.team.dto.MemberWeekView;
import com.weeklycommit.team.dto.PeerCommitView;
import com.weeklycommit.team.dto.PeerMemberWeekView;
import com.weeklycommit.team.dto.RcdoRollupEntry;
import com.weeklycommit.team.dto.TeamWeekViewResponse;
import com.weeklycommit.team.dto.UncommittedTicketSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates weekly commitment data across all team members for a given week.
 *
 * <p>
 * Privacy filtering is applied based on the caller's role:
 * <ul>
 * <li>ADMIN / direct MANAGER: full {@link MemberWeekView} for each member.</li>
 * <li>IC (peer): {@link PeerMemberWeekView} with sensitive fields
 * stripped.</li>
 * </ul>
 *
 * <p>
 * De-duplication rule: work items that are linked to any commit in the team's
 * plans for this week are excluded from the uncommitted sections.
 */
@Service
@Transactional(readOnly = true)
public class TeamWeeklyViewService {

	private final TeamRepository teamRepo;
	private final TeamMembershipRepository membershipRepo;
	private final UserAccountRepository userRepo;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final WorkItemRepository workItemRepo;
	private final AuthorizationService authService;

	public TeamWeeklyViewService(TeamRepository teamRepo, TeamMembershipRepository membershipRepo,
			UserAccountRepository userRepo, WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			WorkItemRepository workItemRepo, AuthorizationService authService) {
		this.teamRepo = teamRepo;
		this.membershipRepo = membershipRepo;
		this.userRepo = userRepo;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.workItemRepo = workItemRepo;
		this.authService = authService;
	}

	// -------------------------------------------------------------------------
	// Main entry point
	// -------------------------------------------------------------------------

	/**
	 * Returns the aggregated team weekly view for the requested week.
	 *
	 * @param teamId
	 *            the team to aggregate
	 * @param weekStart
	 *            the Monday of the target week
	 * @param callerId
	 *            the user making the request (used for privacy filtering)
	 */
	public TeamWeekViewResponse getTeamWeekView(UUID teamId, LocalDate weekStart, UUID callerId) {
		Team team = teamRepo.findById(teamId)
				.orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

		// Authorisation check: caller must be a team member
		authService.checkCanAccessTeam(callerId, teamId);

		UserRole callerRole = authService.getCallerRole(callerId);
		boolean callerIsManager = (callerRole == UserRole.ADMIN) || (callerRole == UserRole.MANAGER);

		// 1. Collect all team members
		List<TeamMembership> memberships = membershipRepo.findByTeamId(teamId);
		List<UUID> memberIds = memberships.stream().map(TeamMembership::getUserId).toList();

		// 2. Load plans for this team + week
		List<WeeklyPlan> plans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart);
		Map<UUID, WeeklyPlan> planByOwnerId = new HashMap<>();
		for (WeeklyPlan plan : plans) {
			planByOwnerId.put(plan.getOwnerUserId(), plan);
		}

		// 3. Load all commits for these plans; collect linked work-item IDs
		Set<UUID> linkedWorkItemIds = new HashSet<>();
		Map<UUID, List<WeeklyCommit>> commitsByPlanId = new HashMap<>();
		for (WeeklyPlan plan : plans) {
			List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
			commitsByPlanId.put(plan.getId(), commits);
			for (WeeklyCommit c : commits) {
				if (c.getWorkItemId() != null) {
					linkedWorkItemIds.add(c.getWorkItemId());
				}
			}
		}

		// 4. Build member views (full detail vs peer view based on caller relationship)
		List<MemberWeekView> memberViews = new ArrayList<>();
		List<PeerMemberWeekView> peerViews = new ArrayList<>();
		List<MemberComplianceSummary> complianceSummary = new ArrayList<>();

		Instant now = Instant.now();

		for (UUID memberId : memberIds) {
			UserAccount member = userRepo.findById(memberId).orElse(null);
			if (member == null)
				continue;

			WeeklyPlan plan = planByOwnerId.get(memberId);
			List<WeeklyCommit> commits = plan != null
					? commitsByPlanId.getOrDefault(plan.getId(), List.of())
					: List.of();

			int totalPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
					.sum();

			// Determine if caller can see full detail for this member
			boolean seesFullDetail = callerIsManager
					? authService.canAccessFullDetail(callerId, memberId)
					: callerId.equals(memberId);

			if (seesFullDetail) {
				memberViews.add(new MemberWeekView(memberId, member.getDisplayName(),
						plan != null ? plan.getId() : null, plan != null ? plan.getState() : null,
						plan != null ? plan.getCapacityBudgetPoints() : member.getWeeklyCapacityPoints(), totalPoints,
						commits.stream().map(CommitResponse::from).toList()));
			} else if (authService.arePeers(callerId, memberId)) {
				// Peer view: only current + next week basic detail
				peerViews.add(new PeerMemberWeekView(memberId, member.getDisplayName(),
						plan != null ? plan.getId() : null, plan != null ? plan.getState() : null,
						commits.stream().map(PeerCommitView::from).toList()));
			}

			// Compliance summary (always included for MANAGER/ADMIN)
			if (callerIsManager) {
				boolean lockCompliant = plan != null && plan.isCompliant() && plan.getState() != PlanState.DRAFT;
				boolean autoLocked = plan != null && plan.isSystemLockedWithErrors();
				boolean missedLock = plan == null
						|| (plan.getState() == PlanState.DRAFT && now.isAfter(plan.getLockDeadline()));
				boolean reconcileCompliant = plan != null
						&& (plan.getState() == PlanState.RECONCILED || now.isBefore(plan.getReconcileDeadline()));

				complianceSummary.add(
						new MemberComplianceSummary(memberId, member.getDisplayName(), lockCompliant && !missedLock,
								reconcileCompliant, autoLocked, plan != null ? plan.getState() : null, plan != null));
			}
		}

		// 5. Uncommitted assigned tickets (assigned to a member, not linked to any
		// commit)
		List<UncommittedTicketSummary> uncommittedAssigned = new ArrayList<>();
		for (UUID memberId : memberIds) {
			List<WorkItem> assignedItems = workItemRepo.findByAssigneeUserId(memberId);
			for (WorkItem wi : assignedItems) {
				if (!linkedWorkItemIds.contains(wi.getId()) && wi.getStatus() != TicketStatus.DONE
						&& wi.getStatus() != TicketStatus.CANCELED) {
					uncommittedAssigned.add(UncommittedTicketSummary.from(wi));
				}
			}
		}

		// 6. Uncommitted unassigned tickets targeted to this week
		List<UncommittedTicketSummary> uncommittedUnassigned = workItemRepo
				.findByTeamIdAndTargetWeekStartDate(teamId, weekStart).stream()
				.filter(wi -> wi.getAssigneeUserId() == null).filter(wi -> !linkedWorkItemIds.contains(wi.getId()))
				.filter(wi -> wi.getStatus() != TicketStatus.DONE && wi.getStatus() != TicketStatus.CANCELED)
				.map(UncommittedTicketSummary::from).toList();

		// 7. RCDO rollup (aggregate points + commit count by RCDO node)
		List<RcdoRollupEntry> rcdoRollup = buildRcdoRollup(commitsByPlanId);

		// 8. Chess distribution (count + points per chess piece)
		List<ChessDistributionEntry> chessDistribution = buildChessDistribution(commitsByPlanId);

		return new TeamWeekViewResponse(teamId, team.getName(), weekStart, callerIsManager ? memberViews : List.of(),
				!callerIsManager ? peerViews : List.of(), callerIsManager ? uncommittedAssigned : List.of(),
				callerIsManager ? uncommittedUnassigned : List.of(), rcdoRollup, chessDistribution,
				callerIsManager ? complianceSummary : List.of());
	}

	// -------------------------------------------------------------------------
	// RCDO rollup
	// -------------------------------------------------------------------------

	private List<RcdoRollupEntry> buildRcdoRollup(Map<UUID, List<WeeklyCommit>> commitsByPlanId) {
		// Aggregate by RCDO node: points and commit count
		Map<UUID, int[]> rollup = new LinkedHashMap<>(); // [commitCount, totalPoints]

		for (List<WeeklyCommit> commits : commitsByPlanId.values()) {
			for (WeeklyCommit c : commits) {
				if (c.getRcdoNodeId() == null)
					continue;
				int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
				rollup.compute(c.getRcdoNodeId(), (k, v) -> {
					if (v == null)
						return new int[]{1, pts};
					v[0]++;
					v[1] += pts;
					return v;
				});
			}
		}

		return rollup.entrySet().stream().map(e -> new RcdoRollupEntry(e.getKey(), e.getValue()[0], e.getValue()[1]))
				.toList();
	}

	// -------------------------------------------------------------------------
	// Chess distribution
	// -------------------------------------------------------------------------

	private List<ChessDistributionEntry> buildChessDistribution(Map<UUID, List<WeeklyCommit>> commitsByPlanId) {
		Map<ChessPiece, int[]> dist = new EnumMap<>(ChessPiece.class); // [count, points]

		for (List<WeeklyCommit> commits : commitsByPlanId.values()) {
			for (WeeklyCommit c : commits) {
				if (c.getChessPiece() == null)
					continue;
				int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
				dist.compute(c.getChessPiece(), (k, v) -> {
					if (v == null)
						return new int[]{1, pts};
					v[0]++;
					v[1] += pts;
					return v;
				});
			}
		}

		return dist.entrySet().stream()
				.map(e -> new ChessDistributionEntry(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
	}
}
