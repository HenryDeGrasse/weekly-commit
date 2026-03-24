package com.weeklycommit.team.service;

import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.entity.WorkItem;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.TicketStatus;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
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
import com.weeklycommit.team.dto.TeamHistoryResponse;
import com.weeklycommit.team.dto.TeamWeekHistoryEntry;
import com.weeklycommit.team.dto.TeamWeekViewResponse;
import com.weeklycommit.team.dto.UncommittedTicketSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates weekly commitment data across all team members for a given week.
 */
@Service
@Transactional(readOnly = true)
public class TeamWeeklyViewService {

	private static final int DEFAULT_HISTORY_WEEKS = 12;

	private final TeamRepository teamRepo;
	private final TeamMembershipRepository membershipRepo;
	private final UserAccountRepository userRepo;
	private final WeeklyPlanRepository planRepo;
	private final WeeklyCommitRepository commitRepo;
	private final WorkItemRepository workItemRepo;
	private final ManagerReviewExceptionRepository exceptionRepo;
	private final AuthorizationService authService;

	public TeamWeeklyViewService(TeamRepository teamRepo, TeamMembershipRepository membershipRepo,
			UserAccountRepository userRepo, WeeklyPlanRepository planRepo, WeeklyCommitRepository commitRepo,
			WorkItemRepository workItemRepo, ManagerReviewExceptionRepository exceptionRepo,
			AuthorizationService authService) {
		this.teamRepo = teamRepo;
		this.membershipRepo = membershipRepo;
		this.userRepo = userRepo;
		this.planRepo = planRepo;
		this.commitRepo = commitRepo;
		this.workItemRepo = workItemRepo;
		this.exceptionRepo = exceptionRepo;
		this.authService = authService;
	}

	// -------------------------------------------------------------------------
	// Main entry point
	// -------------------------------------------------------------------------

	public TeamWeekViewResponse getTeamWeekView(UUID teamId, LocalDate weekStart, UUID callerId) {
		Team team = teamRepo.findById(teamId)
				.orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

		authService.checkCanAccessTeam(callerId, teamId);

		UserRole callerRole = authService.getCallerRole(callerId);
		boolean callerIsManager = callerRole == UserRole.ADMIN || callerRole == UserRole.MANAGER;

		List<TeamMembership> memberships = membershipRepo.findByTeamId(teamId);
		List<UUID> memberIds = memberships.stream().map(TeamMembership::getUserId).toList();

		List<WeeklyPlan> plans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart);
		Map<UUID, WeeklyPlan> planByOwnerId = new HashMap<>();
		for (WeeklyPlan plan : plans) {
			planByOwnerId.put(plan.getOwnerUserId(), plan);
		}

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

		List<MemberWeekView> memberViews = new ArrayList<>();
		List<PeerMemberWeekView> peerViews = new ArrayList<>();
		List<MemberComplianceSummary> complianceSummary = new ArrayList<>();
		Instant now = Instant.now();

		for (UUID memberId : memberIds) {
			UserAccount member = userRepo.findById(memberId).orElse(null);
			if (member == null) {
				continue;
			}

			WeeklyPlan plan = planByOwnerId.get(memberId);
			List<WeeklyCommit> commits = plan != null
					? commitsByPlanId.getOrDefault(plan.getId(), List.of())
					: List.of();
			int totalPoints = commits.stream().mapToInt(c -> c.getEstimatePoints() != null ? c.getEstimatePoints() : 0)
					.sum();

			boolean seesFullDetail = callerIsManager
					? authService.canAccessFullDetail(callerId, memberId)
					: callerId.equals(memberId);

			if (seesFullDetail) {
				memberViews.add(new MemberWeekView(memberId, member.getDisplayName(),
						plan != null ? plan.getId() : null, plan != null ? plan.getState() : null,
						plan != null ? plan.getCapacityBudgetPoints() : member.getWeeklyCapacityPoints(), totalPoints,
						commits.stream().map(CommitResponse::from).toList()));
			} else if (authService.arePeers(callerId, memberId)) {
				peerViews.add(new PeerMemberWeekView(memberId, member.getDisplayName(),
						plan != null ? plan.getId() : null, plan != null ? plan.getState() : null,
						commits.stream().map(PeerCommitView::from).toList()));
			}

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

		List<UncommittedTicketSummary> uncommittedUnassigned = workItemRepo
				.findByTeamIdAndTargetWeekStartDate(teamId, weekStart).stream()
				.filter(wi -> wi.getAssigneeUserId() == null).filter(wi -> !linkedWorkItemIds.contains(wi.getId()))
				.filter(wi -> wi.getStatus() != TicketStatus.DONE && wi.getStatus() != TicketStatus.CANCELED)
				.map(UncommittedTicketSummary::from).toList();

		List<RcdoRollupEntry> rcdoRollup = buildRcdoRollup(commitsByPlanId);
		List<ChessDistributionEntry> chessDistribution = buildChessDistribution(commitsByPlanId);

		return new TeamWeekViewResponse(teamId, team.getName(), weekStart, callerIsManager ? memberViews : List.of(),
				!callerIsManager ? peerViews : List.of(), callerIsManager ? uncommittedAssigned : List.of(),
				callerIsManager ? uncommittedUnassigned : List.of(), rcdoRollup, chessDistribution,
				callerIsManager ? complianceSummary : List.of());
	}

	// -------------------------------------------------------------------------
	// Team history
	// -------------------------------------------------------------------------

	public TeamHistoryResponse getTeamHistory(UUID teamId, UUID callerId) {
		teamRepo.findById(teamId).orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
		authService.checkCanAccessTeam(callerId, teamId);

		List<TeamMembership> memberships = membershipRepo.findByTeamId(teamId);
		int memberCount = memberships.size();
		Map<UUID, UUID> memberIds = new LinkedHashMap<>();
		for (TeamMembership membership : memberships) {
			memberIds.put(membership.getUserId(), membership.getUserId());
		}

		List<WeeklyPlan> plans = planRepo.findByTeamIdOrderByWeekStartDateDesc(teamId);
		LinkedHashSet<LocalDate> weeks = new LinkedHashSet<>();
		for (WeeklyPlan plan : plans) {
			weeks.add(plan.getWeekStartDate());
			if (weeks.size() == DEFAULT_HISTORY_WEEKS) {
				break;
			}
		}

		List<TeamWeekHistoryEntry> entries = new ArrayList<>();
		for (LocalDate weekStart : weeks) {
			List<WeeklyPlan> weekPlans = planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart);
			Map<UUID, WeeklyPlan> planByUserId = new HashMap<>();
			for (WeeklyPlan weekPlan : weekPlans) {
				planByUserId.put(weekPlan.getOwnerUserId(), weekPlan);
			}

			int compliantCount = 0;
			int plannedPoints = 0;
			int achievedPoints = 0;
			int totalCommits = 0;
			int carryForwardCommits = 0;

			for (UUID memberId : memberIds.keySet()) {
				WeeklyPlan plan = planByUserId.get(memberId);
				if (plan != null && plan.isCompliant() && plan.getState() != PlanState.DRAFT) {
					compliantCount++;
				}
				if (plan == null) {
					continue;
				}

				List<WeeklyCommit> commits = commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId());
				for (WeeklyCommit commit : commits) {
					totalCommits++;
					if (commit.getCarryForwardStreak() > 0) {
						carryForwardCommits++;
					}
					plannedPoints += commit.getEstimatePoints() != null ? commit.getEstimatePoints() : 0;
					if (commit.getOutcome() == CommitOutcome.ACHIEVED) {
						achievedPoints += commit.getEstimatePoints() != null ? commit.getEstimatePoints() : 0;
					}
				}
			}

			double complianceRate = memberCount == 0 ? 0.0 : (double) compliantCount / memberCount;
			double carryForwardRate = totalCommits == 0 ? 0.0 : (double) carryForwardCommits / totalCommits;
			long exceptionCount = exceptionRepo.countByTeamIdAndWeekStartDate(teamId, weekStart);

			entries.add(new TeamWeekHistoryEntry(weekStart, memberCount, complianceRate, plannedPoints, achievedPoints,
					carryForwardRate, exceptionCount));
		}

		return new TeamHistoryResponse(teamId, entries);
	}

	// -------------------------------------------------------------------------
	// RCDO rollup
	// -------------------------------------------------------------------------

	private List<RcdoRollupEntry> buildRcdoRollup(Map<UUID, List<WeeklyCommit>> commitsByPlanId) {
		Map<UUID, int[]> rollup = new LinkedHashMap<>();

		for (List<WeeklyCommit> commits : commitsByPlanId.values()) {
			for (WeeklyCommit c : commits) {
				if (c.getRcdoNodeId() == null) {
					continue;
				}
				int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
				rollup.compute(c.getRcdoNodeId(), (k, v) -> {
					if (v == null) {
						return new int[]{1, pts};
					}
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
		Map<ChessPiece, int[]> dist = new EnumMap<>(ChessPiece.class);

		for (List<WeeklyCommit> commits : commitsByPlanId.values()) {
			for (WeeklyCommit c : commits) {
				if (c.getChessPiece() == null) {
					continue;
				}
				int pts = c.getEstimatePoints() != null ? c.getEstimatePoints() : 0;
				dist.compute(c.getChessPiece(), (k, v) -> {
					if (v == null) {
						return new int[]{1, pts};
					}
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
