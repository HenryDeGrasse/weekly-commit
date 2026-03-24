package com.weeklycommit.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.dto.CreateCommitRequest;
import com.weeklycommit.plan.dto.CreatePlanRequest;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.dto.PlanWithCommitsResponse;
import com.weeklycommit.plan.dto.ReorderCommitsRequest;
import com.weeklycommit.plan.exception.PlanValidationException;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanController.class)
class PlanControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private WeeklyPlanService planService;

	@MockBean
	private CommitService commitService;

	@Autowired
	private ObjectMapper objectMapper;

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan samplePlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(UUID.randomUUID());
		p.setOwnerUserId(UUID.randomUUID());
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(PlanState.DRAFT);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(Instant.now());
		p.setReconcileDeadline(Instant.now());
		return p;
	}

	private PlanWithCommitsResponse planResp(WeeklyPlan plan) {
		return new PlanWithCommitsResponse(PlanResponse.from(plan), List.of(), 0);
	}

	private WeeklyCommit sampleCommit(UUID planId) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(UUID.randomUUID());
		c.setTitle("Test commit");
		c.setChessPiece(ChessPiece.ROOK);
		c.setPriorityOrder(1);
		return c;
	}

	// -------------------------------------------------------------------------
	// GET /api/plans
	// -------------------------------------------------------------------------

	@Test
	void listPlans_returns200() throws Exception {
		UUID userId = UUID.randomUUID();
		WeeklyPlan plan = samplePlan();
		when(planService.listPlansForUser(userId)).thenReturn(List.of(PlanResponse.from(plan)));

		mockMvc.perform(get("/api/plans").param("userId", userId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].state").value("DRAFT"));
	}

	// -------------------------------------------------------------------------
	// POST /api/plans
	// -------------------------------------------------------------------------

	@Test
	void getOrCreate_returns200() throws Exception {
		UUID userId = UUID.randomUUID();
		WeeklyPlan plan = samplePlan();
		when(planService.getOrCreatePlan(eq(userId), any())).thenReturn(plan);
		when(planService.getPlanWithCommits(plan.getId())).thenReturn(planResp(plan));

		CreatePlanRequest req = new CreatePlanRequest(userId, null);
		mockMvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.state").value("DRAFT"));
	}

	@Test
	void getOrCreate_userNotFound_returns404() throws Exception {
		UUID userId = UUID.randomUUID();
		when(planService.getOrCreatePlan(eq(userId), any()))
				.thenThrow(new ResourceNotFoundException("User not found: " + userId));

		CreatePlanRequest req = new CreatePlanRequest(userId, null);
		mockMvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------------------
	// GET /api/plans/{id}
	// -------------------------------------------------------------------------

	@Test
	void getPlan_found_returns200() throws Exception {
		WeeklyPlan plan = samplePlan();
		when(planService.getPlanWithCommits(plan.getId())).thenReturn(planResp(plan));

		mockMvc.perform(get("/api/plans/" + plan.getId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.plan.id").value(plan.getId().toString()));
	}

	@Test
	void getPlan_notFound_returns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(planService.getPlanWithCommits(id)).thenThrow(new ResourceNotFoundException("not found"));

		mockMvc.perform(get("/api/plans/" + id)).andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------------------
	// POST /api/plans/{planId}/commits
	// -------------------------------------------------------------------------

	@Test
	void createCommit_validRequest_returns201() throws Exception {
		WeeklyPlan plan = samplePlan();
		WeeklyCommit commit = sampleCommit(plan.getId());
		when(commitService.createCommit(eq(plan.getId()), any(), any())).thenReturn(commit);

		CreateCommitRequest req = new CreateCommitRequest("Task", ChessPiece.ROOK, null, null, null, null, null);
		mockMvc.perform(post("/api/plans/" + plan.getId() + "/commits").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.chessPiece").value("ROOK"));
	}

	@Test
	void createCommit_kingLimitExceeded_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		when(commitService.createCommit(eq(planId), any(), any()))
				.thenThrow(new PlanValidationException("King limit exceeded"));

		CreateCommitRequest req = new CreateCommitRequest("King", ChessPiece.KING, null, null, null, null, "Criteria");
		mockMvc.perform(post("/api/plans/" + planId + "/commits").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	// -------------------------------------------------------------------------
	// DELETE /api/plans/{planId}/commits/{commitId}
	// -------------------------------------------------------------------------

	@Test
	void deleteCommit_success_returns204() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID commitId = UUID.randomUUID();

		mockMvc.perform(delete("/api/plans/" + planId + "/commits/" + commitId)).andExpect(status().isNoContent());
	}

	// -------------------------------------------------------------------------
	// PUT /api/plans/{planId}/commits/reorder
	// -------------------------------------------------------------------------

	@Test
	void reorderCommits_validRequest_returns200() throws Exception {
		WeeklyPlan plan = samplePlan();
		WeeklyCommit c = sampleCommit(plan.getId());
		when(commitService.reorderCommits(eq(plan.getId()), any(), any())).thenReturn(List.of(c));
		when(planService.getPlanWithCommits(plan.getId()))
				.thenReturn(new PlanWithCommitsResponse(PlanResponse.from(plan), List.of(CommitResponse.from(c)), 0));

		ReorderCommitsRequest req = new ReorderCommitsRequest(List.of(c.getId()));
		mockMvc.perform(put("/api/plans/" + plan.getId() + "/commits/reorder").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isOk())
				.andExpect(jsonPath("$.commits[0].id").value(c.getId().toString()));
	}

	@Test
	void reorderCommits_emptyList_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		ReorderCommitsRequest req = new ReorderCommitsRequest(List.of());

		mockMvc.perform(put("/api/plans/" + planId + "/commits/reorder").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest());
	}
}
