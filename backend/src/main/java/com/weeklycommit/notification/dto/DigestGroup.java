package com.weeklycommit.notification.dto;

import java.util.List;

/**
 * A priority-grouped collection of notifications for digest assembly.
 *
 * @param priority
 *            "HIGH", "MEDIUM", or "LOW"
 * @param notifications
 *            notifications in this priority group
 */
public record DigestGroup(String priority, List<NotificationResponse> notifications) {
}
