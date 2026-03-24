package com.weeklycommit.config.controller;

import com.weeklycommit.config.dto.EffectiveCapacityResponse;
import com.weeklycommit.config.dto.EffectiveConfigResponse;
import com.weeklycommit.config.dto.OrgConfigRequest;
import com.weeklycommit.config.dto.OrgConfigResponse;
import com.weeklycommit.config.dto.TeamConfigOverrideRequest;
import com.weeklycommit.config.dto.TeamConfigOverrideResponse;
import com.weeklycommit.config.service.ConfigurationService;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for cadence and capacity configuration.
 *
 * <ul>
 * <li>{@code GET  /api/config/org} — org-level config (resolved for caller's
 * org)</li>
 * <li>{@code PUT  /api/config/org} — replace org config (admin only)</li>
 * <li>{@code GET /api/config/teams/{id}} — effective config for a team</li>
 * <li>{@code PUT /api/config/teams/{id}} — update team override (admin or team
 * manager)</li>
 * <li>{@code GET /api/config/capacity/{userId}} — effective capacity for a
 * user/week</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

	private final ConfigurationService configService;
	private final UserAccountRepository userRepo;

	public ConfigController(ConfigurationService configService, UserAccountRepository userRepo) {
		this.configService = configService;
		this.userRepo = userRepo;
	}

	// -------------------------------------------------------------------------
	// Org config
	// -------------------------------------------------------------------------

	/**
	 * Returns the org-level cadence configuration for the authenticated user's
	 * organisation. Returns system defaults if no config has been saved yet.
	 */
	@GetMapping("/org")
	public ResponseEntity<OrgConfigResponse> getOrgConfig(
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		if (actorUserId == null) {
			return ResponseEntity.badRequest().build();
		}
		UUID orgId = resolveOrgId(actorUserId);
		return ResponseEntity.ok(configService.getOrgConfig(orgId));
	}

	/**
	 * Creates or replaces the org-level cadence configuration. Admin-only.
	 */
	@PutMapping("/org")
	public ResponseEntity<OrgConfigResponse> updateOrgConfig(@Valid @RequestBody OrgConfigRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		if (actorUserId == null) {
			return ResponseEntity.badRequest().build();
		}
		UUID orgId = resolveOrgId(actorUserId);
		return ResponseEntity.ok(configService.updateOrgConfig(orgId, request, actorUserId));
	}

	// -------------------------------------------------------------------------
	// Team config
	// -------------------------------------------------------------------------

	/**
	 * Returns the fully resolved (merged) cadence config for a team, applying any
	 * team-level overrides on top of org defaults.
	 */
	@GetMapping("/teams/{id}")
	public ResponseEntity<EffectiveConfigResponse> getTeamConfig(@PathVariable UUID id) {
		return ResponseEntity.ok(configService.getEffectiveConfig(id));
	}

	/**
	 * Creates or replaces the team-level cadence override. Null fields clear the
	 * override for that field (falling back to org default). Admin or team manager
	 * only.
	 */
	@PutMapping("/teams/{id}")
	public ResponseEntity<TeamConfigOverrideResponse> updateTeamConfig(@PathVariable UUID id,
			@RequestBody TeamConfigOverrideRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		if (actorUserId == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(configService.updateTeamConfig(id, request, actorUserId));
	}

	// -------------------------------------------------------------------------
	// Effective capacity
	// -------------------------------------------------------------------------

	/**
	 * Returns the effective weekly capacity for a user for the given week. The
	 * {@code week} parameter must be the ISO Monday date (e.g. {@code 2025-06-02}).
	 */
	@GetMapping("/capacity/{userId}")
	public ResponseEntity<EffectiveCapacityResponse> getEffectiveCapacity(@PathVariable UUID userId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
		return ResponseEntity.ok(configService.getEffectiveCapacity(userId, week));
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private UUID resolveOrgId(UUID userId) {
		UserAccount user = userRepo.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
		if (user.getOrganizationId() == null) {
			throw new ResourceNotFoundException("User " + userId + " has no organisation");
		}
		return user.getOrganizationId();
	}
}
