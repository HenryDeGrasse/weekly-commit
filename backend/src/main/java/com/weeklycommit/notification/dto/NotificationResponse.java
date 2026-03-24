package com.weeklycommit.notification.dto;

import com.weeklycommit.domain.entity.Notification;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single notification.
 *
 * @param id
 *            unique notification id
 * @param notificationType
 *            the {@link com.weeklycommit.domain.enums.NotificationEvent} name
 * @param title
 *            short human-readable title
 * @param body
 *            full notification body text
 * @param priority
 *            "HIGH", "MEDIUM", or "LOW"
 * @param referenceId
 *            optional id of the related entity (plan, commit, ticket)
 * @param referenceType
 *            optional type label of the related entity (e.g. "PLAN")
 * @param read
 *            whether the notification has been read
 * @param createdAt
 *            UTC creation timestamp
 */
public record NotificationResponse(UUID id, String notificationType, String title, String body, String priority,
		UUID referenceId, String referenceType, boolean read, Instant createdAt) {

	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(n.getId(), n.getNotificationType(), n.getTitle(), n.getBody(), n.getPriority(),
				n.getReferenceId(), n.getReferenceType(), n.isRead(), n.getCreatedAt());
	}
}
