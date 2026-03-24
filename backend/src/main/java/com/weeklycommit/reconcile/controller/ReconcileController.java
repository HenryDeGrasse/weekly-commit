package com.weeklycommit.reconcile.controller;

import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.reconcile.dto.ReconciliationViewResponse;
import com.weeklycommit.reconcile.dto.ScopeChangeRequest;
import com.weeklycommit.reconcile.dto.ScopeChangeTimelineResponse;
import com.weeklycommit.reconcile.dto.SetOutcomeRequest;
import com.weeklycommit.reconcile.service.ReconciliationService;
import com.weeklycommit.reconcile.service.ScopeChangeService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for post-lock scope changes and reconciliation.
 *
 * <p>
 * All routes are under {@code /api/plans/{id}} consistent with the existing
 * plan controller namespace.
 */
@RestController
@RequestMapping("/api/plans")
public class ReconcileController {

	private final ScopeChangeService scopeChangeService;
	private final ReconciliationService reconciliationService;

	public ReconcileController(ScopeChangeService scopeChangeService, ReconciliationService reconciliationService) {
		this.scopeChangeService = scopeChangeService;
		this.reconciliationService = reconciliationService;
	}

	// -------------------------------------------------------------------------
	// POST /api/plans/{id}/scope-changes — add / remove / edit post-lock
	// -------------------------------------------------------------------------

	/**
	 * Routes to the appropriate scope-change operation based on the {@code action}
	 * field in the request body.
	 *
	 * <ul>
	 * <li>ADD — adds a new commit to a LOCKED plan.</li>
	 * <li>REMOVE — cancels an existing baseline commit.</li>
	 * <li>EDIT — patches mutable fields on a baseline commit.</li>
	 * </ul>
	 */
	@PostMapping("/{id}/scope-changes")
	public ResponseEntity<ScopeChangeTimelineResponse> applyScopeChange(@PathVariable UUID id,
			@Valid @RequestBody ScopeChangeRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {

		validateScopeChangeRequest(request);

		ScopeChangeTimelineResponse result = switch (request.action()) {
			case ADD -> scopeChangeService.addPostLockCommit(id, request.commitData(), request.reason(), actorUserId);
			case REMOVE ->
				scopeChangeService.removePostLockCommit(id, request.commitId(), request.reason(), actorUserId);
			case EDIT -> scopeChangeService.editPostLockCommit(id, request.commitId(), request.changes(),
					request.reason(), actorUserId);
		};

		return ResponseEntity.ok(result);
	}

	// -------------------------------------------------------------------------
	// GET /api/plans/{id}/scope-changes — change timeline
	// -------------------------------------------------------------------------

	/** Returns the chronological list of scope-change events for a plan. */
	@GetMapping("/{id}/scope-changes")
	public ResponseEntity<ScopeChangeTimelineResponse> getChangeTimeline(@PathVariable UUID id) {
		return ResponseEntity.ok(scopeChangeService.getChangeTimeline(id));
	}

	// -------------------------------------------------------------------------
	// GET /api/plans/{id}/reconcile — reconciliation view
	// -------------------------------------------------------------------------

	/** Returns the full reconciliation view (baseline vs current). */
	@GetMapping("/{id}/reconcile")
	public ResponseEntity<ReconciliationViewResponse> getReconciliationView(@PathVariable UUID id) {
		return ResponseEntity.ok(reconciliationService.getReconciliationView(id));
	}

	// -------------------------------------------------------------------------
	// PUT /api/plans/{id}/commits/{commitId}/outcome — set outcome
	// -------------------------------------------------------------------------

	/** Sets (or updates) the outcome for a single commit. */
	@PutMapping("/{id}/commits/{commitId}/outcome")
	public ResponseEntity<CommitResponse> setCommitOutcome(@PathVariable UUID id, @PathVariable UUID commitId,
			@Valid @RequestBody SetOutcomeRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		CommitResponse response = reconciliationService.setCommitOutcome(id, commitId, request.outcome(),
				request.notes());
		return ResponseEntity.ok(response);
	}

	// -------------------------------------------------------------------------
	// POST /api/plans/{id}/reconcile/submit — submit reconciliation
	// -------------------------------------------------------------------------

	/**
	 * Finalises reconciliation: validates, snapshots, and transitions to
	 * RECONCILED.
	 */
	@PostMapping("/{id}/reconcile/submit")
	public ResponseEntity<ReconciliationViewResponse> submitReconciliation(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		return ResponseEntity.ok(reconciliationService.submitReconciliation(id));
	}

	private static void validateScopeChangeRequest(ScopeChangeRequest request) {
		switch (request.action()) {
			case ADD -> {
				if (request.commitData() == null) {
					throw new PlanValidationException("commitData is required for ADD scope changes");
				}
			}
			case REMOVE -> {
				if (request.commitId() == null) {
					throw new PlanValidationException("commitId is required for REMOVE scope changes");
				}
			}
			case EDIT -> {
				if (request.commitId() == null) {
					throw new PlanValidationException("commitId is required for EDIT scope changes");
				}
				if (request.changes() == null) {
					throw new PlanValidationException("changes is required for EDIT scope changes");
				}
			}
		}
	}
}
