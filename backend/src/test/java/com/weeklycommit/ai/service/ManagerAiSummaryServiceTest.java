package com.weeklycommit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.ai.dto.ManagerAiSummaryResponse;
import com.weeklycommit.ai.provider.AiProviderRegistry;
import com.weeklycommit.ai.provider.AiSuggestionResult;
import com.weeklycommit.domain.entity.AiSuggestion;
import com.weeklycommit.domain.entity.Team;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.UserRole;
import com.weeklycommit.domain.repository.ManagerReviewExceptionRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.TeamMembershipRepository;
import com.weeklycommit.domain.repository.TeamRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.domain.repository.WorkItemRepository;
import com.weeklycommit.team.exception.AccessDeniedException;
import com.weeklycommit.team.service.AuthorizationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerAiSummaryServiceTest {

	@Mock
	private AiProviderRegistry registry;

	@Mock
	private AiSuggestionService suggestionService;

	@Mock
	private TeamRepository teamRepo;

	@Mock
	private TeamMembershipRepository membershipRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WorkItemRepository workItemRepo;

	@Mock
	private RcdoNodeRepository rcdoNodeRepo;

	@Mock
	private ManagerReviewExceptionRepository exceptionRepo;

	@Mock
	private AuthorizationService authService;

	private ManagerAiSummaryService service;

	private UUID teamId;
	private UUID managerId;
	private LocalDate weekStart;

	@BeforeEach
	void setUp() {
		service = new ManagerAiSummaryService(registry, suggestionService, teamRepo, membershipRepo, planRepo,
				commitRepo, workItemRepo, rcdoNodeRepo, exceptionRepo, authService, new ObjectMapper());
		teamId = UUID.randomUUID();
		managerId = UUID.randomUUID();
		weekStart = LocalDate.of(2026, 3, 23);
	}

	// -------------------------------------------------------------------------
	// Privacy: only managers may see AI summaries
	// -------------------------------------------------------------------------

	@Test
	void getSummary_whenCallerIsIC_throwsAccessDenied() {
		Team team = new Team();
		team.setId(teamId);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.IC);

		assertThatThrownBy(() -> service.getSummary(teamId, weekStart, managerId))
				.isInstanceOf(AccessDeniedException.class).hasMessageContaining("Only managers");
	}

	@Test
	void getSummary_whenCallerIsManager_allowsAccess() {
		Team team = new Team();
		team.setId(teamId);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(registry.isAiEnabled()).thenReturn(false);

		ManagerAiSummaryResponse resp = service.getSummary(teamId, weekStart, managerId);

		// AI disabled → unavailable, but no exception thrown
		assertThat(resp.aiAvailable()).isFalse();
	}

	// -------------------------------------------------------------------------
	// AI disabled
	// -------------------------------------------------------------------------

	@Test
	void getSummary_whenAiDisabled_returnsUnavailable() {
		Team team = new Team();
		team.setId(teamId);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(registry.isAiEnabled()).thenReturn(false);

		ManagerAiSummaryResponse resp = service.getSummary(teamId, weekStart, managerId);

		assertThat(resp.aiAvailable()).isFalse();
		assertThat(resp.teamId()).isEqualTo(teamId);
		assertThat(resp.weekStart()).isEqualTo(weekStart);
		verify(registry, never()).generateSuggestion(any());
	}

	// -------------------------------------------------------------------------
	// Summary cites sources
	// -------------------------------------------------------------------------

	@Test
	void getSummary_citesUnresolvedExceptions() {
		Team team = new Team();
		team.setId(teamId);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(registry.isAiEnabled()).thenReturn(true);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of());

		UUID exceptionId = UUID.randomUUID();
		com.weeklycommit.domain.entity.ManagerReviewException ex = new com.weeklycommit.domain.entity.ManagerReviewException();
		ex.setId(exceptionId);
		when(exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false)).thenReturn(List.of(ex));

		String payload = "{\"summaryText\":\"Team has issues.\",\"topRcdoBranches\":[],"
				+ "\"carryForwardPatterns\":[],\"criticalBlockedItemIds\":[]}";
		when(registry.generateSuggestion(any()))
				.thenReturn(new AiSuggestionResult(true, payload, "rationale", 0.9, "stub-v1"));

		AiSuggestion stored = new AiSuggestion();
		stored.setId(UUID.randomUUID());
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		ManagerAiSummaryResponse resp = service.getSummary(teamId, weekStart, managerId);

		assertThat(resp.aiAvailable()).isTrue();
		assertThat(resp.unresolvedExceptionIds()).containsExactly(exceptionId);
	}

	@Test
	void getSummary_citesCarryForwardPatterns() {
		Team team = new Team();
		team.setId(teamId);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.MANAGER);
		when(registry.isAiEnabled()).thenReturn(true);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of());

		WeeklyPlan plan = new WeeklyPlan();
		plan.setId(UUID.randomUUID());
		plan.setOwnerUserId(UUID.randomUUID());
		plan.setWeekStartDate(weekStart);
		plan.setState(PlanState.LOCKED);
		plan.setCapacityBudgetPoints(10);
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of(plan));

		// Build a commit with carryForwardStreak=2
		com.weeklycommit.domain.entity.WeeklyCommit commit = new com.weeklycommit.domain.entity.WeeklyCommit();
		commit.setId(UUID.randomUUID());
		commit.setPlanId(plan.getId());
		commit.setOwnerUserId(plan.getOwnerUserId());
		commit.setTitle("Old commit");
		commit.setChessPiece(com.weeklycommit.domain.enums.ChessPiece.ROOK);
		commit.setCarryForwardStreak(2);
		commit.setPriorityOrder(1);
		when(commitRepo.findByPlanIdOrderByPriorityOrder(plan.getId())).thenReturn(List.of(commit));

		when(exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false)).thenReturn(List.of());

		String payload = "{\"summaryText\":\"Carry forwards noted.\",\"topRcdoBranches\":[],"
				+ "\"carryForwardPatterns\":[],\"criticalBlockedItemIds\":[]}";
		when(registry.generateSuggestion(any()))
				.thenReturn(new AiSuggestionResult(true, payload, "rationale", 0.9, "stub-v1"));

		AiSuggestion stored = new AiSuggestion();
		stored.setId(UUID.randomUUID());
		when(suggestionService.storeSuggestion(anyString(), any(), any(), any(), anyString(), any()))
				.thenReturn(stored);

		ManagerAiSummaryResponse resp = service.getSummary(teamId, weekStart, managerId);

		assertThat(resp.aiAvailable()).isTrue();
		// carryForwardPatterns should list the carry-forward info
		assertThat(resp.carryForwardPatterns()).isNotEmpty();
		assertThat(resp.carryForwardPatterns().get(0)).contains("1 commit");
	}

	@Test
	void getSummary_whenProviderUnavailable_returnsUnavailable() {
		Team team = new Team();
		team.setId(teamId);
		when(teamRepo.findById(teamId)).thenReturn(Optional.of(team));
		when(authService.getCallerRole(managerId)).thenReturn(UserRole.ADMIN);
		when(registry.isAiEnabled()).thenReturn(true);
		when(membershipRepo.findByTeamId(teamId)).thenReturn(List.of());
		when(planRepo.findByTeamIdAndWeekStartDate(teamId, weekStart)).thenReturn(List.of());
		when(exceptionRepo.findByTeamIdAndWeekStartDateAndResolved(teamId, weekStart, false)).thenReturn(List.of());
		when(registry.generateSuggestion(any())).thenReturn(AiSuggestionResult.unavailable());

		ManagerAiSummaryResponse resp = service.getSummary(teamId, weekStart, managerId);

		assertThat(resp.aiAvailable()).isFalse();
	}
}
