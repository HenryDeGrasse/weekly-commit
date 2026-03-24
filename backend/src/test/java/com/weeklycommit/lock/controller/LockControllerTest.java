package com.weeklycommit.lock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.lock.dto.LockSnapshotHeaderResponse;
import com.weeklycommit.lock.dto.ValidationError;
import com.weeklycommit.lock.service.LockService;
import com.weeklycommit.plan.controller.PlanController;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.dto.PlanWithCommitsResponse;
import com.weeklycommit.plan.service.CommitService;
import com.weeklycommit.plan.service.WeeklyPlanService;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanController.class)
class LockControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private WeeklyPlanService planService;

	@MockBean
	private CommitService commitService;

	@MockBean
	private LockService lockService;

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan lockedPlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(UUID.randomUUID());
		p.setOwnerUserId(UUID.randomUUID());
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(PlanState.LOCKED);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now());
		p.setReconcileDeadline(Instant.now());
		return p;
	}

	private PlanWithCommitsResponse planResp(WeeklyPlan plan) {
		return new PlanWithCommitsResponse(PlanResponse.from(plan), List.of(), 0);
	}

	// -------------------------------------------------------------------------
	// POST /api/plans/{id}/lock
	// -------------------------------------------------------------------------

	@Test
	void lockPlan_success_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		WeeklyPlan plan = lockedPlan();
		plan.setId(planId);
		LockResponse resp = LockResponse.success(planResp(plan));
		when(lockService.lockPlan(eq(planId), any())).thenReturn(resp);

		mockMvc.perform(post("/api/plans/" + planId + "/lock")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.errors").isArray())
				.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void lockPlan_validationFailure_returns422() throws Exception {
		UUID planId = UUID.randomUUID();
		LockResponse resp = LockResponse
				.validationFailed(List.of(ValidationError.of("commits", "At least one commit required")));
		when(lockService.lockPlan(eq(planId), any())).thenReturn(resp);

		mockMvc.perform(post("/api/plans/" + planId + "/lock")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errors[0].field").value("commits"));
	}

	@Test
	void lockPlan_planNotFound_returns404() throws Exception {
		UUID planId = UUID.randomUUID();
		when(lockService.lockPlan(eq(planId), any())).thenThrow(new ResourceNotFoundException("not found"));

		mockMvc.perform(post("/api/plans/" + planId + "/lock")).andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------------------
	// GET /api/plans/{id}/lock-snapshot
	// -------------------------------------------------------------------------

	@Test
	void getLockSnapshot_found_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		LockSnapshotHeaderResponse snap = new LockSnapshotHeaderResponse(UUID.randomUUID(), planId, Instant.now(),
				false, "{}");
		when(lockService.getLockSnapshot(planId)).thenReturn(snap);

		mockMvc.perform(get("/api/plans/" + planId + "/lock-snapshot")).andExpect(status().isOk())
				.andExpect(jsonPath("$.planId").value(planId.toString()));
	}

	@Test
	void getLockSnapshot_notFound_returns404() throws Exception {
		UUID planId = UUID.randomUUID();
		when(lockService.getLockSnapshot(planId)).thenThrow(new ResourceNotFoundException("no snapshot"));

		mockMvc.perform(get("/api/plans/" + planId + "/lock-snapshot")).andExpect(status().isNotFound());
	}
}
