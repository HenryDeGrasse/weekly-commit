package com.weeklycommit.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.notification.dto.NotificationResponse;
import com.weeklycommit.notification.dto.PagedNotificationResponse;
import com.weeklycommit.notification.service.NotificationService;
import com.weeklycommit.team.exception.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private NotificationService notificationService;

	@Test
	void listNotifications_requiresActorHeader() throws Exception {
		mockMvc.perform(get("/api/notifications")).andExpect(status().isBadRequest());
	}

	@Test
	void listNotifications_returnsPagedResponse() throws Exception {
		UUID userId = UUID.randomUUID();
		NotificationResponse notification = new NotificationResponse(UUID.randomUUID(), "AUTO_LOCK_OCCURRED", "Title",
				"Body", "HIGH", UUID.randomUUID(), "PLAN", false, Instant.now());
		when(notificationService.listNotifications(eq(userId), eq(false), eq(1), eq(20)))
				.thenReturn(new PagedNotificationResponse(List.of(notification), 1, 1, 20, 1));

		mockMvc.perform(get("/api/notifications").header("X-Actor-User-Id", userId).param("read", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].notificationType").value("AUTO_LOCK_OCCURRED"))
				.andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	void markAsRead_requiresActorHeader() throws Exception {
		mockMvc.perform(put("/api/notifications/" + UUID.randomUUID() + "/read")).andExpect(status().isBadRequest());
	}

	@Test
	void markAsRead_usesActorHeader() throws Exception {
		UUID notificationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		mockMvc.perform(put("/api/notifications/" + notificationId + "/read").header("X-Actor-User-Id", userId))
				.andExpect(status().isNoContent());

		verify(notificationService).markAsRead(notificationId, userId);
	}

	@Test
	void markAsRead_forbiddenWhenNotificationBelongsToAnotherUser() throws Exception {
		UUID notificationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		doThrow(new AccessDeniedException("forbidden")).when(notificationService).markAsRead(any(), any());

		mockMvc.perform(put("/api/notifications/" + notificationId + "/read").header("X-Actor-User-Id", userId))
				.andExpect(status().isForbidden());
	}
}
