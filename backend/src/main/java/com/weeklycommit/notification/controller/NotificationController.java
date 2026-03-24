package com.weeklycommit.notification.controller;

import com.weeklycommit.notification.dto.PagedNotificationResponse;
import com.weeklycommit.notification.service.NotificationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for in-app notification management.
 *
 * <ul>
 * <li>{@code GET /api/notifications} — list user's notifications (paginated,
 * filterable by read/unread)</li>
 * <li>{@code PUT /api/notifications/{id}/read} — mark a single notification as
 * read</li>
 * <li>{@code PUT /api/notifications/read-all} — mark all notifications as
 * read</li>
 * </ul>
 *
 * <p>
 * The caller's user id is supplied via the {@code X-Actor-User-Id} request
 * header, consistent with all other endpoints in this module.
 */
@RestController
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	// -------------------------------------------------------------------------
	// List
	// -------------------------------------------------------------------------

	/**
	 * Returns a paginated list of notifications for the authenticated user.
	 *
	 * @param read
	 *            optional filter: {@code true} = read only, {@code false} = unread
	 *            only; omit for all
	 * @param page
	 *            1-based page number (default 1)
	 * @param pageSize
	 *            items per page (default 20, max 100)
	 * @param userId
	 *            caller's user id (from header)
	 */
	@GetMapping("/api/notifications")
	public ResponseEntity<PagedNotificationResponse> listNotifications(@RequestParam(required = false) Boolean read,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID userId) {
		if (userId == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(notificationService.listNotifications(userId, read, page, pageSize));
	}

	// -------------------------------------------------------------------------
	// Mark read
	// -------------------------------------------------------------------------

	/**
	 * Marks a single notification as read for the authenticated user.
	 *
	 * @param id
	 *            notification id
	 * @param userId
	 *            caller's user id (from header)
	 */
	@PutMapping("/api/notifications/{id}/read")
	public ResponseEntity<Void> markAsRead(@PathVariable UUID id,
			@RequestHeader(value = "X-Actor-User-Id", required = false) UUID userId) {
		if (userId == null) {
			return ResponseEntity.badRequest().build();
		}
		notificationService.markAsRead(id, userId);
		return ResponseEntity.noContent().build();
	}

	// -------------------------------------------------------------------------
	// Mark all read
	// -------------------------------------------------------------------------

	/**
	 * Marks all notifications for the authenticated user as read.
	 *
	 * @param userId
	 *            caller's user id (from header)
	 */
	@PutMapping("/api/notifications/read-all")
	public ResponseEntity<Void> markAllAsRead(@RequestHeader(value = "X-Actor-User-Id", required = false) UUID userId) {
		if (userId == null) {
			return ResponseEntity.badRequest().build();
		}
		notificationService.markAllAsRead(userId);
		return ResponseEntity.noContent().build();
	}
}
