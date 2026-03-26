package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.provider.AiContext;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.ai.service.AiSuggestionService;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.TeamMembership;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.CarryForwardReason;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.AiSuggestionRepository;
import com.weeklycommit.domain.repository.CarryForwardLinkRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link InsightGenerationService}.
 *
 * <p>
 * All upstream services are mocked so no real HTTP / DB calls are made.
 */
@ExtendWith(MockitoExtension.class)
class InsightGenerationServiceTest {

	private static final UUID TEAM_ID = UUID.randomUUID();
	private static final UUID ORG_ID = UUID.randomUUID();
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID PLAN_ID = UUID.randomUUID();
	private static final LocalDate WEEK = LocalDate.of(2025, 1, 6);

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private PineconeClient pineconeClient;

	@Mock
	private EmbeddingService embeddingService;

	@Mock
	private AiProviderRegistry aiProviderRegistry;

	@Mock
	private AiSuggestionService aiSuggestionService;

	@Mock
	private AiSuggestionRepository suggestionRepo;

	@Mock
	private TeamRepository teamRepo;

	@Mock
	private TeamMembershipRepository membershipRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private CarryForwardLinkRepository carryForwardLinkRepo;

	@Mock
	private ScopeChangeEventRepository scopeChangeRepo;

	private InsightGenerationService service;

	@BeforeEach
	void setUp() {
		service = new InsightGenerationService(pineconeClient, embeddingService, aiProviderRegistry,
				aiSuggestionService, suggestionRepo, teamRepo, membershipRepo, planRepo, commitRepo,
				carryForwardLinkRepo, scopeChangeRepo, objectMapper);
	}

	// ── Availability guards ───────────────────────────────────────────────

	@Test
	void generateTeamInsights_pineconeUnavailable_skipsGracefully() {
		when(pineconeClient.isAvailable()).thenReturn(false);

		service.generateTeamInsights(TEAM_ID, WEEK);

		verifyNoInteractions(aiProviderRegistry, aiSuggestionService);
	}

	@Test
	void generateTeamInsights_embeddingUnavailable_skipsGracefully() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(false);

		service.generateTeamInsights(TEAM_ID, WEEK);

		verifyNoInteractions(aiProviderRegistry, aiSuggestionService);
	}

	@Test
	void generatePersonalInsights_pineconeUnavailable_skipsGracefully() {
		when(pineconeClient.isAvailable()).thenReturn(false);

		service.generatePersonalInsights(PLAN_ID);

		verifyNoInteractions(aiProviderRegistry, aiSuggestionService);
	}

	// ── Team insight generation ───────────────────────────────────────────

	@Test
	void generateTeamInsights_storesOneRowPerInsight() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		when(planRepo.findByTeamIdAndWeekStartDate(TEAM_ID, WEEK)).thenReturn(List.of());
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		String twoInsightPayload = twoInsightPayload();
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_TEAM_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, twoInsightPayload, "rationale", 0.85, "stub-v1"));
		AiSuggestion stored = aiSuggestion(UUID.randomUUID());
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(stored);

		service.generateTeamInsights(TEAM_ID, WEEK);

		// Two insights → two storeSuggestion calls
		verify(aiSuggestionService, times(2)).storeSuggestion(eq(AiContext.TYPE_TEAM_INSIGHT), isNull(), isNull(),
				isNull(), anyString(), any(AiSuggestionResult.class), eq(TEAM_ID), eq(WEEK));
	}

	@Test
	void generateTeamInsights_passesTeamIdAndWeekStartDate() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		when(planRepo.findByTeamIdAndWeekStartDate(TEAM_ID, WEEK)).thenReturn(List.of());
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_TEAM_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(aiSuggestion(UUID.randomUUID()));

		service.generateTeamInsights(TEAM_ID, WEEK);

		ArgumentCaptor<UUID> teamIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<LocalDate> weekCaptor = ArgumentCaptor.forClass(LocalDate.class);
		verify(aiSuggestionService).storeSuggestion(anyString(), any(), any(), any(), anyString(),
				any(AiSuggestionResult.class), teamIdCaptor.capture(), weekCaptor.capture());
		assertThat(teamIdCaptor.getValue()).isEqualTo(TEAM_ID);
		assertThat(weekCaptor.getValue()).isEqualTo(WEEK);
	}

	@Test
	void generateTeamInsights_contextIncludesMembersCommitsScopeChangesAndCarryForwards() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		WeeklyPlan plan = buildPlan();
		WeeklyCommit commit = buildCommit();
		ScopeChangeEvent scopeChange = buildScopeChange(commit.getId());
		CarryForwardLink carryForward = buildCarryForward(commit.getId());
		when(membershipRepo.findByTeamId(TEAM_ID)).thenReturn(List.of(buildMembership()));
		when(planRepo.findByTeamIdAndWeekStartDate(TEAM_ID, WEEK)).thenReturn(List.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(PLAN_ID)).thenReturn(List.of(commit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(PLAN_ID)).thenReturn(List.of(scopeChange));
		when(carryForwardLinkRepo.findBySourceCommitId(commit.getId())).thenReturn(List.of(carryForward));
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_TEAM_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(aiSuggestion(UUID.randomUUID()));

		service.generateTeamInsights(TEAM_ID, WEEK);

		ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
		verify(aiSuggestionService).storeSuggestion(anyString(), any(), any(), any(), contextCaptor.capture(), any(),
				any(), any());
		assertThat(contextCaptor.getValue()).contains("teamMemberIds").contains("commits").contains("scopeChanges")
				.contains("carryForwards").contains(commit.getId().toString()).contains(scopeChange.getId().toString())
				.contains(carryForward.getId().toString());
	}

	@Test
	void generateTeamInsights_llmUnavailable_storesNothing() {
		setUpAvailableServices();
		setUpTeam();
		when(planRepo.findByTeamIdAndWeekStartDate(TEAM_ID, WEEK)).thenReturn(List.of());
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_TEAM_INSIGHT)))
				.thenReturn(AiSuggestionResult.unavailable());

		service.generateTeamInsights(TEAM_ID, WEEK);

		verify(aiSuggestionService, never()).storeSuggestion(anyString(), any(), any(), any(), anyString(), any(),
				any(), any());
	}

	// ── Personal insight generation ───────────────────────────────────────

	@Test
	void generatePersonalInsights_storesWithPlanIdUserIdTeamIdWeekStartDate() throws Exception {
		setUpAvailableServices();
		setUpPlan();
		when(commitRepo.findByPlanIdOrderByPriorityOrder(PLAN_ID)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(PLAN_ID)).thenReturn(List.of());
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		setUpTeam();
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_PERSONAL_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(aiSuggestion(UUID.randomUUID()));

		service.generatePersonalInsights(PLAN_ID);

		ArgumentCaptor<UUID> planIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<UUID> teamIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<LocalDate> weekCaptor = ArgumentCaptor.forClass(LocalDate.class);
		verify(aiSuggestionService).storeSuggestion(eq(AiContext.TYPE_PERSONAL_INSIGHT), userIdCaptor.capture(),
				planIdCaptor.capture(), isNull(), anyString(), any(AiSuggestionResult.class), teamIdCaptor.capture(),
				weekCaptor.capture());

		assertThat(planIdCaptor.getValue()).isEqualTo(PLAN_ID);
		assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
		assertThat(teamIdCaptor.getValue()).isEqualTo(TEAM_ID);
		assertThat(weekCaptor.getValue()).isEqualTo(WEEK);
	}

	@Test
	void generatePersonalInsights_contextIncludesCommitsScopeChangesAndCarryForwards() throws Exception {
		setUpAvailableServices();
		setUpPlan();
		setUpTeam();
		WeeklyCommit commit = buildCommit();
		ScopeChangeEvent scopeChange = buildScopeChange(commit.getId());
		CarryForwardLink carryForward = buildCarryForward(commit.getId());
		when(commitRepo.findByPlanIdOrderByPriorityOrder(PLAN_ID)).thenReturn(List.of(commit));
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(PLAN_ID)).thenReturn(List.of(scopeChange));
		when(carryForwardLinkRepo.findBySourceCommitId(commit.getId())).thenReturn(List.of(carryForward));
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_PERSONAL_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(aiSuggestion(UUID.randomUUID()));

		service.generatePersonalInsights(PLAN_ID);

		ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
		verify(aiSuggestionService).storeSuggestion(anyString(), any(), any(), any(), contextCaptor.capture(), any(),
				any(), any());
		assertThat(contextCaptor.getValue()).contains("commits").contains("scopeChanges").contains("carryForwards")
				.contains(commit.getId().toString()).contains(scopeChange.getId().toString())
				.contains(carryForward.getId().toString());
	}

	@Test
	void generatePersonalInsights_planNotFound_skipsGracefully() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(true);
		when(planRepo.findById(PLAN_ID)).thenReturn(Optional.empty());

		service.generatePersonalInsights(PLAN_ID);

		verifyNoInteractions(aiProviderRegistry, aiSuggestionService);
	}

	// ── Idempotency ───────────────────────────────────────────────────────

	@Test
	void dailySweepReindex_skipsPersonalInsightsIfAlreadyExist() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		when(teamRepo.findAll()).thenReturn(List.of(buildTeam()));

		// Team insight generation: returns one LOCKED plan for the week
		when(planRepo.findByTeamIdAndWeekStartDate(eq(TEAM_ID), any())).thenReturn(List.of(buildPlan()));
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_TEAM_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(aiSuggestion(UUID.randomUUID()));

		// Personal insights already exist for this plan → idempotency check returns
		// non-empty
		when(suggestionRepo.findByPlanIdAndSuggestionType(PLAN_ID, AiContext.TYPE_PERSONAL_INSIGHT))
				.thenReturn(List.of(aiSuggestion(UUID.randomUUID())));

		service.generateDailyInsights();

		// Personal insight generation should NOT be called since insights already exist
		verify(aiProviderRegistry, never()).generateSuggestion(typeContextMatcher(AiContext.TYPE_PERSONAL_INSIGHT));
	}

	@Test
	void dailySweepReindex_generatesPersonalInsightsWhenNoneExist() throws Exception {
		setUpAvailableServices();
		setUpTeam();
		when(teamRepo.findAll()).thenReturn(List.of(buildTeam()));

		when(planRepo.findByTeamIdAndWeekStartDate(eq(TEAM_ID), any())).thenReturn(List.of(buildPlan()));
		when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
		when(pineconeClient.query(anyString(), any(), anyInt(), any())).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_TEAM_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));
		when(aiSuggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any(), any(), any()))
				.thenReturn(aiSuggestion(UUID.randomUUID()));

		// No personal insights exist yet
		when(suggestionRepo.findByPlanIdAndSuggestionType(PLAN_ID, AiContext.TYPE_PERSONAL_INSIGHT))
				.thenReturn(List.of());
		when(planRepo.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(PLAN_ID)).thenReturn(List.of());
		when(scopeChangeRepo.findByPlanIdOrderByCreatedAtAsc(PLAN_ID)).thenReturn(List.of());
		when(aiProviderRegistry.generateSuggestion(typeContextMatcher(AiContext.TYPE_PERSONAL_INSIGHT)))
				.thenReturn(new AiSuggestionResult(true, singleInsightPayload(), "rationale", 0.85, "stub-v1"));

		service.generateDailyInsights();

		// Personal insight generation IS called for the unlocked plan
		verify(aiProviderRegistry).generateSuggestion(typeContextMatcher(AiContext.TYPE_PERSONAL_INSIGHT));
	}

	// ── generatePersonalInsightsAsync ─────────────────────────────────────

	@Test
	void generatePersonalInsightsAsync_callsPersonalInsightsWithoutThrowing() {
		when(pineconeClient.isAvailable()).thenReturn(false);

		// Should not throw even though inner call degrades gracefully
		service.generatePersonalInsightsAsync(PLAN_ID);
		// Assertion: no exception = test passes
	}

	// ── Helpers ───────────────────────────────────────────────────────────

	private void setUpAvailableServices() {
		when(pineconeClient.isAvailable()).thenReturn(true);
		when(embeddingService.isAvailable()).thenReturn(true);
	}

	private void setUpTeam() {
		when(teamRepo.findById(TEAM_ID)).thenReturn(Optional.of(buildTeam()));
	}

	private void setUpPlan() {
		when(planRepo.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
	}

	private Team buildTeam() {
		Team team = new Team();
		team.setId(TEAM_ID);
		team.setOrganizationId(ORG_ID);
		return team;
	}

	private WeeklyPlan buildPlan() {
		WeeklyPlan plan = new WeeklyPlan();
		plan.setId(PLAN_ID);
		plan.setOwnerUserId(USER_ID);
		plan.setTeamId(TEAM_ID);
		plan.setWeekStartDate(WEEK);
		plan.setState(PlanState.LOCKED);
		return plan;
	}

	private TeamMembership buildMembership() {
		TeamMembership membership = new TeamMembership();
		membership.setTeamId(TEAM_ID);
		membership.setUserId(USER_ID);
		membership.setRole("MEMBER");
		return membership;
	}

	private WeeklyCommit buildCommit() {
		WeeklyCommit commit = new WeeklyCommit();
		commit.setId(UUID.randomUUID());
		commit.setPlanId(PLAN_ID);
		commit.setOwnerUserId(USER_ID);
		commit.setTitle("Stabilize ingestion pipeline");
		commit.setChessPiece(ChessPiece.QUEEN);
		commit.setEstimatePoints(5);
		commit.setPriorityOrder(1);
		return commit;
	}

	private ScopeChangeEvent buildScopeChange(UUID commitId) {
		ScopeChangeEvent event = new ScopeChangeEvent();
		event.setId(UUID.randomUUID());
		event.setPlanId(PLAN_ID);
		event.setCommitId(commitId);
		event.setCategory(ScopeChangeCategory.ESTIMATE_CHANGED);
		event.setChangedByUserId(USER_ID);
		event.setReason("Expanded due to dependency work");
		return event;
	}

	private CarryForwardLink buildCarryForward(UUID sourceCommitId) {
		CarryForwardLink link = new CarryForwardLink();
		link.setId(UUID.randomUUID());
		link.setSourceCommitId(sourceCommitId);
		link.setTargetCommitId(UUID.randomUUID());
		link.setReason(CarryForwardReason.STILL_IN_PROGRESS);
		link.setReasonNotes("Waiting on upstream API");
		return link;
	}

	private AiSuggestion aiSuggestion(UUID id) {
		AiSuggestion s = new AiSuggestion();
		s.setId(id);
		return s;
	}

	private String singleInsightPayload() throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of("insights",
				java.util.List.of(java.util.Map.of("insightText", "Team shows good delivery consistency.", "severity",
						"LOW", "sourceEntityIds", java.util.List.of(), "actionSuggestion", "Keep it up."))));
	}

	private String twoInsightPayload() throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of("insights",
				java.util.List.of(
						java.util.Map.of("insightText", "Insight 1", "severity", "LOW", "sourceEntityIds",
								java.util.List.of(), "actionSuggestion", "Action 1"),
						java.util.Map.of("insightText", "Insight 2", "severity", "MEDIUM", "sourceEntityIds",
								java.util.List.of(), "actionSuggestion", "Action 2"))));
	}

	/** Matches an {@link AiContext} by its suggestion type. */
	private static AiContext typeContextMatcher(String type) {
		return org.mockito.ArgumentMatchers.argThat(ctx -> ctx != null && type.equals(ctx.suggestionType()));
	}
}
