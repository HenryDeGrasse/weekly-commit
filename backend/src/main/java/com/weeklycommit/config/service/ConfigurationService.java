package com.weeklycommit.config.service;

import com.weeklycommit.config.dto.EffectiveCapacityResponse;
import com.weeklycommit.config.dto.EffectiveConfigResponse;
import com.weeklycommit.config.dto.OrgConfigRequest;
import com.weeklycommit.config.dto.OrgConfigResponse;
import com.weeklycommit.config.dto.TeamConfigOverrideRequest;
import com.weeklycommit.config.dto.TeamConfigOverrideResponse;
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
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages org-level and team-level cadence configuration. Provides a merged
 * "effective config" view that combines org defaults with team-level overrides.
 *
 * <h3>Default cadence (PRD §12)</h3>
 * <ul>
 * <li>Week starts Monday (day 0)</li>
 * <li>Draft opens: previous Friday 12:00 = -60 h from week-start</li>
 * <li>Lock due: Monday 12:00 = +12 h from week-start</li>
 * <li>Reconcile opens: Friday 17:00 = +113 h (4 days + 17 h)</li>
 * <li>Reconcile due: next Monday 10:00 = +178 h (7 days + 10 h)</li>
 * </ul>
 *
 * <h3>Capacity resolution chain</h3> Per-user per-week override → team default
 * → org default → system default (10)
 */
@Service
@Transactional
public class ConfigurationService {

	// ---------------------------------------------------------------------------
	// System defaults (used when no OrgConfig row exists yet)
	// ---------------------------------------------------------------------------

	public static final int DEFAULT_DRAFT_OPEN_OFFSET_HOURS = -60;
	public static final int DEFAULT_LOCK_DUE_OFFSET_HOURS = 12;
	public static final int DEFAULT_RECONCILE_OPEN_OFFSET_HOURS = 113;
	public static final int DEFAULT_RECONCILE_DUE_OFFSET_HOURS = 178;
	public static final int DEFAULT_WEEKLY_BUDGET = 10;
	public static final String DEFAULT_WEEK_START_DAY = "MONDAY";
	public static final String DEFAULT_TIMEZONE = "UTC";

	private final OrgConfigRepository orgConfigRepo;
	private final TeamConfigOverrideRepository teamConfigOverrideRepo;
	private final TeamRepository teamRepo;
	private final UserAccountRepository userRepo;
	private final CapacityOverrideRepository capacityOverrideRepo;
	private final AuthorizationService authorizationService;

	public ConfigurationService(OrgConfigRepository orgConfigRepo, TeamConfigOverrideRepository teamConfigOverrideRepo,
			TeamRepository teamRepo, UserAccountRepository userRepo, CapacityOverrideRepository capacityOverrideRepo,
			AuthorizationService authorizationService) {
		this.orgConfigRepo = orgConfigRepo;
		this.teamConfigOverrideRepo = teamConfigOverrideRepo;
		this.teamRepo = teamRepo;
		this.userRepo = userRepo;
		this.capacityOverrideRepo = capacityOverrideRepo;
		this.authorizationService = authorizationService;
	}

	// ---------------------------------------------------------------------------
	// Read: effective config
	// ---------------------------------------------------------------------------

	/**
	 * Returns the effective cadence configuration for a team, merging org defaults
	 * with any team-level overrides. If no {@code OrgConfig} row exists the system
	 * defaults are used.
	 *
	 * @param teamId
	 *            the team whose effective config to resolve
	 * @return fully resolved cadence configuration
	 * @throws ResourceNotFoundException
	 *             if the team does not exist
	 */
	@Transactional(readOnly = true)
	public EffectiveConfigResponse getEffectiveConfig(UUID teamId) {
		Team team = requireTeam(teamId);
		OrgConfig orgCfg = orgConfigRepo.findByOrgId(team.getOrganizationId())
				.orElseGet(() -> systemDefaultOrgConfig(team.getOrganizationId()));
		TeamConfigOverride override = teamConfigOverrideRepo.findByTeamId(teamId).orElse(null);
		return merge(teamId, team.getOrganizationId(), orgCfg, override);
	}

	/**
	 * Returns the org-level configuration for the given org. Returns system
	 * defaults if no config row exists.
	 *
	 * @param orgId
	 *            the organisation ID
	 * @return org configuration (may be system defaults if not yet configured)
	 */
	@Transactional(readOnly = true)
	public OrgConfigResponse getOrgConfig(UUID orgId) {
		OrgConfig cfg = orgConfigRepo.findByOrgId(orgId).orElseGet(() -> systemDefaultOrgConfig(orgId));
		return OrgConfigResponse.from(cfg);
	}

	// ---------------------------------------------------------------------------
	// Read: effective capacity
	// ---------------------------------------------------------------------------

	/**
	 * Returns the effective weekly capacity for a user for the given week. The
	 * resolution chain is:
	 * <ol>
	 * <li>Per-user per-week {@code CapacityOverride}</li>
	 * <li>Team default ({@code TeamConfigOverride.defaultWeeklyBudget})</li>
	 * <li>Org default ({@code OrgConfig.defaultWeeklyBudget})</li>
	 * <li>System default ({@value #DEFAULT_WEEKLY_BUDGET})</li>
	 * </ol>
	 *
	 * @param userId
	 *            user to resolve capacity for
	 * @param weekStart
	 *            the week start date (Monday)
	 * @return resolved capacity with the source label
	 */
	@Transactional(readOnly = true)
	public EffectiveCapacityResponse getEffectiveCapacity(UUID userId, LocalDate weekStart) {
		// 1. Per-user per-week override
		var perWeekOverride = capacityOverrideRepo.findByUserIdAndWeekStartDate(userId, weekStart);
		if (perWeekOverride.isPresent()) {
			return new EffectiveCapacityResponse(userId, weekStart, perWeekOverride.get().getBudgetPoints(),
					"PER_WEEK_OVERRIDE");
		}

		// 2. Team default and org default (needs user's team + org)
		UserAccount user = userRepo.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
		if (user.getHomeTeamId() != null) {
			TeamConfigOverride teamOverride = teamConfigOverrideRepo.findByTeamId(user.getHomeTeamId()).orElse(null);
			if (teamOverride != null && teamOverride.getDefaultWeeklyBudget() != null) {
				return new EffectiveCapacityResponse(userId, weekStart, teamOverride.getDefaultWeeklyBudget(),
						"TEAM_DEFAULT");
			}

			// 3. Org default
			if (user.getOrganizationId() != null) {
				var orgCfg = orgConfigRepo.findByOrgId(user.getOrganizationId()).orElse(null);
				if (orgCfg != null) {
					return new EffectiveCapacityResponse(userId, weekStart, orgCfg.getDefaultWeeklyBudget(),
							"ORG_DEFAULT");
				}
			}
		}

		// 4. System default
		return new EffectiveCapacityResponse(userId, weekStart, DEFAULT_WEEKLY_BUDGET, "SYSTEM_DEFAULT");
	}

	// ---------------------------------------------------------------------------
	// Compute deadlines
	// ---------------------------------------------------------------------------

	/**
	 * Computes the lock deadline {@link Instant} for the given team and week start
	 * date, using the team's effective configuration.
	 *
	 * @param teamId
	 *            team whose effective config to use
	 * @param weekStart
	 *            the Monday of the planning week
	 * @return lock deadline as an {@link Instant}
	 */
	@Transactional(readOnly = true)
	public Instant computeLockDeadline(UUID teamId, LocalDate weekStart) {
		EffectiveConfigResponse cfg = getEffectiveConfig(teamId);
		return weekStart.atStartOfDay(ZoneId.of(cfg.timezone())).plusHours(cfg.lockDueOffsetHours()).toInstant();
	}

	/**
	 * Computes the reconciliation-open deadline {@link Instant} for the given team
	 * and week start date, using the team's effective configuration.
	 *
	 * @param teamId
	 *            team whose effective config to use
	 * @param weekStart
	 *            the Monday of the planning week
	 * @return reconciliation-open deadline as an {@link Instant}
	 */
	@Transactional(readOnly = true)
	public Instant computeReconcileDeadline(UUID teamId, LocalDate weekStart) {
		EffectiveConfigResponse cfg = getEffectiveConfig(teamId);
		return weekStart.atStartOfDay(ZoneId.of(cfg.timezone())).plusHours(cfg.reconcileOpenOffsetHours()).toInstant();
	}

	// ---------------------------------------------------------------------------
	// Write: org config (admin-only)
	// ---------------------------------------------------------------------------

	/**
	 * Creates or replaces the organisation-level configuration. Admin-only
	 * operation.
	 *
	 * @param orgId
	 *            target organisation
	 * @param request
	 *            new configuration values
	 * @param actorUserId
	 *            caller — must have {@code ADMIN} role
	 * @return saved configuration
	 * @throws AccessDeniedException
	 *             if the caller is not an admin
	 */
	public OrgConfigResponse updateOrgConfig(UUID orgId, OrgConfigRequest request, UUID actorUserId) {
		checkAdmin(actorUserId);
		OrgConfig cfg = orgConfigRepo.findByOrgId(orgId).orElseGet(OrgConfig::new);
		cfg.setOrgId(orgId);
		cfg.setWeekStartDay(request.weekStartDay());
		cfg.setDraftOpenOffsetHours(request.draftOpenOffsetHours());
		cfg.setLockDueOffsetHours(request.lockDueOffsetHours());
		cfg.setReconcileOpenOffsetHours(request.reconcileOpenOffsetHours());
		cfg.setReconcileDueOffsetHours(request.reconcileDueOffsetHours());
		cfg.setDefaultWeeklyBudget(request.defaultWeeklyBudget());
		cfg.setTimezone(request.timezone());
		return OrgConfigResponse.from(orgConfigRepo.save(cfg));
	}

	// ---------------------------------------------------------------------------
	// Write: team config override (admin or team manager)
	// ---------------------------------------------------------------------------

	/**
	 * Creates or replaces a team-level configuration override. The caller must be
	 * an {@code ADMIN} or a {@code MANAGER} of the target team.
	 *
	 * @param teamId
	 *            target team
	 * @param request
	 *            override values (null fields clear the override for that field)
	 * @param actorUserId
	 *            caller
	 * @return saved override
	 * @throws AccessDeniedException
	 *             if the caller lacks permission
	 * @throws ResourceNotFoundException
	 *             if the team does not exist
	 */
	public TeamConfigOverrideResponse updateTeamConfig(UUID teamId, TeamConfigOverrideRequest request,
			UUID actorUserId) {
		checkAdminOrTeamManager(actorUserId, teamId);
		requireTeam(teamId); // validate team exists
		TeamConfigOverride override = teamConfigOverrideRepo.findByTeamId(teamId).orElseGet(TeamConfigOverride::new);
		override.setTeamId(teamId);
		override.setWeekStartDay(request.weekStartDay());
		override.setDraftOpenOffsetHours(request.draftOpenOffsetHours());
		override.setLockDueOffsetHours(request.lockDueOffsetHours());
		override.setReconcileOpenOffsetHours(request.reconcileOpenOffsetHours());
		override.setReconcileDueOffsetHours(request.reconcileDueOffsetHours());
		override.setDefaultWeeklyBudget(request.defaultWeeklyBudget());
		override.setTimezone(request.timezone());
		return TeamConfigOverrideResponse.from(teamConfigOverrideRepo.save(override));
	}

	// ---------------------------------------------------------------------------
	// Internal helpers
	// ---------------------------------------------------------------------------

	private EffectiveConfigResponse merge(UUID teamId, UUID orgId, OrgConfig orgCfg, TeamConfigOverride override) {
		boolean hasOverride = override != null && (override.getWeekStartDay() != null
				|| override.getDraftOpenOffsetHours() != null || override.getLockDueOffsetHours() != null
				|| override.getReconcileOpenOffsetHours() != null || override.getReconcileDueOffsetHours() != null
				|| override.getDefaultWeeklyBudget() != null || override.getTimezone() != null);

		String weekStartDay = (override != null && override.getWeekStartDay() != null)
				? override.getWeekStartDay()
				: orgCfg.getWeekStartDay();

		int draftOpen = (override != null && override.getDraftOpenOffsetHours() != null)
				? override.getDraftOpenOffsetHours()
				: orgCfg.getDraftOpenOffsetHours();

		int lockDue = (override != null && override.getLockDueOffsetHours() != null)
				? override.getLockDueOffsetHours()
				: orgCfg.getLockDueOffsetHours();

		int reconcileOpen = (override != null && override.getReconcileOpenOffsetHours() != null)
				? override.getReconcileOpenOffsetHours()
				: orgCfg.getReconcileOpenOffsetHours();

		int reconcileDue = (override != null && override.getReconcileDueOffsetHours() != null)
				? override.getReconcileDueOffsetHours()
				: orgCfg.getReconcileDueOffsetHours();

		int budget = (override != null && override.getDefaultWeeklyBudget() != null)
				? override.getDefaultWeeklyBudget()
				: orgCfg.getDefaultWeeklyBudget();

		String tz = (override != null && override.getTimezone() != null)
				? override.getTimezone()
				: orgCfg.getTimezone();

		return new EffectiveConfigResponse(teamId, orgId, weekStartDay, draftOpen, lockDue, reconcileOpen, reconcileDue,
				budget, tz, hasOverride);
	}

	/**
	 * Returns an in-memory {@link OrgConfig} populated with system defaults. This
	 * is returned when no org config row has been persisted yet; it is <em>not</em>
	 * saved to the database.
	 */
	private static OrgConfig systemDefaultOrgConfig(UUID orgId) {
		OrgConfig cfg = new OrgConfig();
		cfg.setOrgId(orgId);
		cfg.setWeekStartDay(DEFAULT_WEEK_START_DAY);
		cfg.setDraftOpenOffsetHours(DEFAULT_DRAFT_OPEN_OFFSET_HOURS);
		cfg.setLockDueOffsetHours(DEFAULT_LOCK_DUE_OFFSET_HOURS);
		cfg.setReconcileOpenOffsetHours(DEFAULT_RECONCILE_OPEN_OFFSET_HOURS);
		cfg.setReconcileDueOffsetHours(DEFAULT_RECONCILE_DUE_OFFSET_HOURS);
		cfg.setDefaultWeeklyBudget(DEFAULT_WEEKLY_BUDGET);
		cfg.setTimezone(DEFAULT_TIMEZONE);
		return cfg;
	}

	private void checkAdmin(UUID actorUserId) {
		if (actorUserId == null) {
			throw new AccessDeniedException("Authenticated user required");
		}
		UserRole role = authorizationService.getCallerRole(actorUserId);
		if (role != UserRole.ADMIN) {
			throw new AccessDeniedException("Admin role required; caller has role " + role);
		}
	}

	private void checkAdminOrTeamManager(UUID actorUserId, UUID teamId) {
		if (actorUserId == null) {
			throw new AccessDeniedException("Authenticated user required");
		}
		UserRole role = authorizationService.getCallerRole(actorUserId);
		if (role == UserRole.ADMIN) {
			return;
		}
		if (role == UserRole.MANAGER && authorizationService.getManagedTeamIds(actorUserId).contains(teamId)) {
			return;
		}
		throw new AccessDeniedException("Admin or team manager role required to update config for team " + teamId);
	}

	private Team requireTeam(UUID teamId) {
		return teamRepo.findById(teamId).orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
	}
}
