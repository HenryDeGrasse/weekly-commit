package com.weeklycommit.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.team.exception.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

	@Mock
	private UserAccountRepository userRepo;

	@Mock
	private TeamMembershipRepository membershipRepo;

	@Mock
	private TeamRepository teamRepo;

	@InjectMocks
	private AuthorizationService service;

	private final UUID managerId = UUID.randomUUID();
	private final UUID icId = UUID.randomUUID();
	private final UUID adminId = UUID.randomUUID();
	private final UUID teamId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private UserAccount userWithRole(UUID id, String role) {
		UserAccount u = new UserAccount();
		u.setId(id);
		u.setRole(role);
		u.setEmail(id + "@test.com");
		u.setDisplayName("User-" + id);
		u.setOrganizationId(UUID.randomUUID());
		return u;
	}

	private UserAccount icWithHomeTeam(UUID id, UUID homeTeamId) {
		UserAccount u = userWithRole(id, "IC");
		u.setHomeTeamId(homeTeamId);
		return u;
	}

	private TeamMembership membership(UUID teamId, UUID userId, String role) {
		TeamMembership m = new TeamMembership();
		m.setId(UUID.randomUUID());
		m.setTeamId(teamId);
		m.setUserId(userId);
		m.setRole(role);
		return m;
	}

	// =========================================================================
	// Role resolution
	// =========================================================================

	@Test
	void getCallerRole_returnsICForDefaultUser() {
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "IC")));
		assertThat(service.getCallerRole(icId)).isEqualTo(UserRole.IC);
	}

	@Test
	void getCallerRole_returnsManagerForManagerUser() {
		when(userRepo.findById(managerId)).thenReturn(Optional.of(userWithRole(managerId, "MANAGER")));
		assertThat(service.getCallerRole(managerId)).isEqualTo(UserRole.MANAGER);
	}

	@Test
	void getCallerRole_returnsAdminForAdminUser() {
		when(userRepo.findById(adminId)).thenReturn(Optional.of(userWithRole(adminId, "ADMIN")));
		assertThat(service.getCallerRole(adminId)).isEqualTo(UserRole.ADMIN);
	}

	@Test
	void getCallerRole_unknownRoleDefaultsToIC() {
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "UNKNOWN")));
		assertThat(service.getCallerRole(icId)).isEqualTo(UserRole.IC);
	}

	// =========================================================================
	// checkCanAccessUserFullDetail — IC accessing own data
	// =========================================================================

	@Test
	void checkCanAccessOwnData_succeeds() {
		// No mocking needed — same ID
		service.checkCanAccessUserFullDetail(icId, icId);
	}

	// =========================================================================
	// checkCanAccessUserFullDetail — admin access
	// =========================================================================

	@Test
	void adminCanAccessAnyUser() {
		when(userRepo.findById(adminId)).thenReturn(Optional.of(userWithRole(adminId, "ADMIN")));
		// Should not throw
		service.checkCanAccessUserFullDetail(adminId, icId);
	}

	// =========================================================================
	// checkCanAccessUserFullDetail — manager and direct reports
	// =========================================================================

	@Test
	void managerCanAccessDirectReport_viaHomeTeam() {
		when(userRepo.findById(managerId)).thenReturn(Optional.of(userWithRole(managerId, "MANAGER")));
		when(membershipRepo.findByUserIdAndRole(managerId, AuthorizationService.TEAM_MANAGER_ROLE))
				.thenReturn(List.of(membership(teamId, managerId, "MANAGER")));
		UserAccount ic = icWithHomeTeam(icId, teamId);
		when(userRepo.findById(icId)).thenReturn(Optional.of(ic));

		// Should not throw
		service.checkCanAccessUserFullDetail(managerId, icId);
	}

	@Test
	void managerCannotAccessNonDirectReport() {
		UUID otherTeam = UUID.randomUUID();
		when(userRepo.findById(managerId)).thenReturn(Optional.of(userWithRole(managerId, "MANAGER")));
		when(membershipRepo.findByUserIdAndRole(managerId, AuthorizationService.TEAM_MANAGER_ROLE))
				.thenReturn(List.of(membership(teamId, managerId, "MANAGER")));
		UserAccount ic = icWithHomeTeam(icId, otherTeam);
		when(userRepo.findById(icId)).thenReturn(Optional.of(ic));
		when(membershipRepo.findByUserId(icId)).thenReturn(List.of(membership(otherTeam, icId, "MEMBER")));

		assertThatThrownBy(() -> service.checkCanAccessUserFullDetail(managerId, icId))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("not authorised");
	}

	@Test
	void icCannotAccessAnotherIcsData() {
		UUID otherId = UUID.randomUUID();
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "IC")));
		// IC role → code throws without calling isDirectManager, no membership lookup
		// needed

		assertThatThrownBy(() -> service.checkCanAccessUserFullDetail(icId, otherId))
				.isInstanceOf(AccessDeniedException.class);
	}

	// =========================================================================
	// checkIsDirectManager — comment authorization
	// =========================================================================

	@Test
	void directManagerCanComment() {
		when(userRepo.findById(managerId)).thenReturn(Optional.of(userWithRole(managerId, "MANAGER")));
		when(membershipRepo.findByUserIdAndRole(managerId, AuthorizationService.TEAM_MANAGER_ROLE))
				.thenReturn(List.of(membership(teamId, managerId, "MANAGER")));
		UserAccount ic = icWithHomeTeam(icId, teamId);
		when(userRepo.findById(icId)).thenReturn(Optional.of(ic));

		// Should not throw
		service.checkIsDirectManager(managerId, icId);
	}

	@Test
	void nonManagerCannotComment() {
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "IC")));

		assertThatThrownBy(() -> service.checkIsDirectManager(icId, UUID.randomUUID()))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("MANAGER or ADMIN");
	}

	@Test
	void managerCannotCommentOnNonDirectReport() {
		UUID otherTeam = UUID.randomUUID();
		when(userRepo.findById(managerId)).thenReturn(Optional.of(userWithRole(managerId, "MANAGER")));
		when(membershipRepo.findByUserIdAndRole(managerId, AuthorizationService.TEAM_MANAGER_ROLE))
				.thenReturn(List.of(membership(teamId, managerId, "MANAGER")));
		UserAccount ic = icWithHomeTeam(icId, otherTeam);
		when(userRepo.findById(icId)).thenReturn(Optional.of(ic));
		when(membershipRepo.findByUserId(icId)).thenReturn(List.of(membership(otherTeam, icId, "MEMBER")));

		assertThatThrownBy(() -> service.checkIsDirectManager(managerId, icId))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("not the direct manager");
	}

	// =========================================================================
	// Manager can access direct reports via team membership (not just homeTeam)
	// =========================================================================

	@Test
	void managerCanAccessDirectReport_viaTeamMembership() {
		UUID differentHomeTeam = UUID.randomUUID();
		when(userRepo.findById(managerId)).thenReturn(Optional.of(userWithRole(managerId, "MANAGER")));
		when(membershipRepo.findByUserIdAndRole(managerId, AuthorizationService.TEAM_MANAGER_ROLE))
				.thenReturn(List.of(membership(teamId, managerId, "MANAGER")));
		// IC's homeTeam is different, but they also belong to the managed team
		UserAccount ic = icWithHomeTeam(icId, differentHomeTeam);
		when(userRepo.findById(icId)).thenReturn(Optional.of(ic));
		// IC has a membership in the managed team
		when(membershipRepo.findByUserId(icId)).thenReturn(List.of(membership(teamId, icId, "MEMBER")));

		// Should not throw — reachable via team membership
		service.checkCanAccessUserFullDetail(managerId, icId);
	}

	// =========================================================================
	// Peer visibility (arePeers)
	// =========================================================================

	@Test
	void arePeers_trueWhenSameTeam() {
		UUID peer1 = UUID.randomUUID();
		UUID peer2 = UUID.randomUUID();
		when(membershipRepo.findByUserId(peer1)).thenReturn(List.of(membership(teamId, peer1, "MEMBER")));
		when(membershipRepo.findByUserId(peer2)).thenReturn(List.of(membership(teamId, peer2, "MEMBER")));

		assertThat(service.arePeers(peer1, peer2)).isTrue();
	}

	@Test
	void arePeers_falseWhenDifferentTeams() {
		UUID peer1 = UUID.randomUUID();
		UUID peer2 = UUID.randomUUID();
		UUID team2 = UUID.randomUUID();
		when(membershipRepo.findByUserId(peer1)).thenReturn(List.of(membership(teamId, peer1, "MEMBER")));
		when(membershipRepo.findByUserId(peer2)).thenReturn(List.of(membership(team2, peer2, "MEMBER")));

		assertThat(service.arePeers(peer1, peer2)).isFalse();
	}

	@Test
	void arePeers_falseForSameUser() {
		assertThat(service.arePeers(icId, icId)).isFalse();
	}

	// =========================================================================
	// canAccessFullDetail (boolean form)
	// =========================================================================

	@Test
	void canAccessFullDetail_returnsTrueForOwnData() {
		assertThat(service.canAccessFullDetail(icId, icId)).isTrue();
	}

	@Test
	void canAccessFullDetail_returnsFalseForUnrelatedUser() {
		UUID other = UUID.randomUUID();
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "IC")));
		// IC role → throws AccessDeniedException without calling findByUserIdAndRole

		assertThat(service.canAccessFullDetail(icId, other)).isFalse();
	}

	// =========================================================================
	// checkCanAccessTeam
	// =========================================================================

	@Test
	void checkCanAccessTeam_memberCanAccess() {
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "IC")));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of(membership(teamId, icId, "MEMBER")));

		// Should not throw
		service.checkCanAccessTeam(icId, teamId);
	}

	@Test
	void checkCanAccessTeam_nonMemberDenied() {
		when(userRepo.findById(icId)).thenReturn(Optional.of(userWithRole(icId, "IC")));
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of());

		assertThatThrownBy(() -> service.checkCanAccessTeam(icId, teamId)).isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("not a member");
	}

	@Test
	void checkCanAccessTeam_adminCanAccessWithoutMembership() {
		when(userRepo.findById(adminId)).thenReturn(Optional.of(userWithRole(adminId, "ADMIN")));

		// Should not throw — admin bypasses membership check
		service.checkCanAccessTeam(adminId, teamId);
	}
}
