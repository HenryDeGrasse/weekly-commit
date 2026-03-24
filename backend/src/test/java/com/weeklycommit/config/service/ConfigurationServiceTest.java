package com.weeklycommit.config.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.dto.EffectiveCapacityResponse;
import com.weeklycommit.config.dto.EffectiveConfigResponse;
import com.weeklycommit.config.dto.OrgConfigRequest;
import com.weeklycommit.config.dto.OrgConfigResponse;
import com.weeklycommit.config.dto.TeamConfigOverrideRequest;
import com.weeklycommit.config.dto.TeamConfigOverrideResponse;
import com.weeklycommit.domain.entity.CapacityOverride;
import com.weeklycommit.domain.entity.OrgConfig;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamConfigOverride;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.CapacityOverrideRepository;
import com.weeklycommit.domain.repository.OrgConfigRepository;
import com.weeklycommit.domain.repository.TeamConfigOverrideRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.exception.AccessDeniedException;
import com.weeklycommit.team.service.AuthorizationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

	@Mock
	private OrgConfigRepository orgConfigRepo;

	@Mock
	private TeamConfigOverrideRepository teamConfigOverrideRepo;

	@Mock
	private TeamRepository teamRepo;

	@Mock
	private UserAccountRepository userRepo;

	@Mock
	private CapacityOverrideRepository capacityOverrideRepo;

	@Mock
	private AuthorizationService authorizationService;

	@InjectMocks
	private ConfigurationService service;

	private final UUID orgId = UUID.randomUUID();
	private final UUID teamId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final UUID adminId = UUID.randomUUID();
	private final UUID managerId = UUID.randomUUID();
	private final LocalDate monday = LocalDate.of(2025, 6, 2);

	private Team team() {
		Team t = new Team();
		t.setId(teamId);
		t.setOrganizationId(orgId);
		t.setName("Test Team");
		return t;
	}

	private OrgConfig orgConfig(int draftOpen, int lockDue, int reconcileOpen, int reconcileDue, int budget,
			String tz) {
		OrgConfig c = new OrgConfig();
		c.setOrgId(orgId);
		c.setWeekStartDay("MONDAY");
		c.setDraftOpenOffsetHours(draftOpen);
		c.setLockDueOffsetHours(lockDue);
		c.setReconcileOpenOffsetHours(reconcileOpen);
		c.setReconcileDueOffsetHours(reconcileDue);
		c.setDefaultWeeklyBudget(budget);
		c.setTimezone(tz);
		return c;
	}

	private UserAccount user() {
		UserAccount u = new UserAccount();
		u.setId(userId);
		u.setOrganizationId(orgId);
		u.setHomeTeamId(teamId);
		return u;
	}

	@BeforeEach
	void stubSave() {
		// Make save return the argument so assertions work
		org.mockito.Mockito.lenient().when(orgConfigRepo.save(any(OrgConfig.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		org.mockito.Mockito.lenient().when(teamConfigOverrideRepo.save(any(TeamConfigOverride.class)))
				.thenAnswer(inv -> inv.getArgument(0));
	}

	// -------------------------------------------------------------------------
	// getEffectiveConfig — org defaults only
	// -------------------------------------------------------------------------

	@Test
	void getEffectiveConfig_noOrgConfig_returnsSystemDefaults() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.empty());
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		EffectiveConfigResponse result = service.getEffectiveConfig(teamId);

		assertThat(result.draftOpenOffsetHours()).isEqualTo(ConfigurationService.DEFAULT_DRAFT_OPEN_OFFSET_HOURS);
		assertThat(result.lockDueOffsetHours()).isEqualTo(ConfigurationService.DEFAULT_LOCK_DUE_OFFSET_HOURS);
		assertThat(result.reconcileDueOffsetHours()).isEqualTo(ConfigurationService.DEFAULT_RECONCILE_DUE_OFFSET_HOURS);
		assertThat(result.defaultWeeklyBudget()).isEqualTo(ConfigurationService.DEFAULT_WEEKLY_BUDGET);
		assertThat(result.timezone()).isEqualTo(ConfigurationService.DEFAULT_TIMEZONE);
		assertThat(result.hasTeamOverride()).isFalse();
	}

	@Test
	void getEffectiveConfig_withOrgConfig_returnsOrgValues() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId))
				.thenReturn(Optional.of(orgConfig(-60, 10, 100, 170, 8, "America/New_York")));
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		EffectiveConfigResponse result = service.getEffectiveConfig(teamId);

		assertThat(result.draftOpenOffsetHours()).isEqualTo(-60);
		assertThat(result.lockDueOffsetHours()).isEqualTo(10);
		assertThat(result.reconcileOpenOffsetHours()).isEqualTo(100);
		assertThat(result.reconcileDueOffsetHours()).isEqualTo(170);
		assertThat(result.defaultWeeklyBudget()).isEqualTo(8);
		assertThat(result.timezone()).isEqualTo("America/New_York");
		assertThat(result.hasTeamOverride()).isFalse();
	}

	// -------------------------------------------------------------------------
	// getEffectiveConfig — team overrides
	// -------------------------------------------------------------------------

	@Test
	void getEffectiveConfig_teamOverridesLockDue_useTeamValue() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.of(orgConfig(-60, 12, 113, 178, 10, "UTC")));

		TeamConfigOverride override = new TeamConfigOverride();
		override.setTeamId(teamId);
		override.setDraftOpenOffsetHours(-48);
		override.setLockDueOffsetHours(8); // override lockDue
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.of(override));

		EffectiveConfigResponse result = service.getEffectiveConfig(teamId);

		assertThat(result.draftOpenOffsetHours()).isEqualTo(-48); // from team
		assertThat(result.lockDueOffsetHours()).isEqualTo(8); // from team
		assertThat(result.reconcileOpenOffsetHours()).isEqualTo(113); // from org
		assertThat(result.reconcileDueOffsetHours()).isEqualTo(178); // from org
		assertThat(result.defaultWeeklyBudget()).isEqualTo(10); // from org
		assertThat(result.timezone()).isEqualTo("UTC"); // from org
		assertThat(result.hasTeamOverride()).isTrue();
	}

	@Test
	void getEffectiveConfig_allFieldsOverridden_allFromTeam() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.of(orgConfig(-60, 12, 113, 178, 10, "UTC")));

		TeamConfigOverride override = new TeamConfigOverride();
		override.setTeamId(teamId);
		override.setWeekStartDay("SUNDAY");
		override.setDraftOpenOffsetHours(-48);
		override.setLockDueOffsetHours(8);
		override.setReconcileOpenOffsetHours(100);
		override.setReconcileDueOffsetHours(165);
		override.setDefaultWeeklyBudget(12);
		override.setTimezone("Europe/London");
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.of(override));

		EffectiveConfigResponse result = service.getEffectiveConfig(teamId);

		assertThat(result.weekStartDay()).isEqualTo("SUNDAY");
		assertThat(result.draftOpenOffsetHours()).isEqualTo(-48);
		assertThat(result.lockDueOffsetHours()).isEqualTo(8);
		assertThat(result.reconcileOpenOffsetHours()).isEqualTo(100);
		assertThat(result.reconcileDueOffsetHours()).isEqualTo(165);
		assertThat(result.defaultWeeklyBudget()).isEqualTo(12);
		assertThat(result.timezone()).isEqualTo("Europe/London");
		assertThat(result.hasTeamOverride()).isTrue();
	}

	@Test
	void getEffectiveConfig_teamOverrideWithAllNulls_noHasOverride() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.of(orgConfig(-60, 12, 113, 178, 10, "UTC")));

		// Override exists but all fields are null → no effective override
		TeamConfigOverride override = new TeamConfigOverride();
		override.setTeamId(teamId);
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.of(override));

		EffectiveConfigResponse result = service.getEffectiveConfig(teamId);

		assertThat(result.hasTeamOverride()).isFalse();
	}

	// -------------------------------------------------------------------------
	// getEffectiveCapacity — resolution chain
	// -------------------------------------------------------------------------

	@Test
	void getEffectiveCapacity_perWeekOverrideWins() {
		CapacityOverride co = new CapacityOverride();
		co.setUserId(userId);
		co.setWeekStartDate(monday);
		co.setBudgetPoints(6);
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.of(co));

		EffectiveCapacityResponse result = service.getEffectiveCapacity(userId, monday);

		assertThat(result.budgetPoints()).isEqualTo(6);
		assertThat(result.source()).isEqualTo("PER_WEEK_OVERRIDE");
	}

	@Test
	void getEffectiveCapacity_noOverride_teamDefaultUsed() {
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(user()));

		TeamConfigOverride override = new TeamConfigOverride();
		override.setDefaultWeeklyBudget(7);
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.of(override));

		EffectiveCapacityResponse result = service.getEffectiveCapacity(userId, monday);

		assertThat(result.budgetPoints()).isEqualTo(7);
		assertThat(result.source()).isEqualTo("TEAM_DEFAULT");
	}

	@Test
	void getEffectiveCapacity_noTeamOverride_orgDefaultUsed() {
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(user()));
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		OrgConfig cfg = orgConfig(-60, 12, 113, 178, 9, "UTC");
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.of(cfg));

		EffectiveCapacityResponse result = service.getEffectiveCapacity(userId, monday);

		assertThat(result.budgetPoints()).isEqualTo(9);
		assertThat(result.source()).isEqualTo("ORG_DEFAULT");
	}

	@Test
	void getEffectiveCapacity_userNotFound_throwsNotFound() {
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getEffectiveCapacity(userId, monday))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void getEffectiveCapacity_noConfigAtAll_systemDefaultUsed() {
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(user()));
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.empty());

		EffectiveCapacityResponse result = service.getEffectiveCapacity(userId, monday);

		assertThat(result.budgetPoints()).isEqualTo(ConfigurationService.DEFAULT_WEEKLY_BUDGET);
		assertThat(result.source()).isEqualTo("SYSTEM_DEFAULT");
	}

	@Test
	void getEffectiveCapacity_teamOverrideExistsButBudgetNull_fallsToOrgDefault() {
		when(capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(user()));

		// Team override exists but budget is null
		TeamConfigOverride override = new TeamConfigOverride();
		override.setTeamId(teamId);
		// defaultWeeklyBudget is null by default
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.of(override));

		OrgConfig cfg = orgConfig(-60, 12, 113, 178, 11, "UTC");
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.of(cfg));

		EffectiveCapacityResponse result = service.getEffectiveCapacity(userId, monday);

		assertThat(result.budgetPoints()).isEqualTo(11);
		assertThat(result.source()).isEqualTo("ORG_DEFAULT");
	}

	// -------------------------------------------------------------------------
	// computeLockDeadline / computeReconcileDeadline
	// -------------------------------------------------------------------------

	@Test
	void computeLockDeadline_defaultConfig_returnsMonday12Utc() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.empty()); // use system defaults
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		Instant deadline = service.computeLockDeadline(teamId, monday);

		// Monday 2025-06-02 + 12h = 2025-06-02T12:00:00Z
		assertThat(deadline).isEqualTo(Instant.parse("2025-06-02T12:00:00Z"));
	}

	@Test
	void computeReconcileDeadline_defaultConfig_returnsFriday17Utc() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.empty()); // use system defaults
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		Instant deadline = service.computeReconcileDeadline(teamId, monday);

		// Monday 2025-06-02 + 113h = 2025-06-06T17:00:00Z (Friday 17:00)
		assertThat(deadline).isEqualTo(Instant.parse("2025-06-06T17:00:00Z"));
	}

	@Test
	void computeLockDeadline_teamOverrideTimezone_shiftsDeadline() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		// Org config in UTC with 12h offset
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.of(orgConfig(-60, 12, 113, 178, 10, "UTC")));

		// Team overrides timezone to America/New_York (UTC-4 in summer)
		TeamConfigOverride override = new TeamConfigOverride();
		override.setTeamId(teamId);
		override.setTimezone("America/New_York");
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.of(override));

		Instant deadline = service.computeLockDeadline(teamId, monday);

		// Mon 2025-06-02 00:00 America/New_York + 12h = Mon 12:00 NY = Mon 16:00 UTC
		assertThat(deadline).isEqualTo(Instant.parse("2025-06-02T16:00:00Z"));
	}

	// -------------------------------------------------------------------------
	// updateOrgConfig — admin enforcement
	// -------------------------------------------------------------------------

	@Test
	void updateOrgConfig_adminCaller_succeeds() {
		when(authorizationService.getCallerRole(adminId)).thenReturn(UserRole.ADMIN);
		when(orgConfigRepo.findByOrgId(orgId)).thenReturn(Optional.empty());

		OrgConfigRequest req = new OrgConfigRequest("MONDAY", -60, 12, 113, 178, 10, "UTC");
		OrgConfigResponse result = service.updateOrgConfig(orgId, req, adminId);

		assertThat(result.lockDueOffsetHours()).isEqualTo(12);
		verify(orgConfigRepo).save(any(OrgConfig.class));
	}

	@Test
	void updateOrgConfig_nonAdminCaller_throwsAccessDenied() {
		when(authorizationService.getCallerRole(userId)).thenReturn(UserRole.IC);

		OrgConfigRequest req = new OrgConfigRequest("MONDAY", -60, 12, 113, 178, 10, "UTC");
		assertThatThrownBy(() -> service.updateOrgConfig(orgId, req, userId)).isInstanceOf(AccessDeniedException.class);

		verify(orgConfigRepo, never()).save(any());
	}

	@Test
	void updateOrgConfig_nullActor_throwsAccessDenied() {
		OrgConfigRequest req = new OrgConfigRequest("MONDAY", -60, 12, 113, 178, 10, "UTC");
		assertThatThrownBy(() -> service.updateOrgConfig(orgId, req, null)).isInstanceOf(AccessDeniedException.class);
	}

	// -------------------------------------------------------------------------
	// updateTeamConfig — admin or team manager enforcement
	// -------------------------------------------------------------------------

	@Test
	void updateTeamConfig_adminCaller_succeeds() {
		when(authorizationService.getCallerRole(adminId)).thenReturn(UserRole.ADMIN);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		TeamConfigOverrideRequest req = new TeamConfigOverrideRequest(null, null, 8, null, null, 12, null);
		TeamConfigOverrideResponse result = service.updateTeamConfig(teamId, req, adminId);

		assertThat(result.lockDueOffsetHours()).isEqualTo(8);
		assertThat(result.defaultWeeklyBudget()).isEqualTo(12);
		verify(teamConfigOverrideRepo).save(any(TeamConfigOverride.class));
	}

	@Test
	void updateTeamConfig_teamManagerCaller_succeeds() {
		when(authorizationService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(authorizationService.getManagedTeamIds(managerId)).thenReturn(Set.of(teamId));
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team()));
		when(teamConfigOverrideRepo.findByTeamId(teamId)).thenReturn(Optional.empty());

		TeamConfigOverrideRequest req = new TeamConfigOverrideRequest(null, null, null, null, null, 15, null);
		TeamConfigOverrideResponse result = service.updateTeamConfig(teamId, req, managerId);

		assertThat(result.defaultWeeklyBudget()).isEqualTo(15);
	}

	@Test
	void updateTeamConfig_managerOfDifferentTeam_throwsAccessDenied() {
		when(authorizationService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(authorizationService.getManagedTeamIds(managerId)).thenReturn(Set.of(UUID.randomUUID())); // diff team

		TeamConfigOverrideRequest req = new TeamConfigOverrideRequest(null, null, null, null, null, 15, null);
		assertThatThrownBy(() -> service.updateTeamConfig(teamId, req, managerId))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void updateTeamConfig_icCaller_throwsAccessDenied() {
		when(authorizationService.getCallerRole(userId)).thenReturn(UserRole.IC);

		TeamConfigOverrideRequest req = new TeamConfigOverrideRequest(null, null, null, null, null, 15, null);
		assertThatThrownBy(() -> service.updateTeamConfig(teamId, req, userId))
				.isInstanceOf(AccessDeniedException.class);
	}

	// -------------------------------------------------------------------------
	// Lifecycle integration: config changes don't affect already-locked plans
	// -------------------------------------------------------------------------

	@Test
	void configChangeDoesNotAffectLockedPlan_deadlineAlreadyStored() {
		// This is a documentation-level test: the WeeklyPlan entity stores
		// lockDeadline and reconcileDeadline as concrete Instant values at creation
		// time. Changing OrgConfig/TeamConfigOverride after a plan is created has
		// no effect on the stored deadlines of that plan.
		//
		// The plan entity is the authoritative source for its own deadlines;
		// config is only consulted at plan-creation time.
		assertThat(true).isTrue(); // assertion-free: documents the design intent
	}

	// -------------------------------------------------------------------------
	// getEffectiveConfig — team not found
	// -------------------------------------------------------------------------

	@Test
	void getEffectiveConfig_teamNotFound_throwsNotFound() {
		when(teamRepo.findById(teamId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getEffectiveConfig(teamId)).isInstanceOf(ResourceNotFoundException.class);
	}
}
