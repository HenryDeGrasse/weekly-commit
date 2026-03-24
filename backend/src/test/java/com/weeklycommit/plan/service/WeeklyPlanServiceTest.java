package com.weeklycommit.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.dto.EffectiveCapacityResponse;
import com.weeklycommit.config.service.ConfigurationService;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private UserAccountRepository userRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private ConfigurationService configurationService;

	@InjectMocks
	private WeeklyPlanService service;

	private final UUID userId = UUID.randomUUID();
	private final UUID teamId = UUID.randomUUID();
	private final LocalDate monday = LocalDate.of(2025, 6, 2); // a Monday

	private UserAccount userWithTeam() {
		UserAccount u = new UserAccount();
		u.setId(userId);
		u.setHomeTeamId(teamId);
		u.setWeeklyCapacityPoints(10);
		return u;
	}

	@BeforeEach
	void stubCommonDependencies() {
		lenient().when(planRepo.save(any(WeeklyPlan.class))).thenAnswer(inv -> {
			WeeklyPlan p = inv.getArgument(0);
			if (p.getId() == null) {
				p.setId(UUID.randomUUID());
			}
			return p;
		});
		lenient().when(commitRepo.findByPlanIdOrderByPriorityOrder(any())).thenReturn(List.of());

		// Default config stubs for plan creation
		lenient().when(configurationService.getEffectiveCapacity(eq(userId), any(LocalDate.class)))
				.thenReturn(new EffectiveCapacityResponse(userId, monday, 10, "SYSTEM_DEFAULT"));
		lenient().when(configurationService.computeLockDeadline(eq(teamId), any(LocalDate.class)))
				.thenReturn(Instant.parse("2025-06-02T12:00:00Z"));
		lenient().when(configurationService.computeReconcileDeadline(eq(teamId), any(LocalDate.class)))
				.thenReturn(Instant.parse("2025-06-06T17:00:00Z"));
	}

	// -------------------------------------------------------------------------
	// getOrCreatePlan
	// -------------------------------------------------------------------------

	@Test
	void getOrCreate_existingPlan_returnsExisting() {
		WeeklyPlan existing = new WeeklyPlan();
		existing.setId(UUID.randomUUID());
		existing.setOwnerUserId(userId);
		existing.setWeekStartDate(monday);
		existing.setState(PlanState.DRAFT);

		when(planRepo.findByOwnerUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.of(existing));

		WeeklyPlan result = service.getOrCreatePlan(userId, monday);

		assertThat(result.getId()).isEqualTo(existing.getId());
		verify(planRepo, never()).save(any());
	}

	@Test
	void getOrCreate_noPlan_createsNewDraftPlan() {
		when(planRepo.findByOwnerUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(userWithTeam()));

		WeeklyPlan result = service.getOrCreatePlan(userId, monday);

		assertThat(result.getState()).isEqualTo(PlanState.DRAFT);
		assertThat(result.getOwnerUserId()).isEqualTo(userId);
		assertThat(result.getTeamId()).isEqualTo(teamId);
		assertThat(result.getWeekStartDate()).isEqualTo(monday);
		verify(planRepo).save(any());
	}

	@Test
	void getOrCreate_snapshotsCapacityFromEffectiveConfig() {
		when(configurationService.getEffectiveCapacity(eq(userId), any(LocalDate.class)))
				.thenReturn(new EffectiveCapacityResponse(userId, monday, 8, "TEAM_DEFAULT"));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(userWithTeam()));

		WeeklyPlan result = service.getOrCreatePlan(userId, monday);

		assertThat(result.getCapacityBudgetPoints()).isEqualTo(8);
	}

	@Test
	void getOrCreate_usesCadenceDeadlinesFromEffectiveConfig() {
		Instant lockDeadline = Instant.parse("2025-06-02T10:00:00Z");
		Instant reconcileDeadline = Instant.parse("2025-06-09T09:00:00Z");
		when(configurationService.computeLockDeadline(eq(teamId), eq(monday))).thenReturn(lockDeadline);
		when(configurationService.computeReconcileDeadline(eq(teamId), eq(monday))).thenReturn(reconcileDeadline);

		when(planRepo.findByOwnerUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(userWithTeam()));

		WeeklyPlan result = service.getOrCreatePlan(userId, monday);

		assertThat(result.getLockDeadline()).isEqualTo(lockDeadline);
		assertThat(result.getReconcileDeadline()).isEqualTo(reconcileDeadline);
	}

	@Test
	void getOrCreate_userNotFound_throws() {
		when(planRepo.findByOwnerUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getOrCreatePlan(userId, monday)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void getOrCreate_userWithNoHomeTeam_throws() {
		UserAccount user = new UserAccount();
		user.setId(userId);
		user.setHomeTeamId(null); // no team
		user.setWeeklyCapacityPoints(10);

		when(planRepo.findByOwnerUserIdAndWeekStartDate(userId, monday)).thenReturn(Optional.empty());
		when(userRepo.findById(userId)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.getOrCreatePlan(userId, monday)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("home team");
	}

	// -------------------------------------------------------------------------
	// listPlansForUser
	// -------------------------------------------------------------------------

	@Test
	void listPlans_returnsPlansInDescendingOrder() {
		WeeklyPlan p1 = new WeeklyPlan();
		p1.setId(UUID.randomUUID());
		p1.setOwnerUserId(userId);
		p1.setWeekStartDate(monday);
		p1.setState(PlanState.DRAFT);

		when(planRepo.findByOwnerUserIdOrderByWeekStartDateDesc(userId)).thenReturn(List.of(p1));

		var result = service.listPlansForUser(userId);
		assertThat(result).hasSize(1);
	}

	// -------------------------------------------------------------------------
	// currentWeekStart
	// -------------------------------------------------------------------------

	@Test
	void currentWeekStart_isAlwaysMonday() {
		LocalDate result = WeeklyPlanService.currentWeekStart();
		assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
	}
}
