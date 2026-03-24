package com.weeklycommit.carryforward.controller;

import com.weeklycommit.carryforward.dto.CarryForwardLineageResponse;
import com.weeklycommit.carryforward.dto.CarryForwardRequest;
import com.weeklycommit.carryforward.dto.CarryForwardResponse;
import com.weeklycommit.carryforward.service.CarryForwardService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for carry-forward operations.
 *
 * <ul>
 * <li>{@code POST /api/plans/{id}/commits/{commitId}/carry-forward} — carry a
 * commit forward into a target week</li>
 * <li>{@code GET /api/commits/{id}/lineage} — retrieve the full carry-forward
 * ancestry chain</li>
 * </ul>
 */
@RestController
public class CarryForwardController {

	private final CarryForwardService carryForwardService;

	public CarryForwardController(CarryForwardService carryForwardService) {
		this.carryForwardService = carryForwardService;
	}

	/**
	 * Carries a commit forward into the target week's plan.
	 *
	 * @param planId
	 *            plan that currently owns the commit (path binding only — verified
	 *            server-side against the commit's plan)
	 * @param commitId
	 *            commit to carry forward
	 * @param request
	 *            target week, reason, and optional free-text
	 * @param actorId
	 *            actor user ID from header (falls back to request.actorUserId())
	 * @return 201 Created with the new commit and provenance link
	 */
	@PostMapping("/api/plans/{planId}/commits/{commitId}/carry-forward")
	public ResponseEntity<CarryForwardResponse> carryForward(@PathVariable UUID planId, @PathVariable UUID commitId,
			@Valid @RequestBody CarryForwardRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorId) {

		UUID actor = actorId != null ? actorId : request.actorUserId();
		CarryForwardResponse response = carryForwardService.carryForward(planId, commitId, request.targetWeekStart(),
				request.reason(), request.reasonText(), actor);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/**
	 * Returns the full carry-forward ancestry chain for a commit.
	 *
	 * @param commitId
	 *            starting commit (may be any point in the chain)
	 * @return lineage ordered oldest-first
	 */
	@GetMapping("/api/commits/{commitId}/lineage")
	public ResponseEntity<CarryForwardLineageResponse> getLineage(@PathVariable UUID commitId) {
		return ResponseEntity.ok(carryForwardService.getCarryForwardLineage(commitId));
	}
}
