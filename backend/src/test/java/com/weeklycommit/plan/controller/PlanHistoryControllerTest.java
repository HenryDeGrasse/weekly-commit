package com.weeklycommit.plan.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.plan.dto.WeeklyPlanHistoryEntry;
import com.weeklycommit.plan.service.PlanHistoryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanHistoryController.class)
class PlanHistoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private PlanHistoryService planHistoryService;

	@Test
	void getPlanHistory_returnsEntries() throws Exception {
		UUID userId = UUID.randomUUID();
		when(planHistoryService.getPlanHistory(userId)).thenReturn(List.of(new WeeklyPlanHistoryEntry(UUID.randomUUID(),
				LocalDate.of(2026, 3, 24), PlanState.RECONCILED, true, 3, 10, 8, 1)));

		mockMvc.perform(get("/api/users/" + userId + "/plan-history")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].planState").value("RECONCILED"))
				.andExpect(jsonPath("$[0].carryForwardCount").value(1));
	}
}
