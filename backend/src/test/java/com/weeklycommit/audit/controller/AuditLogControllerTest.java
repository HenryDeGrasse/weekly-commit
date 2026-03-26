package com.weeklycommit.audit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.audit.service.AuditLogService;
import com.weeklycommit.domain.entity.AuditLog;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AuditLogService auditLogService;

	@MockBean
	private UserAccountRepository userAccountRepository;

	private final UUID adminId = UUID.randomUUID();
	private final UUID icId = UUID.randomUUID();

	private UserAccount makeUser(UUID id, String role) {
		UserAccount u = new UserAccount();
		u.setId(id);
		u.setRole(role);
		return u;
	}

	private AuditLog makeLogEntry(UUID actorId) {
		AuditLog e = new AuditLog();
		e.setAction("PLAN_CREATED");
		e.setActorUserId(actorId);
		e.setActorRole("IC");
		e.setEntityType("PLAN");
		e.setEntityId(UUID.randomUUID());
		// createdAt is set by @CreationTimestamp — no setter needed in test
		return e;
	}

	@Test
	void query_withoutCallerId_returns400() throws Exception {
		mockMvc.perform(get("/api/audit-log")).andExpect(status().isBadRequest());
	}

	@Test
	void query_adminUser_canSeeAllActors() throws Exception {
		when(userAccountRepository.findById(adminId)).thenReturn(Optional.of(makeUser(adminId, "ADMIN")));
		when(auditLogService.query(any(), any(), any(), any(), any(), any())).thenReturn(List.of(makeLogEntry(icId)));

		mockMvc.perform(get("/api/audit-log").header("X-Actor-User-Id", adminId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].action").value("PLAN_CREATED"));
	}

	@Test
	void query_icUser_scopedToOwnRecords() throws Exception {
		when(userAccountRepository.findById(icId)).thenReturn(Optional.of(makeUser(icId, "IC")));
		when(auditLogService.query(any(), any(), eq(icId), any(), any(), any()))
				.thenReturn(List.of(makeLogEntry(icId)));

		mockMvc.perform(get("/api/audit-log").header("X-Actor-User-Id", icId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].action").value("PLAN_CREATED"));
	}

	@Test
	void query_withFilters_passesThrough() throws Exception {
		UUID targetId = UUID.randomUUID();
		when(userAccountRepository.findById(adminId)).thenReturn(Optional.of(makeUser(adminId, "ADMIN")));
		when(auditLogService.query(eq("PLAN"), eq(targetId), any(), eq("PLAN_LOCKED"), any(), any()))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/audit-log").header("X-Actor-User-Id", adminId.toString()).param("targetType", "PLAN")
				.param("targetId", targetId.toString()).param("action", "PLAN_LOCKED")).andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void query_unknownUser_returns404() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(userAccountRepository.findById(unknownId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/audit-log").header("X-Actor-User-Id", unknownId.toString()))
				.andExpect(status().isNotFound());
	}
}
