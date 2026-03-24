package com.weeklycommit.team.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.team.dto.TeamHistoryResponse;
import com.weeklycommit.team.dto.TeamWeekHistoryEntry;
import com.weeklycommit.team.service.ManagerReviewService;
import com.weeklycommit.team.service.TeamWeeklyViewService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeamController.class)
class TeamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private TeamWeeklyViewService teamWeeklyViewService;

	@MockBean
	private ManagerReviewService managerReviewService;

	@Test
	void getTeamHistory_returnsTrendEntries() throws Exception {
		UUID teamId = UUID.randomUUID();
		when(teamWeeklyViewService.getTeamHistory(teamId, null)).thenReturn(new TeamHistoryResponse(teamId,
				List.of(new TeamWeekHistoryEntry(LocalDate.of(2026, 3, 24), 4, 0.75, 20, 15, 0.25, 2))));

		mockMvc.perform(get("/api/teams/" + teamId + "/history")).andExpect(status().isOk())
				.andExpect(jsonPath("$.teamId").value(teamId.toString()))
				.andExpect(jsonPath("$.entries[0].plannedPoints").value(20));
	}
}
