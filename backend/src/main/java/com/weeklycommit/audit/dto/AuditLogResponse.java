package com.weeklycommit.audit.dto;

import com.weeklycommit.domain.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a single audit-log entry.
 */
public record AuditLogResponse(UUID id, UUID actorUserId, String actorRole, String action, String targetType,
		UUID targetId, String beforePayload, String afterPayload, String ipAddress, Instant timestamp) {

	public static AuditLogResponse from(AuditLog entry) {
		return new AuditLogResponse(entry.getId(), entry.getActorUserId(), entry.getActorRole(), entry.getAction(),
				entry.getEntityType(), entry.getEntityId(), entry.getOldValue(), entry.getNewValue(),
				entry.getIpAddress(), entry.getCreatedAt());
	}
}
