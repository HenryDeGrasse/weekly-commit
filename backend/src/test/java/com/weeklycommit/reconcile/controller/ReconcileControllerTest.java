package com.weeklycommit.reconcile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.CommitOutcome;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.plan.dto.CommitResponse;
import com.weeklycommit.plan.dto.PlanResponse;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.reconcile.dto.AddCommitData;
import com.weeklycommit.reconcile.dto.ReconciliationViewResponse;
import com.weeklycommit.reconcile.dto.ScopeChangeAction;
import com.weeklycommit.reconcile.dto.ScopeChangeEventResponse;
import com.weeklycommit.reconcile.dto.ScopeChangeRequest;
import com.weeklycommit.reconcile.dto.ScopeChangeTimelineResponse;
import com.weeklycommit.reconcile.dto.SetOutcomeRequest;
import com.weeklycommit.reconcile.service.ReconciliationService;
import com.weeklycommit.reconcile.service.ScopeChangeService;
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

@WebMvcTest(ReconcileController.class)
class ReconcileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private ScopeChangeService scopeChangeService;

	@MockBean
	private ReconciliationService reconciliationService;

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private ScopeChangeTimelineResponse emptyTimeline() {
		return new ScopeChangeTimelineResponse(List.of(), List.of());
	}

	private ReconciliationViewResponse emptyReconcileView(UUID planId) {
		PlanResponse planResponse = new PlanResponse(planId, UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.of(2025, 6, 2), PlanState.RECONCILING, Instant.now(), Instant.now(), 10, true, false,
				Instant.now(), Instant.now());
		return new ReconciliationViewResponse(planResponse, List.of(), 0, 0, 0, 0);
	}

	private CommitResponse sampleCommit(UUID planId) {
		return new CommitResponse(UUID.randomUUID(), planId, UUID.randomUUID(), "Title", null, ChessPiece.ROOK, 1, null,
				null, 3, null, CommitOutcome.ACHIEVED, null, 0, Instant.now(), Instant.now());
	}

	// =========================================================================
	// POST /api/plans/{id}/scope-changes — ADD
	// =========================================================================

	@Test
	void postScopeChange_add_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		ScopeChangeRequest req = new ScopeChangeRequest(ScopeChangeAction.ADD, "Need this fix", null,
				new AddCommitData("Fix bug", ChessPiece.ROOK, null, null, null, 2, null), null);
		when(scopeChangeService.addPostLockCommit(eq(planId), any(), any(), any())).thenReturn(emptyTimeline());

		mockMvc.perform(post("/api/plans/" + planId + "/scope-changes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isOk())
				.andExpect(jsonPath("$.events").isArray());
	}

	// =========================================================================
	// POST /api/plans/{id}/scope-changes — REMOVE
	// =========================================================================

	@Test
	void postScopeChange_remove_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID commitId = UUID.randomUUID();
		ScopeChangeRequest req = new ScopeChangeRequest(ScopeChangeAction.REMOVE, "No longer needed", commitId, null,
				null);
		when(scopeChangeService.removePostLockCommit(eq(planId), eq(commitId), any(), any()))
				.thenReturn(emptyTimeline());

		mockMvc.perform(post("/api/plans/" + planId + "/scope-changes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
	}

	@Test
	void postScopeChange_removeMissingCommitId_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		ScopeChangeRequest req = new ScopeChangeRequest(ScopeChangeAction.REMOVE, "No longer needed", null, null, null);

		mockMvc.perform(post("/api/plans/" + planId + "/scope-changes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest());
	}

	// =========================================================================
	// POST /api/plans/{id}/scope-changes — validation failure
	// =========================================================================

	@Test
	void postScopeChange_invalidState_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		ScopeChangeRequest req = new ScopeChangeRequest(ScopeChangeAction.ADD, "reason", null,
				new AddCommitData("Fix", ChessPiece.ROOK, null, null, null, 2, null), null);
		when(scopeChangeService.addPostLockCommit(eq(planId), any(), any(), any()))
				.thenThrow(new PlanValidationException("Plan not LOCKED"));

		mockMvc.perform(post("/api/plans/" + planId + "/scope-changes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest());
	}

	@Test
	void postScopeChange_editMissingChanges_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		ScopeChangeRequest req = new ScopeChangeRequest(ScopeChangeAction.EDIT, "reason", UUID.randomUUID(), null,
				null);

		mockMvc.perform(post("/api/plans/" + planId + "/scope-changes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest());
	}

	// =========================================================================
	// GET /api/plans/{id}/scope-changes
	// =========================================================================

	@Test
	void getChangeTimeline_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		ScopeChangeEventResponse event = new ScopeChangeEventResponse(UUID.randomUUID(), planId, UUID.randomUUID(),
				com.weeklycommit.domain.enums.ScopeChangeCategory.COMMIT_ADDED, UUID.randomUUID(), "reason", null, "{}",
				Instant.now());
		when(scopeChangeService.getChangeTimeline(planId))
				.thenReturn(new ScopeChangeTimelineResponse(List.of(event), List.of()));

		mockMvc.perform(get("/api/plans/" + planId + "/scope-changes")).andExpect(status().isOk())
				.andExpect(jsonPath("$.events").isArray())
				.andExpect(jsonPath("$.events[0].category").value("COMMIT_ADDED"));
	}

	// =========================================================================
	// GET /api/plans/{id}/reconcile
	// =========================================================================

	@Test
	void getReconciliationView_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		when(reconciliationService.getReconciliationView(planId)).thenReturn(emptyReconcileView(planId));

		mockMvc.perform(get("/api/plans/" + planId + "/reconcile")).andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").exists()).andExpect(jsonPath("$.commits").isArray());
	}

	@Test
	void getReconciliationView_notFound_returns404() throws Exception {
		UUID planId = UUID.randomUUID();
		when(reconciliationService.getReconciliationView(planId)).thenThrow(new ResourceNotFoundException("not found"));

		mockMvc.perform(get("/api/plans/" + planId + "/reconcile")).andExpect(status().isNotFound());
	}

	// =========================================================================
	// PUT /api/plans/{id}/commits/{commitId}/outcome
	// =========================================================================

	@Test
	void setOutcome_achieved_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID commitId = UUID.randomUUID();
		SetOutcomeRequest req = new SetOutcomeRequest(CommitOutcome.ACHIEVED, null);
		when(reconciliationService.setCommitOutcome(eq(planId), eq(commitId), eq(CommitOutcome.ACHIEVED), any()))
				.thenReturn(sampleCommit(planId));

		mockMvc.perform(put("/api/plans/" + planId + "/commits/" + commitId + "/outcome")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("ACHIEVED"));
	}

	@Test
	void setOutcome_notAchievedWithNotes_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID commitId = UUID.randomUUID();
		SetOutcomeRequest req = new SetOutcomeRequest(CommitOutcome.NOT_ACHIEVED, "Blocked by external dependency");

		CommitResponse mockResponse = new CommitResponse(commitId, planId, UUID.randomUUID(), "Title", null,
				ChessPiece.ROOK, 1, null, null, 3, null, CommitOutcome.NOT_ACHIEVED, "Blocked by external dependency",
				0, Instant.now(), Instant.now());
		when(reconciliationService.setCommitOutcome(eq(planId), eq(commitId), eq(CommitOutcome.NOT_ACHIEVED),
				eq("Blocked by external dependency"))).thenReturn(mockResponse);

		mockMvc.perform(put("/api/plans/" + planId + "/commits/" + commitId + "/outcome")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcomeNotes").value("Blocked by external dependency"));
	}

	@Test
	void setOutcome_validationError_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID commitId = UUID.randomUUID();
		SetOutcomeRequest req = new SetOutcomeRequest(CommitOutcome.NOT_ACHIEVED, null);
		when(reconciliationService.setCommitOutcome(any(), any(), any(), any()))
				.thenThrow(new PlanValidationException("notes required"));

		mockMvc.perform(put("/api/plans/" + planId + "/commits/" + commitId + "/outcome")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isBadRequest());
	}

	// =========================================================================
	// POST /api/plans/{id}/reconcile/submit
	// =========================================================================

	@Test
	void submitReconciliation_returns200() throws Exception {
		UUID planId = UUID.randomUUID();
		when(reconciliationService.submitReconciliation(planId)).thenReturn(emptyReconcileView(planId));

		mockMvc.perform(post("/api/plans/" + planId + "/reconcile/submit")).andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").exists());
	}

	@Test
	void submitReconciliation_missingOutcomes_returns400() throws Exception {
		UUID planId = UUID.randomUUID();
		when(reconciliationService.submitReconciliation(planId))
				.thenThrow(new PlanValidationException("Missing outcomes"));

		mockMvc.perform(post("/api/plans/" + planId + "/reconcile/submit")).andExpect(status().isBadRequest());
	}
}
