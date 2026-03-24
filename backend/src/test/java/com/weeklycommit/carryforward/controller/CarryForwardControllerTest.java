package com.weeklycommit.carryforward.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.carryforward.dto.CarryForwardLineageDetailResponse;
import com.weeklycommit.carryforward.dto.CarryForwardLineageResponse;
import com.weeklycommit.carryforward.dto.CarryForwardNodeResponse;
import com.weeklycommit.carryforward.service.CarryForwardService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CarryForwardController.class)
class CarryForwardControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CarryForwardService carryForwardService;

	@Test
	void getLegacyLineage_returnsLegacyShape() throws Exception {
		UUID commitId = UUID.randomUUID();
		when(carryForwardService.getCarryForwardLineage(commitId))
				.thenReturn(new CarryForwardLineageResponse(commitId, List.of()));

		mockMvc.perform(get("/api/commits/" + commitId + "/lineage")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rootCommitId").value(commitId.toString()));
	}

	@Test
	void getCarryForwardLineageAlias_returnsFrontendShape() throws Exception {
		UUID commitId = UUID.randomUUID();
		when(carryForwardService.getCarryForwardLineageDetail(commitId)).thenReturn(
				new CarryForwardLineageDetailResponse(commitId, List.of(new CarryForwardNodeResponse(commitId,
						UUID.randomUUID(), LocalDate.of(2026, 3, 24), "Commit", null, 1))));

		mockMvc.perform(get("/api/commits/" + commitId + "/carry-forward-lineage")).andExpect(status().isOk())
				.andExpect(jsonPath("$.currentCommitId").value(commitId.toString()))
				.andExpect(jsonPath("$.chain[0].title").value("Commit"));
	}
}
