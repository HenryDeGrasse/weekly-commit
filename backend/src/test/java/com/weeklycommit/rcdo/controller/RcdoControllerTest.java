package com.weeklycommit.rcdo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.enums.RcdoNodeStatus;
import com.weeklycommit.domain.enums.RcdoNodeType;
import com.weeklycommit.rcdo.dto.CreateRcdoNodeRequest;
import com.weeklycommit.rcdo.dto.RcdoNodeWithPathResponse;
import com.weeklycommit.rcdo.dto.RcdoNodeResponse;
import com.weeklycommit.rcdo.dto.UpdateRcdoNodeRequest;
import com.weeklycommit.rcdo.exception.RcdoValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import com.weeklycommit.rcdo.service.RcdoService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RcdoController.class)
class RcdoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private RcdoService rcdoService;

	@Autowired
	private ObjectMapper objectMapper;

	private RcdoNode sampleNode(RcdoNodeType type) {
		RcdoNode n = new RcdoNode();
		n.setId(UUID.randomUUID());
		n.setNodeType(type);
		n.setStatus(RcdoNodeStatus.DRAFT);
		n.setTitle("Sample " + type.name());
		return n;
	}

	// -------------------------------------------------------------------------
	// POST /api/rcdo/nodes
	// -------------------------------------------------------------------------

	@Test
	void createNode_validRequest_returns201() throws Exception {
		RcdoNode saved = sampleNode(RcdoNodeType.RALLY_CRY);
		when(rcdoService.createNode(any(), any())).thenReturn(saved);

		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.RALLY_CRY, null, "Rally Cry 1", null, null,
				null);

		mockMvc.perform(post("/api/rcdo/nodes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.nodeType").value("RALLY_CRY")).andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	void createNode_missingTitle_returns400() throws Exception {
		// title is @NotBlank so Bean Validation rejects it before service
		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.RALLY_CRY, null, "", null, null, null);

		mockMvc.perform(post("/api/rcdo/nodes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest());
	}

	@Test
	void createNode_serviceThrowsValidation_returns400() throws Exception {
		when(rcdoService.createNode(any(), any()))
				.thenThrow(new RcdoValidationException("Rally Cry must have no parent"));

		CreateRcdoNodeRequest req = new CreateRcdoNodeRequest(RcdoNodeType.RALLY_CRY, UUID.randomUUID(), "RC", null,
				null, null);

		mockMvc.perform(post("/api/rcdo/nodes").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	// -------------------------------------------------------------------------
	// GET /api/rcdo/nodes
	// -------------------------------------------------------------------------

	@Test
	void listNodes_returns200WithList() throws Exception {
		RcdoNode n = sampleNode(RcdoNodeType.OUTCOME);
		when(rcdoService.listNodes(any(), any(), any(), any())).thenReturn(List.of(n));

		mockMvc.perform(get("/api/rcdo/nodes")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nodeType").value("OUTCOME"));
	}

	// -------------------------------------------------------------------------
	// GET /api/rcdo/nodes/{id}
	// -------------------------------------------------------------------------

	@Test
	void getNode_exists_returns200() throws Exception {
		UUID id = UUID.randomUUID();
		RcdoNode n = sampleNode(RcdoNodeType.OUTCOME);
		n.setId(id);
		RcdoNodeWithPathResponse resp = new RcdoNodeWithPathResponse(RcdoNodeResponse.from(n), List.of());
		when(rcdoService.getNodeWithPath(eq(id))).thenReturn(resp);

		mockMvc.perform(get("/api/rcdo/nodes/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.node.nodeType").value("OUTCOME"));
	}

	@Test
	void getNode_notFound_returns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(rcdoService.getNodeWithPath(eq(id)))
				.thenThrow(new ResourceNotFoundException("RCDO node not found: " + id));

		mockMvc.perform(get("/api/rcdo/nodes/" + id)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").exists());
	}

	// -------------------------------------------------------------------------
	// PUT /api/rcdo/nodes/{id}
	// -------------------------------------------------------------------------

	@Test
	void updateNode_returns200() throws Exception {
		UUID id = UUID.randomUUID();
		RcdoNode updated = sampleNode(RcdoNodeType.OUTCOME);
		updated.setId(id);
		updated.setTitle("Updated Title");
		when(rcdoService.updateNode(eq(id), any(), any())).thenReturn(updated);

		UpdateRcdoNodeRequest req = new UpdateRcdoNodeRequest("Updated Title", null, null, null);
		mockMvc.perform(put("/api/rcdo/nodes/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Updated Title"));
	}

	@Test
	void updateNode_blankTitle_returns400() throws Exception {
		UUID id = UUID.randomUUID();
		UpdateRcdoNodeRequest req = new UpdateRcdoNodeRequest("   ", null, null, null);

		mockMvc.perform(put("/api/rcdo/nodes/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	// -------------------------------------------------------------------------
	// POST /api/rcdo/nodes/{id}/archive
	// -------------------------------------------------------------------------

	@Test
	void archiveNode_returns200() throws Exception {
		UUID id = UUID.randomUUID();
		RcdoNode archived = sampleNode(RcdoNodeType.OUTCOME);
		archived.setId(id);
		archived.setStatus(RcdoNodeStatus.ARCHIVED);
		when(rcdoService.archiveNode(eq(id), any())).thenReturn(archived);

		mockMvc.perform(post("/api/rcdo/nodes/" + id + "/archive")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void archiveNode_withActiveChildren_returns400() throws Exception {
		UUID id = UUID.randomUUID();
		when(rcdoService.archiveNode(eq(id), any()))
				.thenThrow(new RcdoValidationException("Cannot archive node with non-archived children"));

		mockMvc.perform(post("/api/rcdo/nodes/" + id + "/archive")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	// -------------------------------------------------------------------------
	// GET /api/rcdo/tree
	// -------------------------------------------------------------------------

	@Test
	void getTree_returns200() throws Exception {
		when(rcdoService.getTree()).thenReturn(List.of());
		mockMvc.perform(get("/api/rcdo/tree")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
	}
}
