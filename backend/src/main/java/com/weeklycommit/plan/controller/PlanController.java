package com.weeklycommit.plan.controller;

import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.lock.dto.LockSnapshotHeaderResponse;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.dto.CreateCommitRequest;
import com.weeklycommit.plan.dto.CreatePlanRequest;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.dto.PlanWithCommitsResponse;
import com.weeklycommit.plan.dto.ReorderCommitsRequest;
import com.weeklycommit.plan.dto.UpdateCommitRequest;
import com.weeklycommit.plan.service.CommitService;
import com.weeklycommit.plan.service.WeeklyPlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

	private final WeeklyPlanService planService;
	private final CommitService commitService;
	private final LockService lockService;

	public PlanController(WeeklyPlanService planService, CommitService commitService, LockService lockService) {
		this.planService = planService;
		this.commitService = commitService;
		this.lockService = lockService;
	}

	/** List all plans for a user in descending week order. */
	@GetMapping
	public ResponseEntity<List<PlanResponse>> listPlans(@RequestParam UUID userId) {
		return ResponseEntity.ok(planService.listPlansForUser(userId));
	}

	/**
	 * Get-or-create the plan for a user + week. If {@code weekStartDate} is omitted
	 * the current week's Monday is used.
	 */
	@PostMapping
	public ResponseEntity<PlanWithCommitsResponse> getOrCreatePlan(@Valid @RequestBody CreatePlanRequest request) {
		java.time.LocalDate weekStart = request.weekStartDate() != null
				? request.weekStartDate()
				: WeeklyPlanService.currentWeekStart();
		WeeklyPlan plan = planService.getOrCreatePlan(request.userId(), weekStart);
		return ResponseEntity.status(HttpStatus.OK).body(planService.getPlanWithCommits(plan.getId()));
	}

	/** Plan detail with commits in priority order. */
	@GetMapping("/{id}")
	public ResponseEntity<PlanWithCommitsResponse> getPlan(@PathVariable UUID id) {
		return ResponseEntity.ok(planService.getPlanWithCommits(id));
	}

	/** Create a commit on a DRAFT plan. */
	@PostMapping("/{planId}/commits")
	public ResponseEntity<CommitResponse> createCommit(@PathVariable UUID planId,
			@Valid @RequestBody CreateCommitRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		WeeklyCommit commit = commitService.createCommit(planId, request, actorUserId);
		return ResponseEntity.status(HttpStatus.CREATED).body(CommitResponse.from(commit));
	}

	/** Update mutable commit fields. Null fields are ignored (partial update). */
	@PutMapping("/{planId}/commits/{commitId}")
	public ResponseEntity<CommitResponse> updateCommit(@PathVariable UUID planId, @PathVariable UUID commitId,
			@RequestBody UpdateCommitRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		WeeklyCommit commit = commitService.updateCommit(planId, commitId, request, actorUserId);
		return ResponseEntity.ok(CommitResponse.from(commit));
	}

	/** Delete a commit and re-number the remaining ones. */
	@DeleteMapping("/{planId}/commits/{commitId}")
	public ResponseEntity<Void> deleteCommit(@PathVariable UUID planId, @PathVariable UUID commitId,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		commitService.deleteCommit(planId, commitId, actorUserId);
		return ResponseEntity.noContent().build();
	}

	/** Reorder commits by supplying all commit IDs in the desired order. */
	@PutMapping("/{planId}/commits/reorder")
	public ResponseEntity<PlanWithCommitsResponse> reorderCommits(@PathVariable UUID planId,
			@Valid @RequestBody ReorderCommitsRequest request,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		commitService.reorderCommits(planId, request.commitIds(), actorUserId);
		return ResponseEntity.ok(planService.getPlanWithCommits(planId));
	}

	// -------------------------------------------------------------------------
	// Lock lifecycle
	// -------------------------------------------------------------------------

	/**
	 * Attempt to manually lock a DRAFT plan. Returns 200 + locked plan on success,
	 * or 422 + error list if validation fails.
	 */
	@PostMapping("/{id}/lock")
	public ResponseEntity<LockResponse> lockPlan(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID actorUserId) {
		LockResponse result = lockService.lockPlan(id, actorUserId);
		if (!result.success()) {
			return ResponseEntity.unprocessableEntity().body(result);
		}
		return ResponseEntity.ok(result);
	}

	/** Retrieve the immutable baseline snapshot for a locked plan. */
	@GetMapping("/{id}/lock-snapshot")
	public ResponseEntity<LockSnapshotHeaderResponse> getLockSnapshot(@PathVariable UUID id) {
		return ResponseEntity.ok(lockService.getLockSnapshot(id));
	}
}
