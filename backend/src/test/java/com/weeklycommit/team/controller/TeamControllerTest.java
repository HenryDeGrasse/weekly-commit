package com.weeklycommit.team.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.enums.ExceptionSeverity;
import com.weeklycommit.domain.enums.ExceptionType;
import com.weeklycommit.team.dto.*;
import com.weeklycommit.team.service.ManagerReviewService;
import com.weeklycommit.team.service.TeamWeeklyViewService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeamController.class)
class TeamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private TeamWeeklyViewService teamWeeklyViewService;

	@MockBean
	private ManagerReviewService managerReviewService;

	@Autowired
	private ObjectMapper objectMapper;

	private final UUID teamId = UUID.randomUUID();
	private final UUID managerId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final LocalDate weekStart = LocalDate.of(2026, 3, 23);

	// ── Team weekly view ─────────────────────────────────────────────────

	@Test
	void getTeamWeekView_returnsOk() throws Exception {
		when(teamWeeklyViewService.getTeamWeekView(eq(teamId), eq(weekStart), any()))
				.thenReturn(new TeamWeekViewResponse(teamId, "Engineering", weekStart, List.of(), List.of(), List.of(),
						List.of(), List.of(), List.of(), List.of()));

		mockMvc.perform(get("/api/teams/" + teamId + "/week/" + weekStart)).andExpect(status().isOk())
				.andExpect(jsonPath("$.teamName").value("Engineering"));
	}

	@Test
	void getTeamWeekView_withCallerHeader_passesThrough() throws Exception {
		when(teamWeeklyViewService.getTeamWeekView(eq(teamId), eq(weekStart), eq(managerId)))
				.thenReturn(new TeamWeekViewResponse(teamId, "Eng", weekStart, List.of(), List.of(), List.of(),
						List.of(), List.of(), List.of(), List.of()));

		mockMvc.perform(
				get("/api/teams/" + teamId + "/week/" + weekStart).header("X-Actor-User-Id", managerId.toString()))
				.andExpect(status().isOk());
	}

	// ── Team history ─────────────────────────────────────────────────────

	@Test
	void getTeamHistory_returnsTrendEntries() throws Exception {
		when(teamWeeklyViewService.getTeamHistory(teamId, null)).thenReturn(new TeamHistoryResponse(teamId,
				List.of(new TeamWeekHistoryEntry(weekStart, 4, 0.75, 20, 15, 0.25, 2))));

		mockMvc.perform(get("/api/teams/" + teamId + "/history")).andExpect(status().isOk())
				.andExpect(jsonPath("$.teamId").value(teamId.toString()))
				.andExpect(jsonPath("$.entries[0].plannedPoints").value(20));
	}

	@Test
	void getTeamHistory_emptyTeam_returnsEmptyEntries() throws Exception {
		when(teamWeeklyViewService.getTeamHistory(teamId, null)).thenReturn(new TeamHistoryResponse(teamId, List.of()));

		mockMvc.perform(get("/api/teams/" + teamId + "/history")).andExpect(status().isOk())
				.andExpect(jsonPath("$.entries").isEmpty());
	}

	// ── Exception queue ──────────────────────────────────────────────────

	@Test
	void getExceptionQueue_returnsExceptions() throws Exception {
		when(managerReviewService.getExceptionQueue(eq(teamId), eq(weekStart), any()))
				.thenReturn(List.of(new ExceptionResponse(UUID.randomUUID(), teamId, null, userId,
						ExceptionType.MISSED_LOCK, ExceptionSeverity.HIGH, "User missed lock deadline", weekStart,
						false, null, null, null, Instant.now())));

		mockMvc.perform(get("/api/teams/" + teamId + "/week/" + weekStart + "/exceptions")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].exceptionType").value("MISSED_LOCK"))
				.andExpect(jsonPath("$[0].severity").value("HIGH"));
	}

	@Test
	void getExceptionQueue_empty_returnsEmptyList() throws Exception {
		when(managerReviewService.getExceptionQueue(eq(teamId), eq(weekStart), any())).thenReturn(List.of());

		mockMvc.perform(get("/api/teams/" + teamId + "/week/" + weekStart + "/exceptions")).andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	// ── Add comment ──────────────────────────────────────────────────────

	@Test
	void addComment_withPlanId_returnsCreated() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID commentId = UUID.randomUUID();
		when(managerReviewService.addComment(eq("PLAN"), eq(planId), eq(managerId), eq("Looks good")))
				.thenReturn(new CommentResponse(commentId, planId, null, managerId, "Looks good", Instant.now()));

		mockMvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddCommentRequest(managerId, planId, null, "Looks good"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("Looks good"));
	}

	@Test
	void addComment_withNullIds_returnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddCommentRequest(managerId, null, null, "Comment text"))))
				.andExpect(status().isBadRequest());
	}

	// ── Capacity override ────────────────────────────────────────────────

	@Test
	void setCapacityOverride_returnsOk() throws Exception {
		when(managerReviewService.setCapacityOverride(eq(managerId), eq(userId), eq(weekStart), eq(15),
				eq("heavy sprint")))
				.thenReturn(new CapacityOverrideResponse(UUID.randomUUID(), userId, weekStart, 15, "heavy sprint",
						managerId, Instant.now()));

		mockMvc.perform(put("/api/capacity-overrides").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						new SetCapacityOverrideRequest(managerId, userId, weekStart, 15, "heavy sprint"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.budgetPoints").value(15));
	}

	// ── Resolve exception ────────────────────────────────────────────────

	@Test
	void resolveException_returnsResolvedResponse() throws Exception {
		UUID exceptionId = UUID.randomUUID();
		when(managerReviewService.resolveException(eq(exceptionId), eq("Addressed in standup"), eq(managerId)))
				.thenReturn(new ExceptionResponse(exceptionId, teamId, null, userId, ExceptionType.MISSED_LOCK,
						ExceptionSeverity.HIGH, "Missed lock", weekStart, true, "Addressed in standup", Instant.now(),
						managerId, Instant.now()));

		mockMvc.perform(put("/api/exceptions/" + exceptionId + "/resolve").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper
						.writeValueAsString(new ResolveExceptionRequest(managerId, "Addressed in standup"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.resolved").value(true))
				.andExpect(jsonPath("$.resolution").value("Addressed in standup"));
	}
}
