package com.weeklycommit.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.AuditLog;
import com.weeklycommit.domain.repository.AuditLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central audit-logging service (PRD §13 FR-11).
 *
 * <p>
 * Records are append-only: once written an audit entry is never updated or
 * deleted. The {@link AuditLog} entity enforces this at the JPA column level
 * ({@code updatable = false} on every mutable field).
 *
 * <p>
 * All writes use {@code REQUIRES_NEW} propagation so that an audit failure
 * never rolls back the caller's business transaction, and so that the audit
 * record is committed even if the outer transaction is later rolled back (for
 * failed-attempt logging).
 */
@Service
public class AuditLogService {

	private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

	// ---------------------------------------------------------------------------
	// Canonical action names (action enum contract)
	// ---------------------------------------------------------------------------

	/** Plan lifecycle */
	public static final String PLAN_CREATED = "PLAN_CREATED";
	public static final String PLAN_LOCKED = "PLAN_LOCKED";
	public static final String PLAN_AUTO_LOCKED = "PLAN_AUTO_LOCKED";
	public static final String RECONCILE_OPENED = "RECONCILE_OPENED";
	public static final String RECONCILE_SUBMITTED = "RECONCILE_SUBMITTED";

	/** Commit CRUD */
	public static final String COMMIT_CREATED = "COMMIT_CREATED";
	public static final String COMMIT_UPDATED = "COMMIT_UPDATED";
	public static final String COMMIT_DELETED = "COMMIT_DELETED";
	public static final String COMMIT_OUTCOME_SET = "COMMIT_OUTCOME_SET";

	/** Post-lock scope changes */
	public static final String COMMIT_ADDED_POST_LOCK = "COMMIT_ADDED_POST_LOCK";
	public static final String COMMIT_EDITED_POST_LOCK = "COMMIT_EDITED_POST_LOCK";
	public static final String COMMIT_REMOVED_POST_LOCK = "COMMIT_REMOVED_POST_LOCK";

	/** Carry-forward */
	public static final String CARRY_FORWARD_CREATED = "CARRY_FORWARD_CREATED";

	/** RCDO management */
	public static final String RCDO_CREATED = "RCDO_CREATED";
	public static final String RCDO_UPDATED = "RCDO_UPDATED";
	public static final String RCDO_ARCHIVED = "RCDO_ARCHIVED";
	public static final String RCDO_MOVED = "RCDO_MOVED";

	/** Ticket / work-item */
	public static final String TICKET_CREATED = "TICKET_CREATED";
	public static final String TICKET_UPDATED = "TICKET_UPDATED";
	public static final String TICKET_DELETED = "TICKET_DELETED";
	public static final String TICKET_STATUS_CHANGED = "TICKET_STATUS_CHANGED";

	/** Capacity / comments */
	public static final String CAPACITY_OVERRIDE_SET = "CAPACITY_OVERRIDE_SET";
	public static final String COMMENT_ADDED = "COMMENT_ADDED";

	/** AI */
	public static final String AI_SUGGESTION_CREATED = "AI_SUGGESTION_CREATED";
	public static final String AI_FEEDBACK_SUBMITTED = "AI_FEEDBACK_SUBMITTED";

	/** Config changes */
	public static final String CONFIG_CHANGED = "CONFIG_CHANGED";

	// ---------------------------------------------------------------------------
	// Target type names
	// ---------------------------------------------------------------------------

	public static final String TARGET_PLAN = "PLAN";
	public static final String TARGET_COMMIT = "COMMIT";
	public static final String TARGET_TICKET = "TICKET";
	public static final String TARGET_RCDO = "RCDO";
	public static final String TARGET_CARRY_FORWARD = "CARRY_FORWARD";
	public static final String TARGET_CAPACITY_OVERRIDE = "CAPACITY_OVERRIDE";
	public static final String TARGET_COMMENT = "COMMENT";
	public static final String TARGET_AI_SUGGESTION = "AI_SUGGESTION";
	public static final String TARGET_AI_FEEDBACK = "AI_FEEDBACK";
	public static final String TARGET_CONFIG = "CONFIG";

	// ---------------------------------------------------------------------------
	// Dependencies
	// ---------------------------------------------------------------------------

	private final AuditLogRepository auditLogRepository;
	private final ObjectMapper objectMapper;

	public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
		this.auditLogRepository = auditLogRepository;
		this.objectMapper = objectMapper;
	}

	// ---------------------------------------------------------------------------
	// Core record method
	// ---------------------------------------------------------------------------

	/**
	 * Records a single auditable event. Uses {@code REQUIRES_NEW} propagation so
	 * that audit writes are always committed independently of the caller's
	 * transaction.
	 *
	 * @param action
	 *            canonical action name (see constants above)
	 * @param actorId
	 *            user who performed the action (null for system actions)
	 * @param actorRole
	 *            role of the acting user (IC / MANAGER / ADMIN / SYSTEM)
	 * @param targetType
	 *            entity type being acted upon (PLAN / COMMIT / TICKET …)
	 * @param targetId
	 *            primary key of the affected entity
	 * @param beforePayload
	 *            state of the entity before the action (may be null for creates)
	 * @param afterPayload
	 *            state of the entity after the action (may be null for deletes)
	 * @param ipAddress
	 *            caller's IP address (may be null)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String action, UUID actorId, String actorRole, String targetType, UUID targetId,
			Object beforePayload, Object afterPayload, String ipAddress) {
		try {
			AuditLog entry = new AuditLog();
			entry.setAction(action);
			entry.setActorUserId(actorId);
			entry.setActorRole(actorRole);
			entry.setEntityType(targetType);
			entry.setEntityId(targetId);
			entry.setOldValue(toJson(beforePayload));
			entry.setNewValue(toJson(afterPayload));
			entry.setIpAddress(ipAddress);
			auditLogRepository.save(entry);
		} catch (Exception ex) {
			// Audit failure must never break the business flow
			log.error("Failed to write audit log [action={}, target={}/{}]: {}", action, targetType, targetId,
					ex.getMessage(), ex);
		}
	}

	/** Convenience overload with no IP address. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String action, UUID actorId, String actorRole, String targetType, UUID targetId,
			Object beforePayload, Object afterPayload) {
		record(action, actorId, actorRole, targetType, targetId, beforePayload, afterPayload, null);
	}

	/** Convenience overload for system actions (no actor). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSystem(String action, String targetType, UUID targetId, Object beforePayload,
			Object afterPayload) {
		record(action, null, "SYSTEM", targetType, targetId, beforePayload, afterPayload, null);
	}

	// ---------------------------------------------------------------------------
	// Query
	// ---------------------------------------------------------------------------

	/**
	 * Queries the audit log with optional filters. Results are ordered most-recent
	 * first.
	 *
	 * <p>
	 * Callers are responsible for enforcing visibility (admin vs scoped-to-self)
	 * before invoking this method.
	 */
	@Transactional(readOnly = true)
	public java.util.List<AuditLog> query(String targetType, UUID targetId, UUID actorId, String action, Instant from,
			Instant to) {
		Specification<AuditLog> spec = Specification.where(eqStr("entityType", targetType))
				.and(eqUuid("entityId", targetId)).and(eqUuid("actorUserId", actorId)).and(eqStr("action", action))
				.and(after("createdAt", from)).and(before("createdAt", to));

		return auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	// ---------------------------------------------------------------------------
	// Private helpers
	// ---------------------------------------------------------------------------

	private String toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			log.warn("Audit: failed to serialize payload: {}", ex.getMessage());
			return value.toString();
		}
	}

	private static Specification<AuditLog> eqStr(String field, String value) {
		return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(field), value);
	}

	private static Specification<AuditLog> eqUuid(String field, UUID value) {
		return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(field), value);
	}

	private static Specification<AuditLog> after(String field, Instant value) {
		return (root, query, cb) -> value == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get(field), value);
	}

	private static Specification<AuditLog> before(String field, Instant value) {
		return (root, query, cb) -> value == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get(field), value);
	}
}
