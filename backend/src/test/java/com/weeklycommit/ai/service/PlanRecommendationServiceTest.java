package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.RiskSignalResponse;
import com.weeklycommit.ai.dto.RiskSignalResponse.PlanRiskSignals;
import com.weeklycommit.ai.dto.WhatIfResponse;
import com.weeklycommit.ai.dto.WhatIfResponse.PlanSnapshot;
import com.weeklycommit.ai.dto.WhatIfResponse.RiskDelta;
import com.weeklycommit.ai.evidence.ConfidenceTierCalculator.ConfidenceTier;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.CalibrationService.CalibrationConfidenceTier;
import com.weeklycommit.ai.service.CalibrationService.CalibrationProfile;
import com.weeklycommit.ai.service.PlanRecommendationService.PlanRecommendation;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanRecommendationServiceTest {

	@Mock
	private RiskDetectionService riskDetectionService;

	@Mock
	private WhatIfService whatIfService;

	@Mock
	private CalibrationService calibrationService;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private AiSuggestionRepository suggestionRepo;

	@Mock
	private AiSuggestionService aiSuggestionService;

	private PlanRecommendationService service;
	private UUID planId;
	private UUID userId;

	@BeforeEach
	void setUp() {
		service = new PlanRecommendationService(riskDetectionService, whatIfService, calibrationService, commitRepo,
				suggestionRepo, aiSuggestionService, new ObjectMapper());
		planId = UUID.randomUUID();
		userId = UUID.randomUUID();
	}

	// -------------------------------------------------------------------------
	// OVERCOMMIT produces recommendation
	// -------------------------------------------------------------------------

	@Test
	void generateAndPersist_overcommitSignal_producesRecommendation() {
		// Given
		RiskSignalResponse signal = new RiskSignalResponse(UUID.randomUUID(), "OVERCOMMIT",
				"Planned 15 pts exceeds budget of 10 pts.", planId, null, Instant.now());
		when(riskDetectionService.getRiskSignals(planId, userId))
				.thenReturn(new PlanRiskSignals(true, planId, List.of(signal)));

		WeeklyCommit pawn = makeCommit(UUID.randomUUID(), ChessPiece.PAWN, 5, 1);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(pawn));

		CalibrationProfile calibration = new CalibrationProfile(0.8, Map.of(), 0.2, 20, Map.of(),
				CalibrationConfidenceTier.MEDIUM);
		when(calibrationService.getCalibration(userId)).thenReturn(calibration);

		WhatIfResponse whatIfResp = makeWhatIfResponse(-5, List.of("OVERCOMMIT"), List.of());
		when(whatIfService.simulate(any())).thenReturn(whatIfResp);

		UUID savedId = UUID.randomUUID();
		AiSuggestion saved = makeSavedSuggestion(savedId);
		when(aiSuggestionService.storeSuggestion(eq("PLAN_RECOMMENDATION"), eq(userId), eq(planId), isNull(),
				anyString(), any(AiSuggestionResult.class))).thenReturn(saved);

		// When
		List<PlanRecommendation> recs = service.generateAndPersistRecommendations(planId, userId);

		// Then
		assertThat(recs).hasSize(1);
		assertThat(recs.get(0).riskType()).isEqualTo("OVERCOMMIT");
		assertThat(recs.get(0).confidence()).isEqualTo(ConfidenceTier.HIGH);
		assertThat(recs.get(0).whatIfResult()).isEqualTo(whatIfResp);
		assertThat(recs.get(0).suggestionId()).isEqualTo(savedId);
		assertThat(recs.get(0).description()).isEqualTo("Planned 15 pts exceeds budget of 10 pts.");

		ArgumentCaptor<AiSuggestionResult> resultCaptor = ArgumentCaptor.forClass(AiSuggestionResult.class);
		verify(aiSuggestionService).storeSuggestion(eq("PLAN_RECOMMENDATION"), eq(userId), eq(planId), isNull(),
				anyString(), resultCaptor.capture());
		assertThat(resultCaptor.getValue().payload()).contains("whatIfResult").contains("capacityDelta");
	}

	// -------------------------------------------------------------------------
	// No risks → empty list
	// -------------------------------------------------------------------------

	@Test
	void generateAndPersist_noRiskSignals_returnsEmpty() {
		when(riskDetectionService.getRiskSignals(planId, userId))
				.thenReturn(new PlanRiskSignals(true, planId, List.of()));

		List<PlanRecommendation> recs = service.generateAndPersistRecommendations(planId, userId);

		assertThat(recs).isEmpty();
		verify(riskDetectionService).detectAndStoreRiskSignalsById(planId);
		verify(commitRepo, never()).findByPlanIdOrderByPriorityOrder(any());
	}

	// -------------------------------------------------------------------------
	// WhatIfService failure → partial recommendation (still persisted)
	// -------------------------------------------------------------------------

	@Test
	void generateAndPersist_whatIfFails_returnsPartialRecommendation() {
		RiskSignalResponse signal = new RiskSignalResponse(UUID.randomUUID(), "OVERCOMMIT", "Too many points.", planId,
				null, Instant.now());
		when(riskDetectionService.getRiskSignals(planId, userId))
				.thenReturn(new PlanRiskSignals(true, planId, List.of(signal)));

		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId))
				.thenReturn(List.of(makeCommit(UUID.randomUUID(), ChessPiece.PAWN, 3, 1)));
		when(calibrationService.getCalibration(userId))
				.thenReturn(new CalibrationProfile(0.8, Map.of(), 0.2, 20, Map.of(), CalibrationConfidenceTier.HIGH));
		when(whatIfService.simulate(any())).thenThrow(new RuntimeException("Simulation failed"));

		AiSuggestion saved = makeSavedSuggestion(UUID.randomUUID());
		when(aiSuggestionService.storeSuggestion(eq("PLAN_RECOMMENDATION"), eq(userId), eq(planId), isNull(),
				anyString(), any(AiSuggestionResult.class))).thenReturn(saved);

		// When
		List<PlanRecommendation> recs = service.generateAndPersistRecommendations(planId, userId);

		// Then: recommendation is still produced (partial) with null whatIfResult
		assertThat(recs).hasSize(1);
		assertThat(recs.get(0).whatIfResult()).isNull();
		assertThat(recs.get(0).riskType()).isEqualTo("OVERCOMMIT");
		// Still persisted so it gets a suggestionId
		verify(aiSuggestionService).storeSuggestion(eq("PLAN_RECOMMENDATION"), eq(userId), eq(planId), isNull(),
				anyString(), any(AiSuggestionResult.class));
	}

	// -------------------------------------------------------------------------
	// Multiple risks → multiple recommendations
	// -------------------------------------------------------------------------

	@Test
	void generateAndPersist_multipleSignals_generatesMultipleRecommendations() {
		UUID cfCommitId = UUID.randomUUID();
		List<RiskSignalResponse> signals = List.of(
				new RiskSignalResponse(UUID.randomUUID(), "OVERCOMMIT", "Too many points.", planId, null,
						Instant.now()),
				new RiskSignalResponse(UUID.randomUUID(), "REPEATED_CARRY_FORWARD", "Carry-forward streak.", planId,
						cfCommitId, Instant.now()));
		when(riskDetectionService.getRiskSignals(planId, userId))
				.thenReturn(new PlanRiskSignals(true, planId, signals));

		WeeklyCommit pawn = makeCommit(UUID.randomUUID(), ChessPiece.PAWN, 5, 2);
		WeeklyCommit cfCommit = makeCarryForwardCommit(cfCommitId, 3, 3);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(pawn, cfCommit));
		when(calibrationService.getCalibration(userId))
				.thenReturn(new CalibrationProfile(0.8, Map.of(), 0.2, 20, Map.of(), CalibrationConfidenceTier.MEDIUM));
		when(whatIfService.simulate(any())).thenReturn(makeWhatIfResponse(0, List.of(), List.of()));

		AiSuggestion saved1 = makeSavedSuggestion(UUID.randomUUID());
		AiSuggestion saved2 = makeSavedSuggestion(UUID.randomUUID());
		when(aiSuggestionService.storeSuggestion(any(), any(), any(), any(), anyString(), any())).thenReturn(saved1)
				.thenReturn(saved2);

		// When
		List<PlanRecommendation> recs = service.generateAndPersistRecommendations(planId, userId);

		// Then
		assertThat(recs).hasSize(2);
		assertThat(recs.stream().map(PlanRecommendation::riskType)).containsExactlyInAnyOrder("OVERCOMMIT",
				"REPEATED_CARRY_FORWARD");
		// OVERCOMMIT → HIGH; REPEATED_CARRY_FORWARD with MEDIUM calibration → MEDIUM
		PlanRecommendation overcommit = recs.stream().filter(r -> "OVERCOMMIT".equals(r.riskType())).findFirst()
				.orElseThrow();
		PlanRecommendation carryFwd = recs.stream().filter(r -> "REPEATED_CARRY_FORWARD".equals(r.riskType()))
				.findFirst().orElseThrow();
		assertThat(overcommit.confidence()).isEqualTo(ConfidenceTier.HIGH);
		assertThat(carryFwd.confidence()).isEqualTo(ConfidenceTier.MEDIUM);
	}

	// -------------------------------------------------------------------------
	// getRecommendations returns stable IDs without regeneration
	// -------------------------------------------------------------------------

	@Test
	void getRecommendations_returnsPersistedRowsWithStableIds() throws Exception {
		UUID suggId = UUID.randomUUID();
		AiSuggestion row = makeSavedSuggestion(suggId);
		WhatIfResponse persistedWhatIf = makeWhatIfResponse(-5, List.of("OVERCOMMIT"), List.of());
		row.setSuggestionPayload(new ObjectMapper().writeValueAsString(Map.of("riskType", "OVERCOMMIT", "description",
				"Too many points.", "suggestedAction", "Remove a commit.", "whatIfResult", persistedWhatIf, "narrative",
				"Narrative text.", "confidence", "HIGH")));
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "PLAN_RECOMMENDATION")).thenReturn(List.of(row));

		// When
		List<PlanRecommendation> recs = service.getRecommendations(planId);

		// Then
		assertThat(recs).hasSize(1);
		assertThat(recs.get(0).suggestionId()).isEqualTo(suggId);
		assertThat(recs.get(0).riskType()).isEqualTo("OVERCOMMIT");
		assertThat(recs.get(0).confidence()).isEqualTo(ConfidenceTier.HIGH);
		assertThat(recs.get(0).narrative()).isEqualTo("Narrative text.");
		assertThat(recs.get(0).whatIfResult()).isEqualTo(persistedWhatIf);

		// Verify no generation occurs
		verify(riskDetectionService, never()).getRiskSignals(any(), any());
		verify(whatIfService, never()).simulate(any());
		verify(aiSuggestionService, never()).storeSuggestion(any(), any(), any(), any(), any(), any());
	}

	// -------------------------------------------------------------------------
	// generateAndPersistRecommendations deletes stale rows first
	// -------------------------------------------------------------------------

	@Test
	void generateAndPersist_deletesStaleRowsBeforeGenerating() {
		AiSuggestion stale = makeSavedSuggestion(UUID.randomUUID());
		when(suggestionRepo.findByPlanIdAndSuggestionType(planId, "PLAN_RECOMMENDATION")).thenReturn(List.of(stale));
		// No risk signals → early return after delete
		when(riskDetectionService.getRiskSignals(planId, userId))
				.thenReturn(new PlanRiskSignals(true, planId, List.of()));

		service.generateAndPersistRecommendations(planId, userId);

		verify(suggestionRepo).deleteAll(List.of(stale));
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit makeCommit(UUID id, ChessPiece piece, int points, int priority) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(id);
		c.setChessPiece(piece);
		c.setEstimatePoints(points);
		c.setPriorityOrder(priority);
		c.setTitle("Commit " + id.toString().substring(0, 8));
		c.setCarryForwardStreak(0);
		return c;
	}

	private WeeklyCommit makeCarryForwardCommit(UUID id, int points, int priority) {
		WeeklyCommit c = makeCommit(id, ChessPiece.PAWN, points, priority);
		c.setCarryForwardStreak(3); // >= threshold of 2
		return c;
	}

	private WhatIfResponse makeWhatIfResponse(int delta, List<String> resolved, List<String> newRisks) {
		return new WhatIfResponse(true, new PlanSnapshot(15, 10, List.of("OVERCOMMIT"), Map.of()),
				new PlanSnapshot(10, 10, List.of(), Map.of()), delta, List.of(), new RiskDelta(newRisks, resolved),
				"Removing the commit would resolve overcommit.", "Remove it.");
	}

	private AiSuggestion makeSavedSuggestion(UUID id) {
		AiSuggestion s = new AiSuggestion();
		s.setId(id);
		s.setSuggestionType("PLAN_RECOMMENDATION");
		s.setPrompt("{}");
		s.setRationale("recommendation rationale");
		s.setSuggestionPayload("{\"riskType\":\"OVERCOMMIT\",\"description\":\"desc.\","
				+ "\"suggestedAction\":\"act.\",\"narrative\":\"narr.\",\"confidence\":\"HIGH\"}");
		s.setModelVersion("plan-recommendation-v1");
		return s;
	}
}
