package com.weeklycommit.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.ai.rag.SemanticQueryService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Unit tests for {@link AiStreamController}.
 *
 * <p>
 * Uses {@link WebMvcTest} with a synchronous {@code taskExecutor} so the SSE
 * task runs on the calling thread and events are available immediately for
 * {@link MockMvc#perform(org.springframework.test.web.servlet.RequestBuilder)}
 * assertions via {@code asyncDispatch}.
 */
@WebMvcTest(AiStreamController.class)
@Import(AiStreamControllerTest.SyncExecutorConfig.class)
class AiStreamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private SemanticQueryService semanticQueryService;

	// ── Test infrastructure: synchronous executor ─────────────────────────

	/**
	 * Replaces the async {@code taskExecutor} with an inline executor that runs
	 * tasks on the calling thread. This lets MockMvc observe all SSE events
	 * deterministically without race conditions.
	 */
	@TestConfiguration
	static class SyncExecutorConfig {

		@Bean("taskExecutor")
		Executor taskExecutor() {
			return Runnable::run;
		}
	}

	// ── Missing / invalid parameters → 400 ───────────────────────────────

	@Test
	void streamEndpoint_missingQuestion_returns400() throws Exception {
		mockMvc.perform(get("/api/ai/rag/stream").param("teamId", UUID.randomUUID().toString()).param("userId",
				UUID.randomUUID().toString())).andExpect(status().isBadRequest());
	}

	@Test
	void streamEndpoint_missingTeamId_returns400() throws Exception {
		mockMvc.perform(get("/api/ai/rag/stream").param("question", "What happened last week?").param("userId",
				UUID.randomUUID().toString())).andExpect(status().isBadRequest());
	}

	@Test
	void streamEndpoint_missingUserId_returns400() throws Exception {
		mockMvc.perform(get("/api/ai/rag/stream").param("question", "What happened?").param("teamId",
				UUID.randomUUID().toString())).andExpect(status().isBadRequest());
	}

	// ── Successful stream: correct event types in order ───────────────────

	@Test
	void streamEndpoint_successfulQuery_sendsCorrectEventTypes() throws Exception {
		UUID teamId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		SemanticQueryService.RagQueryResult result = new SemanticQueryService.RagQueryResult(true,
				"The team committed to shipping the new API.", List.of(), 0.85, UUID.randomUUID());
		when(semanticQueryService.query(any(), any(), any())).thenReturn(result);

		MvcResult mvcResult = mockMvc
				.perform(get("/api/ai/rag/stream").param("question", "What happened last week?")
						.param("teamId", teamId.toString()).param("userId", userId.toString()))
				.andExpect(request().asyncStarted()).andReturn();

		mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("event:delta")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("event:sources")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("event:confidence")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
	}

	@Test
	void streamEndpoint_successfulQuery_deltaEventsContainAnswerWords() throws Exception {
		UUID teamId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		SemanticQueryService.RagQueryResult result = new SemanticQueryService.RagQueryResult(true, "Hello world",
				List.of(), 0.9, UUID.randomUUID());
		when(semanticQueryService.query(any(), any(), any())).thenReturn(result);

		MvcResult mvcResult = mockMvc.perform(get("/api/ai/rag/stream").param("question", "test")
				.param("teamId", teamId.toString()).param("userId", userId.toString()))
				.andExpect(request().asyncStarted()).andReturn();

		String body = mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		// Delta events should contain parts of the answer text
		org.assertj.core.api.Assertions.assertThat(body).contains("event:delta").contains("Hello").contains("world");
	}

	// ── AI unavailable → error event ──────────────────────────────────────

	@Test
	void streamEndpoint_aiUnavailable_sendsErrorEvent() throws Exception {
		UUID teamId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(semanticQueryService.query(any(), any(), any()))
				.thenReturn(SemanticQueryService.RagQueryResult.unavailable());

		MvcResult mvcResult = mockMvc.perform(get("/api/ai/rag/stream").param("question", "test")
				.param("teamId", teamId.toString()).param("userId", userId.toString()))
				.andExpect(request().asyncStarted()).andReturn();

		mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")));
	}

	@Test
	void streamEndpoint_serviceThrowsException_sendsErrorEvent() throws Exception {
		UUID teamId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(semanticQueryService.query(any(), any(), any())).thenThrow(new RuntimeException("Upstream service down"));

		MvcResult mvcResult = mockMvc.perform(get("/api/ai/rag/stream").param("question", "test")
				.param("teamId", teamId.toString()).param("userId", userId.toString()))
				.andExpect(request().asyncStarted()).andReturn();

		mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")));
	}
}
