package com.weeklycommit.lock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weeklycommit.ai.service.RiskDetectionService;
import com.weeklycommit.report.service.ReadModelRefreshService;
import com.weeklycommit.domain.entity.LockSnapshotCommit;
import com.weeklycommit.domain.entity.LockSnapshotHeader;
import com.weeklycommit.domain.entity.RcdoNode;
import com.weeklycommit.domain.entity.WeeklyCommit;
import com.weeklycommit.domain.entity.WeeklyPlan;
import com.weeklycommit.domain.enums.ChessPiece;
import com.weeklycommit.domain.enums.PlanState;
import com.weeklycommit.domain.repository.LockSnapshotCommitRepository;
import com.weeklycommit.domain.repository.LockSnapshotHeaderRepository;
import com.weeklycommit.domain.repository.RcdoNodeRepository;
import com.weeklycommit.domain.repository.WeeklyCommitRepository;
import com.weeklycommit.domain.repository.WeeklyPlanRepository;
import com.weeklycommit.lock.dto.LockResponse;
import com.weeklycommit.lock.dto.ValidationError;
import com.weeklycommit.plan.exception.PlanValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LockServiceTest {

	@Mock
	private WeeklyPlanRepository planRepo;

	@Mock
	private WeeklyCommitRepository commitRepo;

	@Mock
	private LockSnapshotHeaderRepository headerRepo;

	@Mock
	private LockSnapshotCommitRepository commitSnapshotRepo;

	@Mock
	private RcdoNodeRepository rcdoNodeRepo;

	@Mock
	private RiskDetectionService riskDetectionService;

	@Mock
	private ReadModelRefreshService readModelRefreshService;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

	@InjectMocks
	private LockService lockService;

	private final UUID planId = UUID.randomUUID();
	private final UUID userId = UUID.randomUUID();
	private final UUID rcdoId = UUID.randomUUID();

	// -------------------------------------------------------------------------
	// Test helpers
	// -------------------------------------------------------------------------

	private WeeklyPlan draftPlan(Instant lockDeadline) {
		WeeklyPlan p = new WeeklyPlan();
		p.setId(planId);
		p.setOwnerUserId(userId);
		p.setTeamId(UUID.randomUUID());
		p.setWeekStartDate(LocalDate.of(2025, 6, 2));
		p.setState(PlanState.DRAFT);
		p.setCapacityBudgetPoints(10);
		p.setLockDeadline(lockDeadline);
		p.setReconcileDeadline(lockDeadline.plusSeconds(7 * 24 * 3600));
		return p;
	}

	private WeeklyCommit validCommit(int order) {
		WeeklyCommit c = new WeeklyCommit();
		c.setId(UUID.randomUUID());
		c.setPlanId(planId);
		c.setOwnerUserId(userId);
		c.setTitle("Commit " + order);
		c.setChessPiece(ChessPiece.ROOK);
		c.setPriorityOrder(order);
		c.setRcdoNodeId(rcdoId);
		c.setEstimatePoints(3);
		return c;
	}

	private WeeklyCommit kingCommit(int order) {
		WeeklyCommit c = validCommit(order);
		c.setChessPiece(ChessPiece.KING);
		c.setSuccessCriteria("Must ship feature X");
		return c;
	}

	private LockSnapshotHeader snapshotHeader() {
		LockSnapshotHeader h = new LockSnapshotHeader();
		h.setId(UUID.randomUUID());
		h.setPlanId(planId);
		h.setSnapshotPayload("{}");
		return h;
	}

	private RcdoNode rcdoNode(UUID id, UUID parentId, String title) {
		RcdoNode node = new RcdoNode();
		node.setId(id);
		node.setParentId(parentId);
		node.setTitle(title);
		node.setNodeType(parentId == null
				? com.weeklycommit.domain.enums.RcdoNodeType.RALLY_CRY
				: com.weeklycommit.domain.enums.RcdoNodeType.OUTCOME);
		node.setStatus(com.weeklycommit.domain.enums.RcdoNodeStatus.ACTIVE);
		return node;
	}

	@BeforeEach
	void stubSave() {
		lenient().when(planRepo.save(any(WeeklyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(headerRepo.save(any(LockSnapshotHeader.class))).thenAnswer(inv -> {
			LockSnapshotHeader h = inv.getArgument(0);
			if (h.getId() == null)
				h.setId(UUID.randomUUID());
			return h;
		});
		lenient().when(commitSnapshotRepo.save(any(LockSnapshotCommit.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(rcdoNodeRepo.findById(any())).thenReturn(Optional.empty());
	}

	// -------------------------------------------------------------------------
	// Manual lock — happy path
	// -------------------------------------------------------------------------

	@Test
	void lockPlan_validDraftWithCommits_returnsSuccess() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600)); // deadline in future
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isTrue();
		assertThat(result.errors()).isEmpty();
		assertThat(plan.getState()).isEqualTo(PlanState.LOCKED);
	}

	@Test
	void lockPlan_onTimeLock_setsCompliant() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600)); // future deadline
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		lockService.lockPlan(planId, userId);

		assertThat(plan.isCompliant()).isTrue();
	}

	@Test
	void lockPlan_success_triggersRiskDetection() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		lockService.lockPlan(planId, userId);

		verify(riskDetectionService).detectAndStoreRiskSignalsById(planId);
	}

	@Test
	void lockPlan_lateLock_setsNonCompliant() {
		WeeklyPlan plan = draftPlan(Instant.now().minusSeconds(3600)); // deadline passed
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		lockService.lockPlan(planId, userId);

		assertThat(plan.isCompliant()).isFalse();
	}

	@Test
	void lockPlan_capturesSnapshot_transactionally() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		lockService.lockPlan(planId, userId);

		verify(headerRepo, times(1)).save(any(LockSnapshotHeader.class));
		verify(commitSnapshotRepo, times(1)).save(any(LockSnapshotCommit.class));
		assertThat(plan.getLockSnapshotId()).isNotNull();
	}

	@Test
	void lockPlan_snapshotPayloadContainsFullCommitDetailsAndRcdoPath() throws Exception {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit commit = validCommit(1);
		commit.setDescription("Detailed description");
		commit.setSuccessCriteria("Success means shipped");
		UUID rallyCryId = UUID.randomUUID();
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(commit));
		when(rcdoNodeRepo.findById(commit.getRcdoNodeId()))
				.thenReturn(Optional.of(rcdoNode(commit.getRcdoNodeId(), rallyCryId, "Outcome A")));
		when(rcdoNodeRepo.findById(rallyCryId)).thenReturn(Optional.of(rcdoNode(rallyCryId, null, "Rally Cry A")));

		lockService.lockPlan(planId, userId);

		ArgumentCaptor<LockSnapshotHeader> captor = ArgumentCaptor.forClass(LockSnapshotHeader.class);
		verify(headerRepo).save(captor.capture());
		String payload = captor.getValue().getSnapshotPayload();
		com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(payload);
		assertThat(root.path("commits").size()).isEqualTo(1);
		assertThat(root.path("commits").get(0).path("description").asText()).isEqualTo("Detailed description");
		assertThat(root.path("commits").get(0).path("successCriteria").asText()).isEqualTo("Success means shipped");
		assertThat(root.path("commits").get(0).path("rcdoPath").size()).isEqualTo(2);
	}

	// -------------------------------------------------------------------------
	// Manual lock — already locked (idempotent)
	// -------------------------------------------------------------------------

	@Test
	void lockPlan_alreadyLocked_isIdempotentSuccess() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		plan.setState(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isTrue();
		verify(planRepo, never()).save(any()); // no state change written
	}

	@Test
	void lockPlan_wrongState_throwsPlanValidation() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		plan.setState(PlanState.RECONCILING);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> lockService.lockPlan(planId, userId)).isInstanceOf(PlanValidationException.class);
	}

	// -------------------------------------------------------------------------
	// Hard validation failures
	// -------------------------------------------------------------------------

	@Test
	void lockPlan_noCommits_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of());

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().equals("commits"));
		assertThat(plan.getState()).isEqualTo(PlanState.DRAFT); // not locked
	}

	@Test
	void lockPlan_commitMissingRcdo_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setRcdoNodeId(null); // missing RCDO
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().contains("rcdoNodeId"));
	}

	@Test
	void lockPlan_commitMissingEstimatePoints_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setEstimatePoints(null);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().contains("estimatePoints"));
	}

	@Test
	void lockPlan_commitInvalidEstimatePoints_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setEstimatePoints(4);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().contains("estimatePoints"));
	}

	@Test
	void lockPlan_kingWithoutSuccessCriteria_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setChessPiece(ChessPiece.KING);
		c.setSuccessCriteria(null); // missing!
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().contains("successCriteria"));
	}

	@Test
	void lockPlan_queenWithoutSuccessCriteria_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setChessPiece(ChessPiece.QUEEN);
		c.setSuccessCriteria(null);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().contains("successCriteria"));
	}

	@Test
	void lockPlan_twoKings_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit k1 = kingCommit(1);
		WeeklyCommit k2 = kingCommit(2);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(k1, k2));

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().equals("commits.king"));
	}

	@Test
	void lockPlan_threeQueens_returnsValidationError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		List<WeeklyCommit> commits = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			WeeklyCommit q = validCommit(i);
			q.setChessPiece(ChessPiece.QUEEN);
			q.setSuccessCriteria("Criteria " + i);
			commits.add(q);
		}
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(commits);

		LockResponse result = lockService.lockPlan(planId, userId);

		assertThat(result.success()).isFalse();
		assertThat(result.errors()).anyMatch(e -> e.field().equals("commits.queen"));
	}

	// -------------------------------------------------------------------------
	// Auto-lock
	// -------------------------------------------------------------------------

	@Test
	void autoLockPlan_expiredDraft_locksWithSystemFlag() {
		WeeklyPlan plan = draftPlan(Instant.now().minusSeconds(3600));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		lockService.autoLockPlan(planId);

		assertThat(plan.getState()).isEqualTo(PlanState.LOCKED);
		assertThat(plan.isCompliant()).isFalse();
		assertThat(plan.isSystemLockedWithErrors()).isFalse();
	}

	@Test
	void autoLockPlan_withValidationErrors_setsSystemLockedWithErrors() {
		WeeklyPlan plan = draftPlan(Instant.now().minusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setRcdoNodeId(null); // missing RCDO → validation error
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		lockService.autoLockPlan(planId);

		assertThat(plan.getState()).isEqualTo(PlanState.LOCKED);
		assertThat(plan.isSystemLockedWithErrors()).isTrue();
		verify(headerRepo, times(1)).save(any(LockSnapshotHeader.class));
	}

	@Test
	void autoLockPlan_triggersRiskDetection() {
		WeeklyPlan plan = draftPlan(Instant.now().minusSeconds(3600));
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		lockService.autoLockPlan(planId);

		verify(riskDetectionService).detectAndStoreRiskSignalsById(planId);
	}

	@Test
	void autoLockPlan_alreadyLocked_isIdempotent() {
		WeeklyPlan plan = draftPlan(Instant.now().minusSeconds(3600));
		plan.setState(PlanState.LOCKED);
		when(planRepo.findById(planId)).thenReturn(Optional.of(plan));

		lockService.autoLockPlan(planId);

		verify(planRepo, never()).save(any()); // no update
		verify(headerRepo, never()).save(any());
	}

	@Test
	void autoLockPlan_planNotFound_isIdempotent() {
		when(planRepo.findById(planId)).thenReturn(Optional.empty());

		// Should not throw
		lockService.autoLockPlan(planId);

		verify(planRepo, never()).save(any());
	}

	// -------------------------------------------------------------------------
	// validateForLock (direct unit tests)
	// -------------------------------------------------------------------------

	@Test
	void validateForLock_validPlan_returnsEmptyList() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(validCommit(1)));

		List<ValidationError> errors = lockService.validateForLock(plan);

		assertThat(errors).isEmpty();
	}

	@Test
	void validateForLock_blankTitle_returnsError() {
		WeeklyPlan plan = draftPlan(Instant.now().plusSeconds(3600));
		WeeklyCommit c = validCommit(1);
		c.setTitle("");
		when(commitRepo.findByPlanIdOrderByPriorityOrder(planId)).thenReturn(List.of(c));

		List<ValidationError> errors = lockService.validateForLock(plan);

		assertThat(errors).anyMatch(e -> e.field().contains("title"));
	}
}
