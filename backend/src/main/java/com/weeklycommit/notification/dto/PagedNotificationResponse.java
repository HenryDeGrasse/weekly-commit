package com.weeklycommit.notification.dto;

import java.util.List;

/**
 * Paginated notification list response.
 *
 * @param items
 *            notifications for the current page
 * @param total
 *            total notifications matching the filter
 * @param page
 *            1-based current page number
 * @param pageSize
 *            items per page
 * @param unreadCount
 *            total unread notifications for the user (for badge display)
 */
public record PagedNotificationResponse(List<NotificationResponse> items, long total, int page, int pageSize,
		long unreadCount) {
}
