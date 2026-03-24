package com.weeklycommit.carryforward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.carryforward.dto.CarryForwardLineageResponse;
import com.weeklycommit.carryforward.dto.CarryForwardResponse;
import com.weeklycommit.domain.entity.CarryForwardLink;
import com.weeklycommit.domain.entity.ScopeChangeEvent;
import com.weeklycommit.domain.entity.UserAccount;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.CarryForwardReason;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.enums.ScopeChangeCategory;
import com.weeklycommit.domain.repository.CarryForwardLinkRepository;
import com.weeklycommit.domain.repository.ScopeChangeEventRepository;
import com.weeklycommit.domain.repository.UserAccountRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.plan.exception.PlanValidationException;
import com.weeklycommit.rcdo.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CarryForwardServiceTest {

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private UserAccountRepository userRepo;

	@Mock
	private CarryForwardLinkRepository linkRepo;

	@Mock
	private ScopeChangeEventRepository scopeChangeEventRepo;

	@InjectMocks
	private CarryForwardService service;

	private final UUID sourceCommitId = UUID.randomUUID();
	private final UUID sourceOwner = UUID.randomUUID();
	private final UUID teamId = UUID.randomUUID();
	private final UUID targetPlanId = UUID.randomUUID();
	private final LocalDate targetWeek = LocalDate.of(2025, 6, 9);

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WeeklyCommit sourceCommit() {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(sourceCommitId);
		c.setPlanId(UUID.randomUUID());
		c.setOwnerUserId(sourceOwner);
		c.setTitle("Original commit");
		c.setDescription("Original description");
		c.setChessPiece(ChessPiece.ROOK);
		c.setRcdoNodeId(UUID.randomUUID());
		c.setEstimatePoints(3);
		c.setSuccessCriteria("Done when unit tests pass");
		c.setCarryForwardStreak(0);
		c.setPriorityOrder(1);
		return c;
	}

	private WeeklyPlan draftTargetPlan() {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(targetPlanId);
		p.setOwnerUserId(sourceOwner);
		p.setTeamId(teamId);
		p.setWeekStartDate(targetWeek);
		p.setState(PlanState.DRAFT);
		p.setLockDeadline(Instant.now().plusSeconds(3600));
		p.setReconcileDeadline(Instant.now().plusSeconds(7 * 24 * 3600));
		return p;
	}

	private WeeklyPlan lockedTargetPlan() {
		WeeklyPlan p = draftTargetPlan();
		p.setState(PlanState.LOCKED);
		return p;
	}

	private UserAccount user() {
		UserAccount u = new UserAccount();
		u.setId(sourceOwner);
		u.setHomeTeamId(teamId);
		u.setEmail("test@example.com");
		u.setDisplayName("Test User");
		u.setOrganizationId(UUID.randomUUID());
		u.setWeeklyCapacityPoints(10);
		return u;
	}

	private CarryForwardLink savedLink(UUID newCommitId) {
		CarryForwardLink l = new CarryForwardLink();
		l.setId(UUID.randomUUID());
		l.setSourceCommitId(sourceCommitId);
		l.setTargetCommitId(newCommitId);
		l.setReason(CarryForwardReason.STILL_IN_PROGRESS);
		return l;
	}

	@BeforeEach
	void stubCommon() {
		// Commit save returns its argument with an ID set
		lenient().when(commitRepo.save(any(WeeklyCommit.class))).thenAnswer(inv -> {
			WeeklyCommit c = inv.getArgument(0);
			if (c.getId() == null) {
				c.setId(UUID.randomUUID());
			}
			return c;
		});
		// Link save returns its argument with an ID set
		lenient().when(linkRepo.save(any(CarryForwardLink.class))).thenAnswer(inv -> {
			CarryForwardLink l = inv.getArgument(0);
			if (l.getId() == null) {
				l.setId(UUID.randomUUID());
			}
			return l;
		});
		lenient().when(scopeChangeEventRepo.save(any(ScopeChangeEvent.class))).thenAnswer(inv -> {
			ScopeChangeEvent e = inv.getArgument(0);
			if (e.getId() == null) {
				e.setId(UUID.randomUUID());
			}
			return e;
		});
	}

	// =========================================================================
	// carryForward — basic flow into DRAFT target plan
	// =========================================================================

	@Test
	void carryForward_intoDraftPlan_createsNewCommitWithProvenance() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek))
				.thenReturn(Optional.of(draftTargetPlan()));
		when(commitRepo.countByPlanId(targetPlanId)).thenReturn(2L);

		CarryForwardResponse resp = service.carryForward(source.getPlanId(), sourceCommitId, targetWeek,
				CarryForwardReason.STILL_IN_PROGRESS, null, UUID.randomUUID());

		assertThat(resp).isNotNull();
		assertThat(resp.postLockAdded()).isFalse();

		// New commit should have provenance fields set
		ArgumentCaptor<WeeklyCommit> commitCaptor = ArgumentCaptor.forClass(WeeklyCommit.class);
		verify(commitRepo).save(commitCaptor.capture());
		WeeklyCommit newCommit = commitCaptor.getValue();

		assertThat(newCommit.getCarryForwardSourceId()).isEqualTo(sourceCommitId);
		assertThat(newCommit.getCarryForwardStreak()).isEqualTo(1); // source streak 0 + 1
		assertThat(newCommit.getTitle()).isEqualTo("Original commit");
		assertThat(newCommit.getDescription()).isEqualTo("Original description");
		assertThat(newCommit.getChessPiece()).isEqualTo(ChessPiece.ROOK);
		assertThat(newCommit.getEstimatePoints()).isEqualTo(3);
		assertThat(newCommit.getPriorityOrder()).isEqualTo(3); // countByPlanId=2, so 3rd
	}

	@Test
	void carryForward_intoDraftPlan_createsProvenanceLink() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek))
				.thenReturn(Optional.of(draftTargetPlan()));
		when(commitRepo.countByPlanId(targetPlanId)).thenReturn(0L);

		service.carryForward(source.getPlanId(), sourceCommitId, targetWeek, CarryForwardReason.BLOCKED_BY_DEPENDENCY,
				"Dep unresolved", null);

		ArgumentCaptor<CarryForwardLink> linkCaptor = ArgumentCaptor.forClass(CarryForwardLink.class);
		verify(linkRepo).save(linkCaptor.capture());
		CarryForwardLink link = linkCaptor.getValue();

		assertThat(link.getSourceCommitId()).isEqualTo(sourceCommitId);
		assertThat(link.getReason()).isEqualTo(CarryForwardReason.BLOCKED_BY_DEPENDENCY);
		assertThat(link.getReasonNotes()).isEqualTo("Dep unresolved");
	}

	// =========================================================================
	// carryForward — streak incrementing
	// =========================================================================

	@Test
	void carryForward_streakIsIncrementedFromSource() {
		WeeklyCommit source = sourceCommit();
		source.setCarryForwardStreak(2); // already carried twice
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek))
				.thenReturn(Optional.of(draftTargetPlan()));
		when(commitRepo.countByPlanId(targetPlanId)).thenReturn(0L);

		service.carryForward(source.getPlanId(), sourceCommitId, targetWeek, CarryForwardReason.STILL_IN_PROGRESS, null,
				null);

		ArgumentCaptor<WeeklyCommit> captor = ArgumentCaptor.forClass(WeeklyCommit.class);
		verify(commitRepo).save(captor.capture());
		assertThat(captor.getValue().getCarryForwardStreak()).isEqualTo(3); // 2 + 1
	}

	// =========================================================================
	// carryForward — lazy plan creation
	// =========================================================================

	@Test
	void carryForward_targetPlanNotFound_lazilyCreastesDraftPlan() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek)).thenReturn(Optional.empty());
		when(userRepo.findById(sourceOwner)).thenReturn(Optional.of(user()));
		when(planRepo.save(any(WeeklyPlan.class))).thenAnswer(inv -> {
			WeeklyPlan p = inv.getArgument(0);
			p.setId(targetPlanId);
			return p;
		});
		when(commitRepo.countByPlanId(targetPlanId)).thenReturn(0L);

		CarryForwardResponse resp = service.carryForward(source.getPlanId(), sourceCommitId, targetWeek,
				CarryForwardReason.STILL_IN_PROGRESS, null, null);

		verify(planRepo).save(any(WeeklyPlan.class));
		assertThat(resp.postLockAdded()).isFalse();
	}

	@Test
	void carryForward_userHasNoHomeTeam_throwsValidation() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek)).thenReturn(Optional.empty());
		UserAccount userNoTeam = user();
		userNoTeam.setHomeTeamId(null);
		when(userRepo.findById(sourceOwner)).thenReturn(Optional.of(userNoTeam));

		assertThatThrownBy(() -> service.carryForward(source.getPlanId(), sourceCommitId, targetWeek,
				CarryForwardReason.STILL_IN_PROGRESS, null, null)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("no home team");
	}

	// =========================================================================
	// carryForward — into LOCKED target plan
	// =========================================================================

	@Test
	void carryForward_intoLockedPlan_addsCommitAndRecordsScopeChangeEvent() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek))
				.thenReturn(Optional.of(lockedTargetPlan()));
		when(commitRepo.countByPlanId(targetPlanId)).thenReturn(1L);

		CarryForwardResponse resp = service.carryForward(source.getPlanId(), sourceCommitId, targetWeek,
				CarryForwardReason.STILL_IN_PROGRESS, "Still active", UUID.randomUUID());

		assertThat(resp.postLockAdded()).isTrue();

		// Scope change event must be recorded
		ArgumentCaptor<ScopeChangeEvent> eventCaptor = ArgumentCaptor.forClass(ScopeChangeEvent.class);
		verify(scopeChangeEventRepo).save(eventCaptor.capture());
		ScopeChangeEvent event = eventCaptor.getValue();

		assertThat(event.getCategory()).isEqualTo(ScopeChangeCategory.COMMIT_ADDED);
		assertThat(event.getPlanId()).isEqualTo(targetPlanId);
		assertThat(event.getReason()).contains("CARRY_FORWARD");
		assertThat(event.getNewValue()).contains("carryForward");
	}

	@Test
	void carryForward_intoDraftPlan_doesNotRecordScopeChangeEvent() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));
		when(planRepo.findByOwnerUserIdAndWeekStartDate(sourceOwner, targetWeek))
				.thenReturn(Optional.of(draftTargetPlan()));
		when(commitRepo.countByPlanId(targetPlanId)).thenReturn(0L);

		service.carryForward(source.getPlanId(), sourceCommitId, targetWeek, CarryForwardReason.STILL_IN_PROGRESS, null,
				null);

		verify(scopeChangeEventRepo, never()).save(any(ScopeChangeEvent.class));
	}

	@Test
	void carryForward_sourceCommitNotFound_throwsNotFound() {
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.carryForward(UUID.randomUUID(), sourceCommitId, targetWeek,
				CarryForwardReason.STILL_IN_PROGRESS, null, null)).isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("Commit not found");
	}

	@Test
	void carryForward_commitFromDifferentPlan_throwsValidation() {
		WeeklyCommit source = sourceCommit();
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(source));

		assertThatThrownBy(() -> service.carryForward(UUID.randomUUID(), sourceCommitId, targetWeek,
				CarryForwardReason.STILL_IN_PROGRESS, null, null)).isInstanceOf(PlanValidationException.class)
				.hasMessageContaining("does not belong to plan");
	}

	// =========================================================================
	// getCarryForwardLineage — chain traversal
	// =========================================================================

	@Test
	void getCarryForwardLineage_singleCommitNoAncestors_returnsEmptyChain() {
		WeeklyCommit c = sourceCommit(); // no carryForwardSourceId
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.of(c));
		// findByTargetCommitId is NOT called because carryForwardSourceId is null
		when(linkRepo.findBySourceCommitId(sourceCommitId)).thenReturn(List.of());

		CarryForwardLineageResponse result = service.getCarryForwardLineage(sourceCommitId);

		assertThat(result.rootCommitId()).isEqualTo(sourceCommitId);
		assertThat(result.chain()).isEmpty();
	}

	@Test
	void getCarryForwardLineage_twoGenerations_returnsChainInOrder() {
		// grandparent → parent → child
		UUID grandparentId = UUID.randomUUID();
		UUID parentId = UUID.randomUUID();
		UUID childId = UUID.randomUUID();

		// grandparent: no source
		WeeklyCommit grandparent = new WeeklyCommit();
		grandparent.setId(grandparentId);
		grandparent.setCarryForwardStreak(0);

		// parent: sourced from grandparent
		WeeklyCommit parent = new WeeklyCommit();
		parent.setId(parentId);
		parent.setCarryForwardSourceId(grandparentId);
		parent.setCarryForwardStreak(1);

		// child: sourced from parent
		WeeklyCommit child = new WeeklyCommit();
		child.setId(childId);
		child.setCarryForwardSourceId(parentId);
		child.setCarryForwardStreak(2);

		CarryForwardLink gpToParent = new CarryForwardLink();
		gpToParent.setId(UUID.randomUUID());
		gpToParent.setSourceCommitId(grandparentId);
		gpToParent.setTargetCommitId(parentId);
		gpToParent.setReason(CarryForwardReason.STILL_IN_PROGRESS);

		CarryForwardLink parentToChild = new CarryForwardLink();
		parentToChild.setId(UUID.randomUUID());
		parentToChild.setSourceCommitId(parentId);
		parentToChild.setTargetCommitId(childId);
		parentToChild.setReason(CarryForwardReason.BLOCKED_BY_DEPENDENCY);

		// Query from parentId
		when(commitRepo.findById(parentId)).thenReturn(Optional.of(parent));
		when(commitRepo.findById(grandparentId)).thenReturn(Optional.of(grandparent));
		when(linkRepo.findByTargetCommitId(parentId)).thenReturn(Optional.of(gpToParent));
		when(linkRepo.findBySourceCommitId(parentId)).thenReturn(List.of(parentToChild));
		when(linkRepo.findBySourceCommitId(childId)).thenReturn(List.of());

		CarryForwardLineageResponse result = service.getCarryForwardLineage(parentId);

		assertThat(result.rootCommitId()).isEqualTo(grandparentId);
		assertThat(result.chain()).hasSize(2);
		// ancestors first (gpToParent), then descendants (parentToChild)
		assertThat(result.chain().get(0).sourceCommitId()).isEqualTo(grandparentId);
		assertThat(result.chain().get(0).targetCommitId()).isEqualTo(parentId);
		assertThat(result.chain().get(1).sourceCommitId()).isEqualTo(parentId);
		assertThat(result.chain().get(1).targetCommitId()).isEqualTo(childId);
	}

	@Test
	void getCarryForwardLineage_commitNotFound_throwsNotFound() {
		when(commitRepo.findById(sourceCommitId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getCarryForwardLineage(sourceCommitId))
				.isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Commit not found");
	}
}
