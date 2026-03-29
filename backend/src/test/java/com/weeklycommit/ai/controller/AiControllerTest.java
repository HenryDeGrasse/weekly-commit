package com.weeklycommit.ai.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.ai.dto.RiskSignalResponse;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.rag.SemanticQueryService;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.ai.service.CommitDraftAssistService;
import com.weeklycommit.ai.service.CommitLintService;
import com.weeklycommit.ai.service.ManagerAiSummaryService;
import com.weeklycommit.ai.service.RcdoSuggestService;
import com.weeklycommit.ai.service.ReconcileAssistService;
import com.weeklycommit.ai.service.RiskDetectionService;
import com.weeklycommit.ai.service.CalibrationService;
import com.weeklycommit.ai.service.WhatIfService;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiController.class)
class AiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CommitDraftAssistService draftAssistService;

	@MockBean
	private CommitLintService lintService;

	@MockBean
	private RcdoSuggestService rcdoSuggestService;

	@MockBean
	private RiskDetectionService riskDetectionService;

	@MockBean
	private ReconcileAssistService reconcileAssistService;

	@MockBean
	private ManagerAiSummaryService managerSummaryService;

	@MockBean
	private AiSuggestionService suggestionService;

	@MockBean
	private AiProviderRegistry providerRegistry;

	@MockBean
	private com.weeklycommit.ai.rag.SemanticIndexService semanticIndexService;

	@MockBean
	private SemanticQueryService semanticQueryService;

	@MockBean
	private AiSuggestionRepository suggestionRepo;

	@MockBean
	private com.weeklycommit.ai.evidence.StructuredEvidenceService evidenceService;

	@MockBean
	private WhatIfService whatIfService;

	@MockBean
	private CalibrationService calibrationService;

	@Test
	void getRiskSignals_requiresActorHeader() throws Exception {
		mockMvc.perform(get("/api/plans/" + UUID.randomUUID() + "/risk-signals")).andExpect(status().isBadRequest());
	}

	@Test
	void getRiskSignals_usesActorHeaderForPrivacyCheckedLookup() throws Exception {
		UUID planId = UUID.randomUUID();
		UUID callerId = UUID.randomUUID();
		PlanRiskSignals response = new PlanRiskSignals(true, planId, List
				.of(new RiskSignalResponse(UUID.randomUUID(), "OVERCOMMIT", "rationale", planId, null, Instant.now())));
		when(riskDetectionService.getRiskSignals(eq(planId), eq(callerId))).thenReturn(response);

		mockMvc.perform(get("/api/plans/" + planId + "/risk-signals").header("X-Actor-User-Id", callerId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.planId").value(planId.toString()))
				.andExpect(jsonPath("$.signals[0].signalType").value("OVERCOMMIT"));

		verify(riskDetectionService).getRiskSignals(planId, callerId);
	}
}
