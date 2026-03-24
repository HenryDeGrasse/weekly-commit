package com.weeklycommit.notification.service;

import com.weeklycommit.domain.entity.Notification;
import com.weeklycommit.domain.repository.NotificationRepository;
import com.weeklycommit.notification.dto.DigestGroup;
import com.weeklycommit.notification.dto.DigestPayload;
import com.weeklycommit.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles email digest payloads for pending notifications (PRD §18).
 *
 * <p>
 * Actual email delivery is handled by the host application or an external
 * service. This service provides the assembled payload only — v1 does not send
 * emails directly.
 *
 * <p>
 * Priority scheduling:
 * <ul>
 * <li><b>HIGH</b> — immediate delivery (not handled here; immediate
 * notifications are dispatched at creation time).</li>
 * <li><b>MEDIUM</b> — same-day digest (collected once or twice per day).</li>
 * <li><b>LOW</b> — daily digest (collected once per day).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class EmailDigestService {

	private static final Logger log = LoggerFactory.getLogger(EmailDigestService.class);

	private final NotificationRepository notifRepo;

	public EmailDigestService(NotificationRepository notifRepo) {
		this.notifRepo = notifRepo;
	}

	// -------------------------------------------------------------------------
	// Digest preparation
	// -------------------------------------------------------------------------

	/**
	 * Prepares a digest payload for the given user, aggregating all unread
	 * notifications grouped by priority.
	 *
	 * @param userId
	 *            target user
	 * @return assembled digest payload; groups may be empty if no pending
	 *         notifications exist
	 */
	public DigestPayload prepareDigestForUser(UUID userId) {
		return prepareDigestForUser(userId, List.of("HIGH", "MEDIUM", "LOW"));
	}

	private DigestPayload prepareDigestForUser(UUID userId, List<String> priorities) {
		List<DigestGroup> groups = new ArrayList<>();

		for (String priority : priorities) {
			List<Notification> pending = notifRepo.findByRecipientUserIdAndPriorityAndReadOrderByCreatedAtAsc(userId,
					priority, false);
			if (!pending.isEmpty()) {
				List<NotificationResponse> responses = pending.stream().map(NotificationResponse::from).toList();
				groups.add(new DigestGroup(priority, responses));
			}
		}

		log.debug("Prepared digest for user {}: {} group(s)", userId, groups.size());
		return new DigestPayload(userId, Instant.now(), groups);
	}

	/**
	 * Aggregates same-day digest payloads for all users who have unread MEDIUM
	 * priority notifications.
	 *
	 * <p>
	 * In v1 this returns prepared payloads; actual email dispatch is delegated to
	 * the host application.
	 *
	 * @return list of per-user digest payloads; empty if no pending items
	 */
	public List<DigestPayload> prepareSameDayDigests() {
		List<Notification> pendingMedium = notifRepo.findAll().stream()
				.filter(n -> "MEDIUM".equals(n.getPriority()) && !n.isRead()).toList();

		List<UUID> distinctUsers = pendingMedium.stream().map(Notification::getRecipientUserId).distinct().toList();

		List<DigestPayload> digests = new ArrayList<>();
		for (UUID userId : distinctUsers) {
			DigestPayload payload = prepareDigestForUser(userId, List.of("MEDIUM"));
			if (!payload.groups().isEmpty()) {
				digests.add(payload);
			}
		}

		log.info("Same-day digest: {} user(s) with pending MEDIUM notifications", digests.size());
		return digests;
	}

	/**
	 * Aggregates daily digest payloads for all users who have unread LOW or MEDIUM
	 * priority notifications.
	 *
	 * <p>
	 * In v1 this returns prepared payloads; actual email dispatch is delegated to
	 * the host application.
	 *
	 * @return list of per-user digest payloads; empty if no pending items
	 */
	public List<DigestPayload> prepareDailyDigests() {
		List<Notification> pendingLow = notifRepo.findAll().stream()
				.filter(n -> "LOW".equals(n.getPriority()) && !n.isRead()).toList();

		List<UUID> distinctUsers = pendingLow.stream().map(Notification::getRecipientUserId).distinct().toList();

		List<DigestPayload> digests = new ArrayList<>();
		for (UUID userId : distinctUsers) {
			DigestPayload payload = prepareDigestForUser(userId, List.of("LOW"));
			if (!payload.groups().isEmpty()) {
				digests.add(payload);
			}
		}

		log.info("Daily digest: {} user(s) with pending LOW notifications", digests.size());
		return digests;
	}
}
