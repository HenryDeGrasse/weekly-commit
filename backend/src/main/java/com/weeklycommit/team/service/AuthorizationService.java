package com.weeklycommit.team.service;

import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.exception.AccessDeniedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces role-based access control (RBAC) at the service layer.
 *
 * <p>
 * Role semantics:
 * <ul>
 * <li><b>IC</b> — can only access own plans and commits.</li>
 * <li><b>MANAGER</b> — full detail for direct reports (users in teams where the
 * caller has a MANAGER membership role); aggregates only for indirect
 * reports.</li>
 * <li><b>ADMIN</b> — unrestricted access to all data.</li>
 * </ul>
 *
 * <p>
 * Peer visibility: same-team peers may see only the basic commit summary (see
 * {@link com.weeklycommit.team.dto.PeerCommitView}).
 */
@Service
@Transactional(readOnly = true)
public class AuthorizationService {

	/** Membership role value stored in team_membership.role for a manager. */
	public static final String TEAM_MANAGER_ROLE = "MANAGER";

	private final UserAccountRepository userRepo;
	private final TeamMembershipRepository membershipRepo;
	private final TeamRepository teamRepo;

	public AuthorizationService(UserAccountRepository userRepo, TeamMembershipRepository membershipRepo,
			TeamRepository teamRepo) {
		this.userRepo = userRepo;
		this.membershipRepo = membershipRepo;
		this.teamRepo = teamRepo;
	}

	// -------------------------------------------------------------------------
	// Role resolution
	// -------------------------------------------------------------------------

	/**
	 * Returns the {@link UserRole} for the given user.
	 *
	 * @throws ResourceNotFoundException
	 *             if the user does not exist.
	 */
	public UserRole getCallerRole(UUID callerId) {
		UserAccount user = requireUser(callerId);
		try {
			return UserRole.valueOf(user.getRole().toUpperCase());
		} catch (IllegalArgumentException e) {
			return UserRole.IC; // unknown role defaults to IC for safety
		}
	}

	// -------------------------------------------------------------------------
	// Access checks — plan / user level
	// -------------------------------------------------------------------------

	/**
	 * Verifies that {@code callerId} may access the full detail of
	 * {@code targetUserId}'s plan.
	 *
	 * @throws AccessDeniedException
	 *             if access is denied.
	 */
	public void checkCanAccessUserFullDetail(UUID callerId, UUID targetUserId) {
		if (callerId.equals(targetUserId))
			return; // own data

		UserRole role = getCallerRole(callerId);
		if (role == UserRole.ADMIN)
			return; // admin sees everything

		if (role == UserRole.MANAGER && isDirectManager(callerId, targetUserId))
			return;

		throw new AccessDeniedException(
				"Caller " + callerId + " is not authorised to access full detail for user " + targetUserId);
	}

	/**
	 * Returns {@code true} if {@code callerId} may read the full detail of
	 * {@code targetUserId}'s data (without throwing).
	 */
	public boolean canAccessFullDetail(UUID callerId, UUID targetUserId) {
		try {
			checkCanAccessUserFullDetail(callerId, targetUserId);
			return true;
		} catch (AccessDeniedException e) {
			return false;
		}
	}

	/**
	 * Verifies that {@code callerId} may add a manager comment on data owned by
	 * {@code targetUserId}.
	 *
	 * @throws AccessDeniedException
	 *             if not a direct manager.
	 */
	public void checkIsDirectManager(UUID callerId, UUID targetUserId) {
		UserRole role = getCallerRole(callerId);
		if (role == UserRole.ADMIN)
			return;

		if (role != UserRole.MANAGER) {
			throw new AccessDeniedException("Only MANAGER or ADMIN users may add manager comments");
		}

		if (!isDirectManager(callerId, targetUserId)) {
			throw new AccessDeniedException("Caller " + callerId + " is not the direct manager of user " + targetUserId
					+ "; only direct managers may comment on direct reports' plans");
		}
	}

	/**
	 * Verifies that {@code callerId} may access team-level data for {@code teamId}.
	 *
	 * @throws AccessDeniedException
	 *             if denied.
	 */
	public void checkCanAccessTeam(UUID callerId, UUID teamId) {
		UserRole role = getCallerRole(callerId);
		if (role == UserRole.ADMIN)
			return;

		// Must be a team member (any role)
		boolean isMember = membershipRepo.findByTeamId(teamId).stream().anyMatch(m -> m.getUserId().equals(callerId));
		if (!isMember) {
			throw new AccessDeniedException("Caller " + callerId + " is not a member of team " + teamId);
		}
	}

	// -------------------------------------------------------------------------
	// Direct / indirect manager relationship
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} if {@code managerId} is the direct manager of
	 * {@code userId}.
	 *
	 * <p>
	 * A user is a direct report if their {@code homeTeamId} is one of the teams
	 * where the candidate manager holds a {@code MANAGER} membership role, OR if
	 * they share a team where the candidate manager has a MANAGER membership.
	 */
	public boolean isDirectManager(UUID managerId, UUID userId) {
		UserAccount target = userRepo.findById(userId).orElse(null);
		if (target == null)
			return false;

		// Teams where managerId has MANAGER membership role
		Set<UUID> managedTeamIds = getManagedTeamIds(managerId);
		if (managedTeamIds.isEmpty())
			return false;

		// Primary check: target's homeTeamId is in a managed team
		if (target.getHomeTeamId() != null && managedTeamIds.contains(target.getHomeTeamId()))
			return true;

		// Secondary check: target has any membership in a managed team
		List<TeamMembership> targetMemberships = membershipRepo.findByUserId(userId);
		return targetMemberships.stream().anyMatch(m -> managedTeamIds.contains(m.getTeamId()));
	}

	/**
	 * Returns {@code true} if {@code managerId} is a direct OR indirect manager of
	 * {@code userId}.
	 */
	public boolean isManager(UUID managerId, UUID userId) {
		if (isDirectManager(managerId, userId))
			return true;
		return isIndirectManager(managerId, userId);
	}

	/**
	 * Returns the set of team IDs in which {@code userId} holds a MANAGER
	 * membership role.
	 */
	public Set<UUID> getManagedTeamIds(UUID userId) {
		List<TeamMembership> managerMemberships = membershipRepo.findByUserIdAndRole(userId, TEAM_MANAGER_ROLE);
		Set<UUID> ids = new HashSet<>();
		for (TeamMembership m : managerMemberships) {
			ids.add(m.getTeamId());
		}
		return ids;
	}

	/**
	 * Returns all team IDs the given user belongs to (any role).
	 */
	public Set<UUID> getTeamIds(UUID userId) {
		List<TeamMembership> memberships = membershipRepo.findByUserId(userId);
		Set<UUID> ids = new HashSet<>();
		for (TeamMembership m : memberships) {
			ids.add(m.getTeamId());
		}
		return ids;
	}

	/**
	 * Returns {@code true} if {@code callerId} and {@code targetUserId} share at
	 * least one team.
	 */
	public boolean arePeers(UUID callerId, UUID targetUserId) {
		if (callerId.equals(targetUserId))
			return false;
		Set<UUID> callerTeams = getTeamIds(callerId);
		if (callerTeams.isEmpty())
			return false;
		List<TeamMembership> targetMemberships = membershipRepo.findByUserId(targetUserId);
		return targetMemberships.stream().anyMatch(m -> callerTeams.contains(m.getTeamId()));
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private boolean isIndirectManager(UUID managerId, UUID userId) {
		Set<UUID> managedTeamIds = getManagedTeamIds(managerId);
		if (managedTeamIds.isEmpty())
			return false;

		// Collect child teams of all directly-managed teams (one level)
		Set<UUID> childTeamIds = new HashSet<>();
		for (UUID teamId : managedTeamIds) {
			List<Team> childTeams = teamRepo.findByParentTeamId(teamId);
			for (Team child : childTeams) {
				childTeamIds.add(child.getId());
			}
		}

		if (childTeamIds.isEmpty())
			return false;

		// Check if userId is in any of the child teams
		UserAccount target = userRepo.findById(userId).orElse(null);
		if (target != null && target.getHomeTeamId() != null && childTeamIds.contains(target.getHomeTeamId()))
			return true;

		List<TeamMembership> targetMemberships = membershipRepo.findByUserId(userId);
		return targetMemberships.stream().anyMatch(m -> childTeamIds.contains(m.getTeamId()));
	}

	private UserAccount requireUser(UUID userId) {
		return userRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
	}
}
