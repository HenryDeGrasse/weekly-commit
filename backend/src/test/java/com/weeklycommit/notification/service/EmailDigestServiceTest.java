package com.weeklycommit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.weeklycommit.domain.entity.Notification;
import com.weeklycommit.domain.repository.NotificationRepository;
import com.weeklycommit.notification.dto.DigestPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDigestServiceTest {

	@Mock
	private NotificationRepository notifRepo;

	@InjectMocks
	private EmailDigestService emailDigestService;

	private UUID userId;
	private Notification mediumNotification;
	private Notification lowNotification;
	private Notification highNotification;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		mediumNotification = notification(userId, "MEDIUM");
		lowNotification = notification(userId, "LOW");
		highNotification = notification(userId, "HIGH");
	}

	@Test
	void prepareSameDayDigests_includesOnlyMediumPriorityNotifications() {
		when(notifRepo.findAll()).thenReturn(List.of(mediumNotification, lowNotification, highNotification));
		when(notifRepo.findByRecipientUserIdAndPriorityAndReadOrderByCreatedAtAsc(userId, "MEDIUM", false))
				.thenReturn(List.of(mediumNotification));

		List<DigestPayload> digests = emailDigestService.prepareSameDayDigests();

		assertThat(digests).hasSize(1);
		assertThat(digests.get(0).groups()).hasSize(1);
		assertThat(digests.get(0).groups().get(0).priority()).isEqualTo("MEDIUM");
		assertThat(digests.get(0).groups().get(0).notifications()).hasSize(1);
	}

	@Test
	void prepareDailyDigests_includesOnlyLowPriorityNotifications() {
		when(notifRepo.findAll()).thenReturn(List.of(mediumNotification, lowNotification, highNotification));
		when(notifRepo.findByRecipientUserIdAndPriorityAndReadOrderByCreatedAtAsc(userId, "LOW", false))
				.thenReturn(List.of(lowNotification));

		List<DigestPayload> digests = emailDigestService.prepareDailyDigests();

		assertThat(digests).hasSize(1);
		assertThat(digests.get(0).groups()).hasSize(1);
		assertThat(digests.get(0).groups().get(0).priority()).isEqualTo("LOW");
		assertThat(digests.get(0).groups().get(0).notifications()).hasSize(1);
	}

	private Notification notification(UUID recipientUserId, String priority) {
		Notification notification = new Notification();
		notification.setId(UUID.randomUUID());
		notification.setRecipientUserId(recipientUserId);
		notification.setNotificationType("AUTO_LOCK_OCCURRED");
		notification.setTitle("Test notification");
		notification.setBody("Test body");
		notification.setPriority(priority);
		notification.setRead(false);
		return notification;
	}
}
