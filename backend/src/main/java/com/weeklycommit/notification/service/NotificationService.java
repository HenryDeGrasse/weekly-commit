package com.weeklycommit.notification.service;

import com.weeklycommit.domain.entity.Notification;
import com.weeklycommit.domain.entity.NotificationDelivery;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.enums.NotificationEvent;
import com.weeklycommit.domain.repository.NotificationDeliveryRepository;
import com.weeklycommit.domain.repository.NotificationRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.notification.dto.NotificationResponse;
import com.weeklycommit.notification.dto.PagedNotificationResponse;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.team.exception.AccessDeniedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core notification service for the Weekly Commit Module (PRD §18).
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Creating notifications with correct priority assignment.</li>
 * <li>Enforcing frequency controls (max 2 direct reminders per user per task
 * per day).</li>
 * <li>Preparing low-priority notifications for deferred digest delivery.</li>
 * <li>Tracking delivery records for IN_APP and EMAIL channels.</li>
 * <li>Providing read/unread management for the notification panel.</li>
 * </ul>
 *
 * <p>
 * Priority mapping:
 * <ul>
 * <li><b>HIGH</b> — immediate delivery (King exceptions, auto-lock, blocked
 * critical tickets).</li>
 * <li><b>MEDIUM</b> — same-day digest (reminders, reconciliation events,
 * carry-forward alerts).</li>
 * <li><b>LOW</b> — daily digest (draft window opened, background events).</li>
 * </ul>
 */
@Service
@Transactional
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	/** Maximum direct reminders per user per task per calendar day. */
	static final int MAX_REMINDERS_PER_DAY = 2;

	/** TeamMembership role value for a team manager. */
	private static final String TEAM_MANAGER_ROLE = "MANAGER";

	private final NotificationRepository notifRepo;
	private final NotificationDeliveryRepository deliveryRepo;
	private final TeamMembershipRepository membershipRepo;

	public NotificationService(NotificationRepository notifRepo, NotificationDeliveryRepository deliveryRepo,
			TeamMembershipRepository membershipRepo) {
		this.notifRepo = notifRepo;
		this.deliveryRepo = deliveryRepo;
		this.membershipRepo = membershipRepo;
	}

	// -------------------------------------------------------------------------
	// createNotification — main entry point
	// -------------------------------------------------------------------------

	/**
	 * Creates and persists a notification if all guards pass.
	 *
	 * <p>
	 * Guards (checked in order):
	 * <ol>
	 * <li>Frequency limit: at most {@link #MAX_REMINDERS_PER_DAY} notifications for
	 * (userId, event, referenceId) per UTC calendar day for non-HIGH priority.</li>
	 * </ol>
	 *
	 * @param userId
	 *            recipient user id
	 * @param event
	 *            the triggering notification event
	 * @param title
	 *            short display title
	 * @param body
	 *            full notification body
	 * @param priority
	 *            "HIGH", "MEDIUM", or "LOW"; if null the event default is used
	 * @param referenceId
	 *            optional related entity id
	 * @param referenceType
	 *            optional related entity type label
	 * @return the saved {@link Notification}, or {@code null} if suppressed by a
	 *         guard
	 */
	public Notification createNotification(UUID userId, NotificationEvent event, String title, String body,
			String priority, UUID referenceId, String referenceType) {

		String effectivePriority = (priority != null) ? priority : event.defaultPriority();

		// Guard 1: frequency limit (non-HIGH only)
		if (!"HIGH".equals(effectivePriority)) {
			Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
			long todayCount;
			if (referenceId != null) {
				todayCount = notifRepo.countTodayByUserTypeAndRef(userId, event.name(), referenceId, startOfDay);
			} else {
				todayCount = notifRepo.countTodayByUserAndType(userId, event.name(), startOfDay);
			}
			if (todayCount >= MAX_REMINDERS_PER_DAY) {
				log.debug("Notification suppressed (frequency limit): userId={}, event={}, refId={}", userId, event,
						referenceId);
				return null;
			}
		}

		// LOW-priority notifications are still persisted so they can appear in the
		// in-app panel and be included in the next daily digest. Business-hours
		// delivery timing is handled by the digest sender rather than dropping the
		// notification at creation time.

		Notification n = new Notification();
		n.setRecipientUserId(userId);
		n.setNotificationType(event.name());
		n.setTitle(title);
		n.setBody(body);
		n.setPriority(effectivePriority);
		n.setReferenceId(referenceId);
		n.setReferenceType(referenceType);
		Notification saved = notifRepo.save(n);

		// Delivery records
		createDeliveryRecord(saved.getId(), "IN_APP", "DELIVERED");
		// EMAIL: immediate for HIGH, pending-digest for MEDIUM/LOW
		String emailStatus = "HIGH".equals(effectivePriority) ? "PENDING" : "PENDING_DIGEST";
		createDeliveryRecord(saved.getId(), "EMAIL", emailStatus);

		return saved;
	}

	/**
	 * Convenience overload using the event's default priority.
	 */
	public Notification createNotification(UUID userId, NotificationEvent event, String title, String body,
			UUID referenceId, String referenceType) {
		return createNotification(userId, event, title, body, null, referenceId, referenceType);
	}

	// -------------------------------------------------------------------------
	// Read management
	// -------------------------------------------------------------------------

	/**
	 * Marks a single notification as read for the owning user.
	 *
	 * @throws ResourceNotFoundException
	 *             if the notification does not exist
	 * @throws AccessDeniedException
	 *             if the notification belongs to a different user
	 */
	public void markAsRead(UUID notificationId, UUID userId) {
		Notification n = notifRepo.findById(notificationId)
				.orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
		if (!n.getRecipientUserId().equals(userId)) {
			throw new AccessDeniedException("Caller " + userId + " may not read notification " + notificationId);
		}
		if (!n.isRead()) {
			n.setRead(true);
			notifRepo.save(n);
			markInAppDeliveryOpened(notificationId);
		}
	}

	/**
	 * Marks all notifications for a user as read.
	 *
	 * @return number of notifications updated
	 */
	public int markAllAsRead(UUID userId) {
		return notifRepo.markAllReadForUser(userId);
	}

	// -------------------------------------------------------------------------
	// Query
	// -------------------------------------------------------------------------

	/**
	 * Returns a paginated list of notifications for the given user.
	 *
	 * @param userId
	 *            recipient user id
	 * @param read
	 *            if non-null, filters to read ({@code true}) or unread
	 *            ({@code false}) notifications; if null all are returned
	 * @param page
	 *            1-based page number
	 * @param pageSize
	 *            items per page (capped at 100)
	 */
	@Transactional(readOnly = true)
	public PagedNotificationResponse listNotifications(UUID userId, Boolean read, int page, int pageSize) {
		int safePage = Math.max(1, page);
		int safeSize = Math.min(100, Math.max(1, pageSize));
		Pageable pageable = PageRequest.of(safePage - 1, safeSize);

		Page<Notification> result;
		if (read == null) {
			result = notifRepo.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
		} else {
			result = notifRepo.findByRecipientUserIdAndReadOrderByCreatedAtDesc(userId, read, pageable);
		}

		long unreadCount = notifRepo.countByRecipientUserIdAndRead(userId, false);
		List<NotificationResponse> items = result.getContent().stream().map(NotificationResponse::from).toList();
		return new PagedNotificationResponse(items, result.getTotalElements(), safePage, safeSize, unreadCount);
	}

	/**
	 * Returns the count of unread notifications for the given user.
	 */
	@Transactional(readOnly = true)
	public long getUnreadCount(UUID userId) {
		return notifRepo.countByRecipientUserIdAndRead(userId, false);
	}

	// -------------------------------------------------------------------------
	// Manager resolution helper
	// -------------------------------------------------------------------------

	/**
	 * Finds the user id of a manager in the given team, if one exists.
	 *
	 * @param teamId
	 *            the team to search
	 * @return the first MANAGER membership's user id, or empty if none
	 */
	public Optional<UUID> findManagerForTeam(UUID teamId) {
		return membershipRepo.findByTeamId(teamId).stream().filter(m -> TEAM_MANAGER_ROLE.equals(m.getRole()))
				.map(TeamMembership::getUserId).findFirst();
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private void markInAppDeliveryOpened(UUID notificationId) {
		Instant openedAt = Instant.now();
		deliveryRepo.findByNotificationId(notificationId).stream().filter(d -> "IN_APP".equals(d.getChannel()))
				.filter(d -> d.getOpenedAt() == null).forEach(d -> {
					d.setOpenedAt(openedAt);
					deliveryRepo.save(d);
				});
	}

	private void createDeliveryRecord(UUID notificationId, String channel, String status) {
		NotificationDelivery delivery = new NotificationDelivery();
		delivery.setNotificationId(notificationId);
		delivery.setChannel(channel);
		delivery.setStatus(status);
		if ("DELIVERED".equals(status) || "PENDING".equals(status)) {
			delivery.setSentAt(Instant.now());
		}
		deliveryRepo.save(delivery);
	}
}
