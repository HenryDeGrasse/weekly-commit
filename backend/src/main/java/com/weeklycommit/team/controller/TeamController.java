package com.weeklycommit.team.controller;

import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.team.dto.AddCommentRequest;
import com.weeklycommit.team.dto.CapacityOverrideResponse;
import com.weeklycommit.team.dto.CommentResponse;
import com.weeklycommit.team.dto.ExceptionResponse;
import com.weeklycommit.team.dto.ResolveExceptionRequest;
import com.weeklycommit.team.dto.SetCapacityOverrideRequest;
import com.weeklycommit.team.dto.TeamHistoryResponse;
import com.weeklycommit.team.dto.TeamMemberDto;
import com.weeklycommit.team.dto.TeamWeekViewResponse;
import com.weeklycommit.team.service.ManagerReviewService;
import com.weeklycommit.team.service.TeamWeeklyViewService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for team weekly view, manager review, and exception
 * management.
 *
 * <ul>
 * <li>{@code GET /api/teams/{id}/week/{weekStart}} — team weekly view</li>
 * <li>{@code GET /api/teams/{id}/week/{weekStart}/exceptions} — exception
 * queue</li>
 * <li>{@code GET /api/teams/{id}/history} — team trend history</li>
 * <li>{@code POST /api/comments} — add manager comment</li>
 * <li>{@code PUT  /api/capacity-overrides} — set capacity override</li>
 * <li>{@code PUT /api/exceptions/{id}/resolve} — resolve exception</li>
 * </ul>
 */
@RestController
public class TeamController {

	private final TeamWeeklyViewService teamViewService;
	private final ManagerReviewService managerReviewService;
	private final TeamMembershipRepository membershipRepo;
	private final UserAccountRepository userRepo;

	public TeamController(TeamWeeklyViewService teamViewService, ManagerReviewService managerReviewService,
			TeamMembershipRepository membershipRepo, UserAccountRepository userRepo) {
		this.teamViewService = teamViewService;
		this.managerReviewService = managerReviewService;
		this.membershipRepo = membershipRepo;
		this.userRepo = userRepo;
	}

	// -------------------------------------------------------------------------
	// Team weekly view
	// -------------------------------------------------------------------------

	/**
	 * Returns the aggregated weekly view for all team members. Full detail for
	 * managers; peer-filtered view for ICs.
	 */
	@GetMapping("/api/teams/{id}/week/{weekStart}")
	public ResponseEntity<TeamWeekViewResponse> getTeamWeekView(@PathVariable UUID id,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {
		return ResponseEntity.ok(teamViewService.getTeamWeekView(id, weekStart, callerId));
	}

	// -------------------------------------------------------------------------
	// Team members
	// -------------------------------------------------------------------------

	/**
	 * Returns the list of members for a team with their display names. Lightweight
	 * endpoint used by the frontend for assignee dropdowns without fetching the
	 * full team-weekly-view payload.
	 */
	@GetMapping("/api/teams/{id}/members")
	public ResponseEntity<List<TeamMemberDto>> getTeamMembers(@PathVariable UUID id) {
		List<TeamMembership> memberships = membershipRepo.findByTeamId(id);
		List<UUID> userIds = memberships.stream().map(TeamMembership::getUserId).toList();
		java.util.Map<UUID, String> roleMap = memberships.stream()
				.collect(java.util.stream.Collectors.toMap(TeamMembership::getUserId, TeamMembership::getRole));
		List<UserAccount> users = userRepo.findAllById(userIds);
		List<TeamMemberDto> result = users.stream().filter(UserAccount::isActive)
				.map(u -> new TeamMemberDto(u.getId(), u.getDisplayName(), u.getEmail(),
						roleMap.getOrDefault(u.getId(), "MEMBER")))
				.sorted(java.util.Comparator.comparing(TeamMemberDto::displayName)).toList();
		return ResponseEntity.ok(result);
	}

	// -------------------------------------------------------------------------
	// Team history
	// -------------------------------------------------------------------------

	/** Returns recent week-over-week team trends. */
	@GetMapping("/api/teams/{id}/history")
	public ResponseEntity<TeamHistoryResponse> getTeamHistory(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {
		return ResponseEntity.ok(teamViewService.getTeamHistory(id, callerId));
	}

	// -------------------------------------------------------------------------
	// Exception queue
	// -------------------------------------------------------------------------

	/**
	 * Returns the unresolved manager-review exception queue for a team + week,
	 * detecting new exceptions from current plan data. Ordered by severity.
	 */
	@GetMapping("/api/teams/{id}/week/{weekStart}/exceptions")
	public ResponseEntity<List<ExceptionResponse>> getExceptionQueue(@PathVariable UUID id,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {
		return ResponseEntity.ok(managerReviewService.getExceptionQueue(id, weekStart, callerId));
	}

	// -------------------------------------------------------------------------
	// Comments
	// -------------------------------------------------------------------------

	/**
	 * Adds a manager comment on a plan or commit. Only the direct manager of the
	 * plan/commit owner may comment.
	 */
	@PostMapping("/api/comments")
	public ResponseEntity<CommentResponse> addComment(@Valid @RequestBody AddCommentRequest request) {
		String targetType = request.planId() != null ? "PLAN" : "COMMIT";
		UUID targetId = request.planId() != null ? request.planId() : request.commitId();

		if (targetId == null) {
			return ResponseEntity.badRequest().build();
		}

		CommentResponse response = managerReviewService.addComment(targetType, targetId, request.managerId(),
				request.text());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	// -------------------------------------------------------------------------
	// Capacity override
	// -------------------------------------------------------------------------

	/**
	 * Sets (or updates) the capacity budget override for a user for a specific
	 * week. Also updates the associated plan's budget if a plan exists.
	 */
	@PutMapping("/api/capacity-overrides")
	public ResponseEntity<CapacityOverrideResponse> setCapacityOverride(
			@Valid @RequestBody SetCapacityOverrideRequest request) {
		CapacityOverrideResponse response = managerReviewService.setCapacityOverride(request.managerId(),
				request.userId(), request.weekStartDate(), request.overridePoints(), request.reason());
		return ResponseEntity.ok(response);
	}

	// -------------------------------------------------------------------------
	// Resolve exception
	// -------------------------------------------------------------------------

	/**
	 * Marks a manager-review exception as resolved with a required resolution note.
	 */
	@PutMapping("/api/exceptions/{id}/resolve")
	public ResponseEntity<ExceptionResponse> resolveException(@PathVariable UUID id,
			@Valid @RequestBody ResolveExceptionRequest request) {
		return ResponseEntity.ok(managerReviewService.resolveException(id, request.resolution(), request.resolverId()));
	}
}
