package com.weeklycommit.audit.controller;

import com.weeklycommit.audit.dto.AuditLogResponse;
import com.weeklycommit.audit.service.AuditLogService;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for querying the audit log.
 *
 * <p>
 * Access control:
 * <ul>
 * <li>ADMIN — unrestricted: may query any target or actor.</li>
 * <li>MANAGER / IC — scoped to their own actor ID: the {@code actorId} param is
 * ignored and replaced with the caller's own ID.</li>
 * </ul>
 */
@RestController
public class AuditLogController {

	private final AuditLogService auditLogService;
	private final UserAccountRepository userAccountRepository;

	public AuditLogController(AuditLogService auditLogService, UserAccountRepository userAccountRepository) {
		this.auditLogService = auditLogService;
		this.userAccountRepository = userAccountRepository;
	}

	/**
	 * Query the audit log.
	 *
	 * @param targetType
	 *            entity type filter (PLAN, COMMIT, TICKET …)
	 * @param targetId
	 *            specific entity ID filter
	 * @param actorId
	 *            actor filter (ADMIN only — non-admins always see own records)
	 * @param action
	 *            action name filter (PLAN_LOCKED, COMMIT_CREATED …)
	 * @param from
	 *            start of time window (ISO-8601 instant)
	 * @param to
	 *            end of time window (ISO-8601 instant)
	 * @param callerId
	 *            actor derived from request header
	 */
	@GetMapping("/api/audit-log")
	public ResponseEntity<List<AuditLogResponse>> queryAuditLog(@RequestParam(required = false) String targetType,
			@RequestParam(required = false) UUID targetId, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID callerId) {

		if (callerId == null) {
			return ResponseEntity.badRequest().build();
		}

		UUID resolvedActorFilter = resolveActorFilter(callerId, actorId);

		List<AuditLogResponse> results = auditLogService
				.query(targetType, targetId, resolvedActorFilter, action, from, to).stream().map(AuditLogResponse::from)
				.toList();

		return ResponseEntity.ok(results);
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	/**
	 * Resolves the effective actor filter:
	 * <ul>
	 * <li>ADMIN — uses the {@code actorId} param (may be null = all actors)</li>
	 * <li>Everyone else — always scoped to the caller's own ID</li>
	 * </ul>
	 */
	private UUID resolveActorFilter(UUID callerId, UUID requestedActorId) {
		UserAccount caller = userAccountRepository.findById(callerId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + callerId));

		if ("ADMIN".equals(caller.getRole())) {
			// Admin: respect the requested actorId filter (null = all actors)
			return requestedActorId;
		}

		// Non-admin: ignore requested actorId — always own records only
		return callerId;
	}
}
